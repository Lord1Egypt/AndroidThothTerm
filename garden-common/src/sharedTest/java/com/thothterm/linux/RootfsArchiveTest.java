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
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;

public class RootfsArchiveTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void digestCoversTheWholeFileNotJustTheTar() throws Exception {
        // A tar padded far beyond its end marker, like a real 10 KiB-record
        // archive, plus trailing bytes after the gzip member.
        byte[] tar = new TarBuilder().file("etc/os-release", "ID=x\n").build();
        byte[] padded = new byte[tar.length + 300_000];
        System.arraycopy(tar, 0, padded, 0, tar.length);
        byte[] gz = gzip(padded);
        byte[] file = new byte[gz.length + 7];
        System.arraycopy(gz, 0, file, 0, gz.length);
        RootfsArchive.Result result = RootfsArchive.extract(new ByteArrayInputStream(file),
                temporaryFolder.newFolder("staging"), new JvmFileOps(), null);
        assertEquals(sha256(file), result.sha256);
        assertEquals(file.length, result.compressedBytes);
        RootfsArchive.verify(result, sha256(file));
    }

    @Test
    public void verifyRefusesAWrongDigest() throws Exception {
        RootfsArchive.Result result = RootfsArchive.extract(
                new ByteArrayInputStream(gzip(new TarBuilder().file("a", "a").build())),
                temporaryFolder.newFolder("wrong"), new JvmFileOps(), null);
        try {
            RootfsArchive.verify(result, "00");
            fail("a wrong digest must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("checksum"));
        }
    }

    @Test
    public void verifyRefusesAnyRejectedEntry() throws Exception {
        byte[] gz = gzip(new TarBuilder().file("ok", "ok").file("../evil", "x").build());
        RootfsArchive.Result result = RootfsArchive.extract(new ByteArrayInputStream(gz),
                temporaryFolder.newFolder("rejected"), new JvmFileOps(), null);
        assertEquals(1, result.rejected);
        try {
            RootfsArchive.verify(result, sha256(gz));
            fail("a pinned archive with an unsafe entry must not install");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("../evil"));
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(data);
        gz.close();
        return out.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        return RootfsArchive.toHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
