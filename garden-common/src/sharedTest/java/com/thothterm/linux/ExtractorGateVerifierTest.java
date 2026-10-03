/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * The extractor gate proves the final objects, not what it recorded: a file
 * its owner cannot read (Arch's 0110 dbus-daemon-launch-helper) is read
 * through a descriptor pinned on its own inode -- never reopened, its mode
 * never changed -- and any change after the record was made is caught. Hard
 * link groups survive renames and unlinks of their members (Codex QA round 3,
 * findings 2 and 3).
 */
public class ExtractorGateVerifierTest {
    private static final byte[] HELPER = "launch helper, mode 0110".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER = "launch helper, MODE 0110".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TOOL = "#!/bin/sh\necho tool\n".getBytes(StandardCharsets.UTF_8);
    private static final String CERT = "etc/ssl/certs/NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt";
    private static final byte[] CERT_BYTES = "cert".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ObjectInspector inspector = new JvmObjectInspector();
    private File archive;
    private String archiveSha;

    private void writeArchive(boolean helperLink) throws Exception {
        TarBuilder tar = new TarBuilder()
                .dir("usr/", 0755)
                .dir("usr/lib/", 0755)
                .file("usr/lib/dbus-daemon-launch-helper", HELPER, 04110);
        if (helperLink) tar.hardlink("usr/lib/helper-link", "usr/lib/dbus-daemon-launch-helper");
        tar.dir("usr/bin/", 0755)
                .file("usr/bin/tool", TOOL, 0755)
                .hardlink("usr/bin/tool-link", "usr/bin/tool")
                .symlink("usr/bin/alias", "tool")
                .file("usr/bin/dup", OTHER, 0600)
                .file("usr/bin/dup", TOOL, 0644)
                .file(CERT, CERT_BYTES, 0644);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        GZIPOutputStream z = new GZIPOutputStream(gz);
        z.write(tar.build());
        z.close();
        archive = tmp.newFile("rootfs.tgz");
        Files.write(archive.toPath(), gz.toByteArray());
        archiveSha = hex(MessageDigest.getInstance("SHA-256").digest(gz.toByteArray()));
    }

    /** What manifest.py prints for that archive, written out by hand. */
    private File manifest(String helperSha, boolean helperLink) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("etc\td\t755\t0\t-");
        lines.add("etc/ssl\td\t755\t0\t-");
        lines.add("etc/ssl/certs\td\t755\t0\t-");
        lines.add(CERT + "\tf\t644\t" + CERT_BYTES.length + "\t" + sha(CERT_BYTES));
        lines.add("usr\td\t755\t0\t-");
        lines.add("usr/bin\td\t755\t0\t-");
        lines.add("usr/bin/alias\tl\t0\t0\ttool");
        lines.add("usr/bin/dup\tf\t644\t" + TOOL.length + "\t" + sha(TOOL));
        lines.add("usr/bin/tool\tf\t755\t" + TOOL.length + "\t" + sha(TOOL));
        lines.add("usr/bin/tool-link\tf\t755\t" + TOOL.length + "\t" + sha(TOOL));
        lines.add("usr/lib\td\t755\t0\t-");
        lines.add("usr/lib/dbus-daemon-launch-helper\tf\t110\t" + HELPER.length + "\t" + helperSha);
        if (helperLink) {
            lines.add("usr/lib/helper-link\tf\t110\t" + HELPER.length + "\t" + helperSha);
        }
        File m = tmp.newFile();
        Files.write(m.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        return m;
    }

    @Test
    public void anOwnerUnreadableFileIsReadThroughItsPinWithoutReopeningOrChmod() throws Exception {
        writeArchive(false);
        List<String> report = new ArrayList<>();
        File staging = new File(tmp.getRoot(), "rootfs");
        int failures = ExtractorGate.realArchive(new JvmFileOps(), inspector, archive, archiveSha,
                manifest(sha(HELPER), false), staging, report::add);
        assertEquals(String.join("\n", report), 0, failures);
        File helper = new File(staging, "usr/lib/dbus-daemon-launch-helper");
        assertEquals("the final mode is untouched", 0110, ExtractorSecurityCases.Fixture.mode(helper));
        String summary = report.get(report.size() - 1);
        assertTrue(summary, summary.contains("content of 5 regular files"));
        assertTrue(summary, summary.contains("4 re-read from disk"));
        assertTrue(summary, summary.contains("1 owner-unreadable read through the descriptor pinned"
                + " on that same inode (no reopen, no chmod) [usr/lib/dbus-daemon-launch-helper=110]"));
        assertTrue(summary, summary.contains("1 hard link groups (2 names) share one inode each"));
    }

    /**
     * Android: every hard link is a copy. A link to the 04110 helper is copied
     * before the helper takes its final mode, and both copies are verified.
     */
    @Test
    public void copiesOfAnOwnerUnreadableFileAreEachVerified() throws Exception {
        writeArchive(true);
        List<String> report = new ArrayList<>();
        File staging = new File(tmp.getRoot(), "rootfs");
        int failures = ExtractorGate.realArchive(
                new TarballExtractorTest.NoHardlinkOps(new JvmFileOps()), inspector, archive,
                archiveSha, manifest(sha(HELPER), true), staging, report::add);
        assertEquals(String.join("\n", report), 0, failures);
        String summary = report.get(report.size() - 1);
        assertTrue(summary, summary.contains("2 owner-unreadable read through the descriptor"));
        assertTrue(summary, summary.contains("0 hard link groups"));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(new File(staging, "usr/lib/helper-link")));
    }

    @Test
    public void wrongContentInAnOwnerUnreadableFileIsStillCaught() throws Exception {
        writeArchive(false);
        List<String> report = new ArrayList<>();
        File staging = new File(tmp.getRoot(), "rootfs");
        int failures = ExtractorGate.realArchive(new JvmFileOps(), inspector, archive, archiveSha,
                manifest(sha("something else".getBytes(StandardCharsets.UTF_8)), false), staging,
                report::add);
        assertEquals(1, failures);
        assertTrue(String.join("\n", report), String.join("\n", report)
                .contains("DIFF missing/different: usr/lib/dbus-daemon-launch-helper"));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(
                new File(staging, "usr/lib/dbus-daemon-launch-helper")));
    }

    @Test
    public void readableContentIsTheFinalFileAndMustMatchWhatWasWritten() throws Exception {
        File root = tmp.newFolder("tree");
        RecordingFileOps recording = new RecordingFileOps(new JvmFileOps(), inspector);
        ExtractorGate.ContentCheck check = new ExtractorGate.ContentCheck(recording, new JvmFileOps(),
                inspector);
        File a = write(recording, root, "a", TOOL, 0644);
        assertEquals(sha(TOOL), check.sha256(a, "a", TOOL.length));
        // Changed on disk after extraction (same length): caught by the re-read.
        Files.write(a.toPath(), "#!/bin/sh\necho evil\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(check.sha256(a, "a", TOOL.length).startsWith("FINAL-OBJECT-"));
        // A size that is not what was written.
        assertTrue(check.sha256(a, "a", TOOL.length + 1).startsWith("SIZE-ON-DISK-"));
        // A file the extractor never wrote.
        File stray = new File(root, "stray");
        Files.write(stray.toPath(), TOOL);
        assertEquals("NOT-WRITTEN-BY-THE-EXTRACTOR", check.sha256(stray, "stray", TOOL.length));
        recording.close();
    }

    /** Codex round 3, finding 2: owner-unreadable content changed after it was recorded. */
    @Test
    public void ownerUnreadableContentChangedAfterTheRecordIsCaught() throws Exception {
        File root = tmp.newFolder("tree");
        RecordingFileOps recording = new RecordingFileOps(new JvmFileOps(), inspector);
        ExtractorGate.ContentCheck check = new ExtractorGate.ContentCheck(recording, new JvmFileOps(),
                inspector);
        // 0200: its owner may write but not read, the case Codex described.
        File secret = write(recording, root, "secret", HELPER, 0200);
        assertEquals(0200, ExtractorSecurityCases.Fixture.mode(secret));
        assertEquals(sha(HELPER), check.sha256(secret, "secret", HELPER.length));

        // Same size, same mode, different bytes: the record alone would match.
        Files.write(secret.toPath(), OTHER);
        assertEquals(HELPER.length, OTHER.length);
        assertEquals(0200, ExtractorSecurityCases.Fixture.mode(secret));
        assertEquals("FINAL-OBJECT-" + sha(OTHER) + "-BUT-WROTE-" + sha(HELPER),
                check.sha256(secret, "secret", HELPER.length));

        // Truncated: the pinned inode is shorter than what was written.
        Files.write(secret.toPath(), new byte[0]);
        assertTrue(check.sha256(secret, "secret", HELPER.length).startsWith("PINNED-INODE-SIZE-"));

        // Replaced by another 0200 file with the original bytes: not the pinned inode.
        File twin = new File(root, "twin");
        Files.write(twin.toPath(), HELPER);
        Files.setPosixFilePermissions(twin.toPath(), ExtractorSecurityCases.JvmPermissions.of(0200));
        Files.move(twin.toPath(), secret.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        assertEquals("FINAL-PATH-IS-NOT-THE-PINNED-INODE",
                check.sha256(secret, "secret", HELPER.length));
        recording.close();
    }

    @Test
    public void ownerUnreadableWithoutAPinFailsAndChmodThroughTheRecorderPins() throws Exception {
        File root = tmp.newFolder("tree");
        RecordingFileOps recording = new RecordingFileOps(new JvmFileOps(), inspector);
        ExtractorGate.ContentCheck check = new ExtractorGate.ContentCheck(recording, new JvmFileOps(),
                inspector);
        // Made unreadable behind the recorder's back: nothing can prove its bytes.
        File hidden = write(recording, root, "hidden", HELPER, 0644);
        Files.setPosixFilePermissions(hidden.toPath(), ExtractorSecurityCases.JvmPermissions.of(0200));
        assertEquals("OWNER-UNREADABLE-AND-NEVER-PINNED", check.sha256(hidden, "hidden", HELPER.length));
        // Made unreadable through the recorder (the extractor's deferred mode): pinned first.
        File deferred = write(recording, root, "deferred", HELPER, 0600);
        recording.chmodNoFollow(deferred, 04110);
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(deferred));
        assertEquals(sha(HELPER), check.sha256(deferred, "deferred", HELPER.length));
        recording.close();
    }

    /** Codex round 3, finding 3: a link group survives renames and unlinks of its members. */
    @Test
    public void hardlinkGroupsSurviveRenameAndUnlink() throws Exception {
        File root = tmp.newFolder("tree");
        RecordingFileOps recording = new RecordingFileOps(new JvmFileOps(), inspector);
        File a = write(recording, root, "a", TOOL, 0644);
        File b = new File(root, "b");
        recording.hardlink(a, b);
        File c = new File(root, "c");
        recording.rename(a, c);
        assertEquals(names(root, "b", "c"), groups(recording));
        assertEquals(sha(TOOL), recording.nodeOf(c).sha256());
        assertEquals(null, recording.nodeOf(a));
        assertNull(ExtractorGate.hardlinkGroupProblem(recording.hardlinkGroups(), inspector));

        // A third name, then the first one goes: the other two are still one group.
        File d = new File(root, "d");
        recording.hardlink(b, d);
        recording.unlink(b);
        assertEquals(names(root, "c", "d"), groups(recording));

        // A member inside a renamed directory keeps its group.
        File dir = new File(root, "dir");
        recording.mkdir(dir, 0755);
        File inner = new File(dir, "inner");
        recording.hardlink(c, inner);
        File moved = new File(root, "moved");
        recording.rename(dir, moved);
        assertEquals(names(root, "c", "d", "moved/inner"), groups(recording));
        assertNull(ExtractorGate.hardlinkGroupProblem(recording.hardlinkGroups(), inspector));

        // rename(2) over a member replaces it: that name leaves the group.
        File x = write(recording, root, "x", HELPER, 0644);
        recording.rename(x, d);
        assertEquals(names(root, "c", "moved/inner"), groups(recording));
        assertEquals(sha(HELPER), recording.nodeOf(d).sha256());

        // A member replaced behind the recorder's back by a copy is caught.
        Files.delete(c.toPath());
        Files.write(c.toPath(), TOOL);
        String problem = ExtractorGate.hardlinkGroupProblem(recording.hardlinkGroups(), inspector);
        assertTrue(String.valueOf(problem), problem != null && problem.contains("not one inode"));
        recording.close();
    }

    private static File write(RecordingFileOps recording, File root, String name, byte[] data,
                              int mode) throws Exception {
        File f = new File(root, name);
        try (OutputStream out = recording.createNew(f, mode)) {
            out.write(data);
        }
        return f;
    }

    private static String groups(RecordingFileOps recording) {
        return String.valueOf(recording.hardlinkGroups());
    }

    private static String names(File root, String... rel) {
        List<File> files = new ArrayList<>();
        for (String r : rel) files.add(new File(root, r));
        List<List<File>> one = new ArrayList<>();
        one.add(files);
        return String.valueOf(one);
    }

    private static String sha(byte[] data) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String hex(byte[] bytes) {
        return RootfsArchive.toHex(bytes);
    }
}
