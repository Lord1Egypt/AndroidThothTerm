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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;

public class TarballExtractorTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsTraversalAndAbsoluteNames() {
        assertNull(TarballExtractor.sanitizeEntryName("../evil"));
        assertNull(TarballExtractor.sanitizeEntryName("a/../../b"));
        assertNull(TarballExtractor.sanitizeEntryName("/etc/passwd"));
        assertNull(TarballExtractor.sanitizeEntryName(".."));
        assertNull(TarballExtractor.sanitizeEntryName(""));
        assertNull(TarballExtractor.sanitizeEntryName("a/..\\..\\b"));
        assertNull(TarballExtractor.sanitizeEntryName("a\\..\\evil"));
        assertNull(TarballExtractor.sanitizeEntryName("\\etc\\passwd"));
        assertNull(TarballExtractor.sanitizeEntryName("\\..\\x"));
        assertNull(TarballExtractor.sanitizeEntryName("..\\x"));
        assertNull(TarballExtractor.sanitizeEntryName("a\0b"));
    }

    @Test
    public void normalizesSafeNames() {
        assertEquals("etc/os-release", TarballExtractor.sanitizeEntryName("etc/os-release"));
        assertEquals("usr/bin", TarballExtractor.sanitizeEntryName("./usr/bin"));
        assertEquals("a/b/c", TarballExtractor.sanitizeEntryName("a//b/./c"));
        assertEquals("...", TarballExtractor.sanitizeEntryName("..."));
        assertEquals("a..b", TarballExtractor.sanitizeEntryName("a..b"));
        assertEquals("usr/lib/systemd/system/system-systemd\\x2dcryptsetup.slice",
                TarballExtractor.sanitizeEntryName(
                        "usr/lib/systemd/system/system-systemd\\x2dcryptsetup.slice"));
    }

    @Test
    public void decodesStrictUtf8Only() {
        byte[] good = "F\u0151tan\u00fas\u00edtv\u00e1ny".getBytes(UTF8);
        assertEquals("F\u0151tan\u00fas\u00edtv\u00e1ny",
                TarballExtractor.decodeName(good, 0, good.length));
        // Overlong '/', a lone continuation byte, and 0xFF.
        assertNull(TarballExtractor.decodeName(new byte[]{(byte) 0xC0, (byte) 0xAF}, 0, 2));
        assertNull(TarballExtractor.decodeName(new byte[]{(byte) 0x80}, 0, 1));
        assertNull(TarballExtractor.decodeName(new byte[]{(byte) 0xFF}, 0, 1));
    }

    /** Every adversarial case, with the JVM's no-follow operations. */
    @Test
    public void adversarialCasesWithHardlinks() throws Exception {
        runAll(new JvmFileOps(), "jvm");
    }

    /** The same cases when every hardlink is refused, as Android SELinux does. */
    @Test
    public void adversarialCasesWithHardlinkFallback() throws Exception {
        runAll(new NoHardlinkOps(new JvmFileOps()), "nolink");
    }

    private void runAll(FileOps ops, String prefix) throws Exception {
        StringBuilder failures = new StringBuilder();
        for (Map.Entry<String, ExtractorSecurityCases.Case> c
                : ExtractorSecurityCases.all().entrySet()) {
            if (c.getKey().equals("utf8NamesPreserved") && !utf8FileNames()) continue;
            File dir = temporaryFolder.newFolder(prefix + "-" + c.getKey());
            try {
                c.getValue().run(ops, dir);
            } catch (Throwable t) {
                failures.append(c.getKey()).append(": ").append(t).append('\n');
            }
        }
        if (failures.length() > 0) fail(failures.toString());
    }

    /**
     * Android always maps file names as UTF-8; a host JVM does so only under a
     * UTF-8 locale (sun.jnu.encoding), so there the case is checked separately.
     */
    @Test
    public void utf8NamesWhereTheHostCanRepresentThem() throws Exception {
        org.junit.Assume.assumeTrue("host JVM file names are not UTF-8 (set LANG=C.UTF-8)",
                utf8FileNames());
        ExtractorSecurityCases.all().get("utf8NamesPreserved")
                .run(new JvmFileOps(), temporaryFolder.newFolder("utf8"));
    }

    private static boolean utf8FileNames() {
        String jnu = System.getProperty("sun.jnu.encoding", "UTF-8");
        return Charset.forName(jnu).equals(UTF8);
    }

    /**
     * The regression Sonnet reported, against the operations the extractor
     * used before: plain File.exists() and FileOutputStream follow a dangling
     * symlink. Proves the fixture detects the escape, so the passing cases
     * above are meaningful.
     */
    @Test
    public void fixtureDetectsTheOldFollowingBehaviour() throws Exception {
        File dir = temporaryFolder.newFolder("old-behaviour");
        FileOps following = new FollowingOps(new JvmFileOps());
        try {
            ExtractorSecurityCases.all().get("danglingSymlinkThenFile").run(following, dir);
            fail("an extractor that follows symlinks must be caught");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("OUTSIDE"));
        }
    }

    @Test
    public void hardlinkFallsBackToCopyWhenLinkIsRefused() throws Exception {
        File root = temporaryFolder.newFolder("fallback");
        TarballExtractor extractor = new TarballExtractor(new NoHardlinkOps(new JvmFileOps()),
                root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .file("usr/bin/target", "payload")
                .hardlink("usr/bin/link", "usr/bin/target")
                .build()));

        assertEquals(1, extractor.hardlinkFallbacks());
        assertTrue(Files.isRegularFile(new File(root, "usr/bin/link").toPath()));
        assertEquals("payload", readText(new File(root, "usr/bin/link")));
    }

    /**
     * On Android every hard link is a copy, which reads its target. A target
     * whose final mode its owner cannot read (Arch's 04110 launch helper) must
     * still be copyable: such modes are applied after every entry.
     */
    @Test
    public void hardlinkCopyOfAnOwnerUnreadableTargetWorks() throws Exception {
        File root = temporaryFolder.newFolder("unreadable-copy");
        TarballExtractor extractor = new TarballExtractor(new NoHardlinkOps(new JvmFileOps()),
                root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .file("usr/lib/helper", "helper".getBytes(UTF8), 04110)
                .hardlink("usr/lib/helper.hl", "usr/lib/helper")
                .build()));
        assertEquals(1, extractor.hardlinkFallbacks());
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(new File(root, "usr/lib/helper")));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(new File(root, "usr/lib/helper.hl")));
        assertEquals("helper", readOwnerUnreadable(new File(root, "usr/lib/helper.hl")));
    }

    /** A real link keeps the target's final mode even when the target name is replaced later. */
    @Test
    public void unreadableModeReachesAHardlinkWhoseTargetNameIsReplaced() throws Exception {
        File root = temporaryFolder.newFolder("unreadable-link");
        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .file("a", "old".getBytes(UTF8), 0110)
                .hardlink("b", "a")
                .file("a", "new".getBytes(UTF8), 0644)
                .build()));
        assertEquals(0, extractor.hardlinkFallbacks());
        assertEquals(0644, ExtractorSecurityCases.Fixture.mode(new File(root, "a")));
        assertEquals("new", readText(new File(root, "a")));
        assertEquals(0110, ExtractorSecurityCases.Fixture.mode(new File(root, "b")));
        assertEquals("old", readOwnerUnreadable(new File(root, "b")));
    }

    /** A duplicate entry replaces the earlier file; its deferred mode goes with it. */
    @Test
    public void duplicateEntryDropsTheEarlierDeferredMode() throws Exception {
        File root = temporaryFolder.newFolder("duplicate");
        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .file("x", "first".getBytes(UTF8), 0000)
                .file("x", "second".getBytes(UTF8), 0640)
                .file("y", "first".getBytes(UTF8), 0644)
                .file("y", "second".getBytes(UTF8), 0200)
                .build()));
        assertEquals(0640, ExtractorSecurityCases.Fixture.mode(new File(root, "x")));
        assertEquals("second", readText(new File(root, "x")));
        assertEquals(0200, ExtractorSecurityCases.Fixture.mode(new File(root, "y")));
        assertEquals("second", readOwnerUnreadable(new File(root, "y")));
    }

    /** Test-side only: this tree is the test's own, not a gate's rootfs. */
    private static String readOwnerUnreadable(File file) throws IOException {
        java.util.Set<java.nio.file.attribute.PosixFilePermission> mode =
                Files.getPosixFilePermissions(file.toPath());
        Files.setPosixFilePermissions(file.toPath(), ExtractorSecurityCases.JvmPermissions.of(0400));
        try {
            return readText(file);
        } finally {
            Files.setPosixFilePermissions(file.toPath(), mode);
        }
    }

    @Test
    public void extractsSymlinkAndHardlink() throws Exception {
        File root = temporaryFolder.newFolder("links");
        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .file("usr/bin/target", "data")
                .hardlink("usr/bin/link", "usr/bin/target")
                .symlink("etc/os-release", "../usr/bin/target")
                .build()));

        assertTrue(new File(root, "usr/bin/link").isFile());
        assertTrue(Files.isSymbolicLink(new File(root, "etc/os-release").toPath()));
        assertEquals("../usr/bin/target",
                Files.readSymbolicLink(new File(root, "etc/os-release").toPath()).toString());
        assertEquals(0, extractor.hardlinkFallbacks());
        assertEquals(0, extractor.rejectedEntries());
    }

    @Test
    public void rejectionReasonsAreRecorded() throws Exception {
        File root = temporaryFolder.newFolder("reasons");
        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(new TarBuilder()
                .symlink("lnk", "/")
                .file("lnk/etc/passwd", "x")
                .file("../evil", "x")
                .build()));
        assertEquals(2, extractor.rejectedEntries());
        assertTrue(extractor.rejectionReasons().get(0),
                extractor.rejectionReasons().get(0).contains("symlink"));
        assertTrue(extractor.rejectionReasons().get(1),
                extractor.rejectionReasons().get(1).contains("unsafe name"));
    }

    @Test
    public void failedExtractionLeavesCleanableStagingForRetry() throws Exception {
        File root = temporaryFolder.newFolder("retry");
        JvmFileOps ops = new JvmFileOps();

        byte[] good = new TarBuilder().file("a/b", "one").build();
        byte[] broken = new byte[good.length - 1024 + 512 + 1024];
        System.arraycopy(good, 0, broken, 0, good.length - 1024);
        byte[] bad = new TarBuilder().file("broken", "x").build();
        System.arraycopy(bad, 0, broken, good.length - 1024, 512);
        broken[good.length - 1024] = 'X'; // corrupt the header checksum

        try {
            new TarballExtractor(ops, root, null).extract(new ByteArrayInputStream(broken));
            fail("a corrupt header must fail");
        } catch (IOException expected) {
            // expected
        }
        assertTrue(new File(root, "a/b").exists());

        // Cleanup must recover and remove the partial tree.
        SafeFileTree.deleteTree(ops, root, root);
        assertFalse(root.exists());

        // Retry from clean staging succeeds.
        new TarballExtractor(ops, root, null).extract(new ByteArrayInputStream(
                new TarBuilder().file("ok.txt", "ok").build()));
        assertTrue(new File(root, "ok.txt").isFile());
    }

    @Test(expected = IOException.class)
    public void rejectsCorruptHeader() throws Exception {
        byte[] tar = new TarBuilder().file("broken", "x").build();
        tar[0] = 'X';
        new TarballExtractor(new JvmFileOps(), temporaryFolder.newFolder("corrupt"), null)
                .extract(new ByteArrayInputStream(tar));
    }

    @Test(expected = IOException.class)
    public void refusesARootThatIsNotADirectory() throws Exception {
        File base = temporaryFolder.newFolder("not-a-dir");
        File root = new File(base, "root");
        Files.createSymbolicLink(root.toPath(), base.toPath());
        new TarballExtractor(new JvmFileOps(), root, null)
                .extract(new ByteArrayInputStream(new TarBuilder().file("x", "x").build()));
    }

    private static String readText(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), UTF8);
    }

    /** Simulates Android's SELinux refusal of hardlinks for untrusted apps. */
    static final class NoHardlinkOps extends DelegatingOps {
        NoHardlinkOps(FileOps delegate) {
            super(delegate);
        }

        @Override
        public void hardlink(File existing, File link) throws IOException {
            throw new IOException("link failed: EACCES (Permission denied)");
        }
    }

    /**
     * The pre-fix semantics: an existing path is judged with File.exists()
     * (follows links) and created with FileOutputStream (follows links).
     */
    static final class FollowingOps extends DelegatingOps {
        FollowingOps(FileOps delegate) {
            super(delegate);
        }

        @Override
        public Type type(File file) throws IOException {
            if (!file.exists()) return Type.NONE;
            return file.isDirectory() ? Type.DIRECTORY : Type.REGULAR;
        }

        @Override
        public OutputStream createNew(File file, int mode) throws IOException {
            return new java.io.FileOutputStream(file);
        }
    }

    static class DelegatingOps implements FileOps {
        private final FileOps d;

        DelegatingOps(FileOps delegate) {
            this.d = delegate;
        }

        @Override public Type type(File f) throws IOException { return d.type(f); }
        @Override public int permissions(File f) throws IOException { return d.permissions(f); }
        @Override public void mkdir(File f, int m) throws IOException { d.mkdir(f, m); }
        @Override public OutputStream createNew(File f, int m) throws IOException { return d.createNew(f, m); }
        @Override public InputStream openNoFollow(File f) throws IOException { return d.openNoFollow(f); }
        @Override public void chmodNoFollow(File f, int m) throws IOException { d.chmodNoFollow(f, m); }
        @Override public void unlink(File f) throws IOException { d.unlink(f); }
        @Override public void rmdir(File f) throws IOException { d.rmdir(f); }
        @Override public void symlink(String t, File l) throws IOException { d.symlink(t, l); }
        @Override public String readlink(File l) throws IOException { return d.readlink(l); }
        @Override public void hardlink(File e, File l) throws IOException { d.hardlink(e, l); }
        @Override public void rename(File a, File b) throws IOException { d.rename(a, b); }
        @Override public void setLastModified(File f, long t) { d.setLastModified(f, t); }
        @Override public String canonicalPath(File f) throws IOException { return d.canonicalPath(f); }
    }
}
