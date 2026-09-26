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

package com.thothterm.garden;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class TarballExtractorTest {
    private static final Charset ASCII = Charset.forName("US-ASCII");

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
        assertNull(TarballExtractor.sanitizeEntryName("a\0b"));
    }

    @Test
    public void normalizesSafeNames() {
        assertEquals("etc/os-release", TarballExtractor.sanitizeEntryName("etc/os-release"));
        assertEquals("usr/bin", TarballExtractor.sanitizeEntryName("./usr/bin"));
        assertEquals("a/b/c", TarballExtractor.sanitizeEntryName("a//b/./c"));
    }

    @Test
    public void extractsFilesAndRejectsTraversalEntry() throws Exception {
        File root = temporaryFolder.newFolder("rootfs");
        File outside = new File(root.getParentFile(), "evil");

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(entry("usr/bin/hello", '0', null, "hi\n".getBytes(ASCII)));
        tar.write(entry("../evil", '0', null, "bad".getBytes(ASCII)));
        tar.write(entry("etc/os-release", '0', null, "NAME=Ubuntu\n".getBytes(ASCII)));
        tar.write(new byte[1024]);

        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(tar.toByteArray()));

        assertTrue(new File(root, "usr/bin/hello").isFile());
        assertTrue(new File(root, "etc/os-release").isFile());
        assertFalse(outside.exists());
        assertEquals(1, extractor.rejectedEntries());
        assertEquals(2, extractor.extractedEntries());
    }

    @Test
    public void extractsSymlinkAndHardlink() throws Exception {
        File root = temporaryFolder.newFolder("links");

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(entry("usr/bin/target", '0', null, "data".getBytes(ASCII)));
        tar.write(entry("usr/bin/link", '1', "usr/bin/target", new byte[0]));
        tar.write(entry("etc/os-release", '2', "../usr/bin/target", new byte[0]));
        tar.write(new byte[1024]);

        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(tar.toByteArray()));

        assertTrue(new File(root, "usr/bin/link").isFile());
        assertTrue(Files.isSymbolicLink(new File(root, "etc/os-release").toPath()));
        assertEquals("../usr/bin/target",
                Files.readSymbolicLink(new File(root, "etc/os-release").toPath()).toString());
        assertEquals(0, extractor.hardlinkFallbacks());
    }

    @Test
    public void restrictiveDirectoryFinalModeDoesNotBlockLaterChildren() throws Exception {
        File root = temporaryFolder.newFolder("restrictive");
        JvmFileOps ops = new JvmFileOps();
        try {
            ByteArrayOutputStream tar = new ByteArrayOutputStream();
            tar.write(entry("ro", '5', null, new byte[0], 0555));
            tar.write(entry("ro/child", '0', null, "ok".getBytes(ASCII)));
            tar.write(entry("ro/target", '0', null, "target".getBytes(ASCII)));
            tar.write(entry("ro/link", '1', "ro/target", new byte[0]));
            tar.write(new byte[1024]);

            TarballExtractor extractor = new TarballExtractor(ops, root, null);
            extractor.extract(new ByteArrayInputStream(tar.toByteArray()));

            assertTrue(new File(root, "ro/child").isFile());
            assertTrue(new File(root, "ro/link").isFile());
            assertEquals("ok", readText(new File(root, "ro/child")));
            // Final archived mode applied only after extraction finished.
            assertEquals("r-xr-xr-x", permissionString(new File(root, "ro")));
        } finally {
            SafeFileTree.deleteTree(ops, root, root);
        }
    }

    @Test
    public void hardlinkFallsBackToCopyWhenLinkIsRefused() throws Exception {
        File root = temporaryFolder.newFolder("fallback");
        JvmFileOps inner = new JvmFileOps();
        FileOps noLink = new NoHardlinkOps(inner);

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(entry("usr/bin/target", '0', null, "payload".getBytes(ASCII)));
        tar.write(entry("usr/bin/link", '1', "usr/bin/target", new byte[0]));
        tar.write(new byte[1024]);

        TarballExtractor extractor = new TarballExtractor(noLink, root, null);
        extractor.extract(new ByteArrayInputStream(tar.toByteArray()));

        assertEquals(1, extractor.hardlinkFallbacks());
        assertTrue(new File(root, "usr/bin/link").isFile());
        assertEquals("payload", readText(new File(root, "usr/bin/link")));
    }

    @Test
    public void hardlinkTargetTraversalIsRejected() throws Exception {
        File root = temporaryFolder.newFolder("badlink");
        File outside = new File(root.getParentFile(), "outside-target");
        Files.write(outside.toPath(), "outside".getBytes(ASCII));

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(entry("usr/bin/link", '1', "../outside-target", new byte[0]));
        tar.write(new byte[1024]);

        TarballExtractor extractor = new TarballExtractor(new JvmFileOps(), root, null);
        extractor.extract(new ByteArrayInputStream(tar.toByteArray()));

        assertEquals(1, extractor.rejectedEntries());
        assertFalse(new File(root, "usr/bin/link").exists());
    }

    @Test
    public void failedExtractionLeavesCleanableStagingForRetry() throws Exception {
        File root = temporaryFolder.newFolder("retry");
        JvmFileOps ops = new JvmFileOps();

        ByteArrayOutputStream broken = new ByteArrayOutputStream();
        broken.write(entry("a/b", '0', null, "one".getBytes(ASCII)));
        byte[] bad = entry("broken", '0', null, "x".getBytes(ASCII));
        bad[0] = 'X';
        broken.write(bad);
        broken.write(new byte[1024]);

        boolean threw = false;
        try {
            new TarballExtractor(ops, root, null)
                    .extract(new ByteArrayInputStream(broken.toByteArray()));
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        assertTrue(new File(root, "a/b").exists());

        // Cleanup must recover and remove the partial tree.
        SafeFileTree.deleteTree(ops, root, root);
        assertFalse(root.exists());

        // Retry from clean staging succeeds.
        ByteArrayOutputStream good = new ByteArrayOutputStream();
        good.write(entry("ok.txt", '0', null, "ok".getBytes(ASCII)));
        good.write(new byte[1024]);
        root.mkdirs();
        new TarballExtractor(ops, root, null)
                .extract(new ByteArrayInputStream(good.toByteArray()));
        assertTrue(new File(root, "ok.txt").isFile());
    }

    @Test(expected = IOException.class)
    public void rejectsCorruptHeader() throws Exception {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        byte[] block = entry("broken", '0', null, "x".getBytes(ASCII));
        block[0] = 'X';
        tar.write(block);
        tar.write(new byte[1024]);

        TarballExtractor extractor = new TarballExtractor(
                new JvmFileOps(), temporaryFolder.newFolder("corrupt"), null);
        extractor.extract(new ByteArrayInputStream(tar.toByteArray()));
    }

    private static byte[] entry(String name, char type, String link, byte[] data) {
        return entry(name, type, link, data, 0755);
    }

    private static byte[] entry(String name, char type, String link, byte[] data, int mode) {
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeString(header, 100, 8, String.format("%07o", mode));
        writeString(header, 108, 8, String.format("%07o", 0));
        writeString(header, 116, 8, String.format("%07o", 0));
        writeString(header, 124, 12, String.format("%011o", data.length));
        writeString(header, 136, 12, String.format("%011o", 0));
        for (int i = 148; i < 156; i++) header[i] = ' ';
        header[156] = (byte) type;
        if (link != null) writeString(header, 157, 100, link);
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");

        long checksum = 0;
        for (byte b : header) checksum += b & 0xFF;
        String checksumText = String.format("%06o", checksum);
        writeString(header, 148, 8, checksumText);
        header[154] = 0;
        header[155] = ' ';

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header, 0, header.length);
        if (data.length > 0) {
            out.write(data, 0, data.length);
            int pad = (512 - (data.length % 512)) % 512;
            out.write(new byte[pad], 0, pad);
        }
        return out.toByteArray();
    }

    private static void writeString(byte[] target, int offset, int length, String value) {
        byte[] bytes = value.getBytes(ASCII);
        int count = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, target, offset, count);
    }

    private static String readText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, ASCII);
    }

    private static String permissionString(File file) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()));
    }

    static class JvmFileOps implements FileOps {
        @Override
        public boolean exists(File file) {
            return file.exists();
        }

        @Override
        public boolean isDirectory(File file) {
            return file.isDirectory();
        }

        @Override
        public boolean isSymlink(File file) {
            return Files.isSymbolicLink(file.toPath());
        }

        @Override
        public boolean isRegularFile(File file) {
            return Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public void mkdirs(File dir, int mode) throws IOException {
            Files.createDirectories(dir.toPath());
            setMode(dir, mode);
        }

        @Override
        public OutputStream createFile(File file, int mode) throws IOException {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) Files.createDirectories(parent.toPath());
            OutputStream out = new FileOutputStream(file);
            setMode(file, mode);
            return out;
        }

        @Override
        public void copyFile(File source, File destination, int mode) throws IOException {
            OutputStream out = createFile(destination, mode);
            InputStream in = Files.newInputStream(source.toPath());
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            } finally {
                in.close();
                out.close();
            }
        }

        @Override
        public void symlink(String target, File link) throws IOException {
            Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target));
        }

        @Override
        public void hardlink(File existing, File link) throws IOException {
            Files.createLink(link.toPath(), existing.toPath());
        }

        @Override
        public void setMode(File file, int mode) {
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString(
                        permissionString(mode));
                Files.setPosixFilePermissions(file.toPath(), perms);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void setLastModified(File file, long timeMillis) {
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(timeMillis);
        }

        @Override
        public String canonicalPath(File file) throws IOException {
            return file.getCanonicalPath();
        }

        private static String permissionString(int mode) {
            StringBuilder b = new StringBuilder(9);
            b.append((mode & 0400) != 0 ? 'r' : '-');
            b.append((mode & 0200) != 0 ? 'w' : '-');
            b.append((mode & 0100) != 0 ? 'x' : '-');
            b.append((mode & 0040) != 0 ? 'r' : '-');
            b.append((mode & 0020) != 0 ? 'w' : '-');
            b.append((mode & 0010) != 0 ? 'x' : '-');
            b.append((mode & 0004) != 0 ? 'r' : '-');
            b.append((mode & 0002) != 0 ? 'w' : '-');
            b.append((mode & 0001) != 0 ? 'x' : '-');
            return b.toString();
        }
    }

    /** Simulates Android's SELinux refusal of hardlinks for untrusted apps. */
    static final class NoHardlinkOps implements FileOps {
        private final FileOps delegate;

        NoHardlinkOps(FileOps delegate) {
            this.delegate = delegate;
        }

        @Override
        public void hardlink(File existing, File link) throws IOException {
            throw new IOException("link failed: EACCES (Permission denied)");
        }

        @Override
        public boolean exists(File f) {
            return delegate.exists(f);
        }

        @Override
        public boolean isDirectory(File f) {
            return delegate.isDirectory(f);
        }

        @Override
        public boolean isSymlink(File f) {
            return delegate.isSymlink(f);
        }

        @Override
        public boolean isRegularFile(File f) {
            return delegate.isRegularFile(f);
        }

        @Override
        public void mkdirs(File d, int m) throws IOException {
            delegate.mkdirs(d, m);
        }

        @Override
        public OutputStream createFile(File f, int m) throws IOException {
            return delegate.createFile(f, m);
        }

        @Override
        public void copyFile(File s, File d, int m) throws IOException {
            delegate.copyFile(s, d, m);
        }

        @Override
        public void symlink(String t, File l) throws IOException {
            delegate.symlink(t, l);
        }

        @Override
        public void setMode(File f, int m) {
            delegate.setMode(f, m);
        }

        @Override
        public void setLastModified(File f, long t) {
            delegate.setLastModified(f, t);
        }

        @Override
        public String canonicalPath(File f) throws IOException {
            return delegate.canonicalPath(f);
        }
    }
}
