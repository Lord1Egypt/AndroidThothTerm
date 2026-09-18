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

        TarballExtractor extractor = new TarballExtractor(
                new JvmFileOps(), root, null);
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
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeString(header, 100, 8, String.format("%07o", 0755));
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

    private static final class JvmFileOps implements FileOps {
        @Override
        public boolean exists(File file) {
            return file.exists();
        }

        @Override
        public boolean isDirectory(File file) {
            return file.isDirectory();
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
        public void symlink(String target, File link) throws IOException {
            Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target));
        }

        @Override
        public void hardlink(File existing, File link) throws IOException {
            Files.createLink(link.toPath(), existing.toPath());
        }

        @Override
        public void setMode(File file, int mode) {
        }

        @Override
        public void setLastModified(File file, long timeMillis) {
        }

        @Override
        public String canonicalPath(File file) throws IOException {
            return file.getCanonicalPath();
        }
    }
}
