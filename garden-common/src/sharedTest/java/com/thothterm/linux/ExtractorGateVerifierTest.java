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
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * The extractor gate verifies a file its owner cannot read (Arch's 0110
 * dbus-daemon-launch-helper) from the bytes recorded while it was extracted:
 * the file is never reopened, its mode never changes, and its content is
 * still compared with the independent manifest.
 */
public class ExtractorGateVerifierTest {
    private static final byte[] HELPER = "launch helper, mode 0110".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TOOL = "#!/bin/sh\necho tool\n".getBytes(StandardCharsets.UTF_8);
    private static final String CERT = "etc/ssl/certs/NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt";
    private static final byte[] CERT_BYTES = "cert".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File archive;
    private String archiveSha;

    private void writeArchive() throws Exception {
        byte[] tar = new TarBuilder()
                .dir("usr/", 0755)
                .dir("usr/lib/", 0755)
                .file("usr/lib/dbus-daemon-launch-helper", HELPER, 04110)
                .dir("usr/bin/", 0755)
                .file("usr/bin/tool", TOOL, 0755)
                .hardlink("usr/bin/tool-link", "usr/bin/tool")
                .symlink("usr/bin/alias", "tool")
                .file(CERT, CERT_BYTES, 0644)
                .build();
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        GZIPOutputStream z = new GZIPOutputStream(gz);
        z.write(tar);
        z.close();
        archive = tmp.newFile("rootfs.tgz");
        Files.write(archive.toPath(), gz.toByteArray());
        archiveSha = hex(MessageDigest.getInstance("SHA-256").digest(gz.toByteArray()));
    }

    /** What manifest.py prints for that archive, written out by hand. */
    private File manifest(String helperSha) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("etc\td\t755\t0\t-");
        lines.add("etc/ssl\td\t755\t0\t-");
        lines.add("etc/ssl/certs\td\t755\t0\t-");
        lines.add(CERT + "\tf\t644\t" + CERT_BYTES.length + "\t" + sha(CERT_BYTES));
        lines.add("usr\td\t755\t0\t-");
        lines.add("usr/bin\td\t755\t0\t-");
        lines.add("usr/bin/alias\tl\t0\t0\ttool");
        lines.add("usr/bin/tool\tf\t755\t" + TOOL.length + "\t" + sha(TOOL));
        lines.add("usr/bin/tool-link\tf\t755\t" + TOOL.length + "\t" + sha(TOOL));
        lines.add("usr/lib\td\t755\t0\t-");
        lines.add("usr/lib/dbus-daemon-launch-helper\tf\t110\t" + HELPER.length + "\t" + helperSha);
        File m = tmp.newFile();
        Files.write(m.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        return m;
    }

    @Test
    public void anOwnerUnreadableFileIsVerifiedWithoutReopeningOrChmod() throws Exception {
        writeArchive();
        List<String> report = new ArrayList<>();
        File staging = new File(tmp.getRoot(), "rootfs");
        int failures = ExtractorGate.realArchive(new JvmFileOps(), archive, archiveSha,
                manifest(sha(HELPER)), staging, report::add);
        assertEquals(String.join("\n", report), 0, failures);
        File helper = new File(staging, "usr/lib/dbus-daemon-launch-helper");
        assertEquals("the final mode is untouched", 0110, ExtractorSecurityCases.Fixture.mode(helper));
        String summary = report.get(report.size() - 1);
        assertTrue(summary, summary.contains("content of 4 regular files"));
        assertTrue(summary, summary.contains("3 re-read from disk and identical"));
        assertTrue(summary, summary.contains("1 owner-unreadable verified without reopening or chmod"
                + " [usr/lib/dbus-daemon-launch-helper=110]"));
        assertTrue(summary, summary.contains("1 hard links share their target's inode"));
    }

    @Test
    public void wrongContentInAnOwnerUnreadableFileIsStillCaught() throws Exception {
        writeArchive();
        List<String> report = new ArrayList<>();
        File staging = new File(tmp.getRoot(), "rootfs");
        int failures = ExtractorGate.realArchive(new JvmFileOps(), archive, archiveSha,
                manifest(sha("something else".getBytes(StandardCharsets.UTF_8))), staging, report::add);
        assertEquals(1, failures);
        assertTrue(String.join("\n", report), String.join("\n", report)
                .contains("DIFF missing/different: usr/lib/dbus-daemon-launch-helper"));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(
                new File(staging, "usr/lib/dbus-daemon-launch-helper")));
    }

    @Test
    public void contentIsTheExtractionRecordAndMustMatchTheDisk() throws Exception {
        File root = tmp.newFolder("tree");
        JvmFileOps jvm = new JvmFileOps();
        RecordingFileOps recording = new RecordingFileOps(jvm);
        File a = new File(root, "a");
        try (OutputStream out = recording.createNew(a, 0644)) {
            out.write(TOOL);
        }
        File secret = new File(root, "secret");
        try (OutputStream out = recording.createNew(secret, 0110)) {
            out.write(HELPER);
        }
        ExtractorGate.ContentCheck check = new ExtractorGate.ContentCheck(recording, jvm);
        assertEquals(sha(TOOL), check.sha256(a, "a", TOOL.length));
        assertEquals(sha(HELPER), check.sha256(secret, "secret", HELPER.length));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(secret));

        // Changed on disk after extraction (same length): caught by the re-read.
        Files.write(a.toPath(), "#!/bin/sh\necho evil\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(check.sha256(a, "a", TOOL.length).startsWith("ON-DISK-"));
        // A size that is not what was written.
        assertTrue(check.sha256(secret, "secret", HELPER.length + 1).startsWith("SIZE-ON-DISK-"));
        // A file the extractor never wrote.
        File stray = new File(root, "stray");
        Files.write(stray.toPath(), TOOL);
        assertEquals("NOT-WRITTEN-BY-THE-EXTRACTOR", check.sha256(stray, "stray", TOOL.length));
    }

    @Test
    public void recordsFollowInodesAcrossHardlinksUnlinkAndRename() throws Exception {
        File root = tmp.newFolder("tree");
        RecordingFileOps recording = new RecordingFileOps(new JvmFileOps());
        File a = new File(root, "a");
        try (OutputStream out = recording.createNew(a, 0644)) {
            out.write(TOOL);
        }
        File b = new File(root, "b");
        recording.hardlink(a, b);
        assertEquals(sha(TOOL), recording.contentOf(b).sha256());
        assertEquals(1, recording.links().size());
        // The archive replaces "a": "b" keeps the old inode and its record.
        recording.unlink(a);
        try (OutputStream out = recording.createNew(a, 0644)) {
            out.write(HELPER);
        }
        assertEquals(sha(HELPER), recording.contentOf(a).sha256());
        assertEquals(sha(TOOL), recording.contentOf(b).sha256());
        assertEquals("a replaced name is no longer one inode with its link", 0, recording.links().size());
        File c = new File(root, "c");
        recording.rename(b, c);
        assertEquals(null, recording.contentOf(b));
        assertEquals(sha(TOOL), recording.contentOf(c).sha256());
    }

    private static String sha(byte[] data) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String hex(byte[] bytes) {
        return RootfsArchive.toHex(bytes);
    }
}
