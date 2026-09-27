/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;

public class UploadNamesTest {

    @Test
    public void ordinaryAndUnicodeNamesPassUnchanged() throws Exception {
        for (String name : new String[]{"main.py", "README.md", ".hidden", "file with spaces.txt",
                "مرحبا.txt", "مشروع جديد", "résumé.txt", "日本語.txt", "emoji \uD83D\uDE00.txt",
                "C:notes", "a:b", "%", "100%", "trailing dot.", "-rf", "%41"}) {
            assertEquals(name, UploadNames.checkName(name));
        }
    }

    @Test
    public void unicodeIsNotNormalized() throws Exception {
        String nfd = "re\u0301sume\u0301.txt";
        String nfc = "r\u00e9sum\u00e9.txt";
        assertEquals(nfd, UploadNames.checkName(nfd));
        assertEquals(nfc, UploadNames.checkName(nfc));
    }

    @Test
    public void traversalAndSeparatorsAreRefused() {
        for (String name : new String[]{"", ".", "..", "../evil", "a/b", "/abs", "a\\b", "..\\evil",
                "C:", "c:", "nul\0byte", "line\nbreak", "tab\there", "del\u007f", "c1\u0085",
                "%2e%2e", "%2E%2e", "%2e", "%252e%252e", "a%2fb", "a%5cb", "%00",
                ".thothterm-upload-0123456789abcdef", "\uD800", "x\uDC00"}) {
            bad(name);
        }
        bad(null);
    }

    @Test
    public void namesAreLimitedTo255Utf8Bytes() throws Exception {
        String ascii = repeat("a", 255);
        assertEquals(ascii, UploadNames.checkName(ascii));
        bad(ascii + "a");
        // Arabic letters are two bytes each in UTF-8.
        UploadNames.checkName(repeat("م", 127));
        bad(repeat("م", 128));
    }

    @Test
    public void relativePathsAreCheckedComponentByComponent() throws Exception {
        assertEquals(Arrays.asList("src", "main.py"), UploadNames.checkRelativePath("src/main.py"));
        assertEquals(Arrays.asList("مشروع جديد", "مرحبا.txt"),
                UploadNames.checkRelativePath("مشروع جديد/مرحبا.txt"));
        for (String path : new String[]{"/abs/x", "a//b", "a/", "a/../b", "../../../data/x", "a/./b",
                "C:/x", "a\\b/c", "..%2f..%2fetc"}) {
            try {
                UploadNames.checkRelativePath(path);
                fail("accepted " + path);
            } catch (UploadError e) {
                assertEquals(UploadError.Code.BAD_NAME, e.code);
            }
        }
    }

    @Test
    public void pathsHaveADepthLimit() throws Exception {
        StringBuilder ok = new StringBuilder("d");
        for (int i = 1; i < UploadNames.MAX_DEPTH; ++i) ok.append("/d");
        assertEquals(UploadNames.MAX_DEPTH, UploadNames.checkRelativePath(ok.toString()).size());
        try {
            UploadNames.checkRelativePath(ok + "/d");
            fail("accepted a path deeper than the limit");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.BAD_NAME, e.code);
        }
    }

    @Test
    public void encodedPathsAreDecodedOnceAndStrictly() throws Exception {
        assertEquals(Arrays.asList("my project", "مرحبا.txt"), UploadNames.decodeRelativePath(
                "my%20project/%D9%85%D8%B1%D8%AD%D8%A8%D8%A7.txt"));
        assertEquals(Arrays.asList("100%.txt"), UploadNames.decodeRelativePath("100%25.txt"));
        for (String encoded : new String[]{"%2e%2e/x", "a/%2E%2E", "%2e", "..", "a%2fb", "a%5Cb",
                "%00", "%zz", "%4", "a b", "%C0%AF", "%ED%A0%80", "/x", "x/", "a//b",
                "%25%32%65%25%32%65"}) {
            try {
                UploadNames.decodeRelativePath(encoded);
                fail("accepted " + encoded);
            } catch (UploadError e) {
                assertEquals(UploadError.Code.BAD_NAME, e.code);
            }
        }
    }

    @Test
    public void keepBothNamesFollowTheUsualPattern() throws Exception {
        assertEquals("file.txt", UploadNames.candidate("file.txt", 0, false));
        assertEquals("file (1).txt", UploadNames.candidate("file.txt", 1, false));
        assertEquals("file (12).txt", UploadNames.candidate("file.txt", 12, false));
        assertEquals("archive (1).tar.gz", UploadNames.candidate("archive.tar.gz", 1, false));
        assertEquals("archive (1).TAR.XZ", UploadNames.candidate("archive.TAR.XZ", 1, false));
        assertEquals(".hidden (1)", UploadNames.candidate(".hidden", 1, false));
        assertEquals(".bashrc (2).bak", UploadNames.candidate(".bashrc.bak", 2, false));
        assertEquals("Makefile (1)", UploadNames.candidate("Makefile", 1, false));
        assertEquals("project (1)", UploadNames.candidate("project", 1, true));
        assertEquals("my.project (1)", UploadNames.candidate("my.project", 1, true));
        assertEquals("مرحبا (1).txt", UploadNames.candidate("مرحبا.txt", 1, false));
        assertEquals(".tar (1).gz", UploadNames.candidate(".tar.gz", 1, false));
    }

    @Test
    public void aKeepBothNameMayNotOutgrowTheLimit() {
        try {
            UploadNames.candidate(repeat("a", 250) + ".txt", 1, false);
            fail("accepted a name longer than 255 bytes");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.CONFLICT, e.code);
        }
    }

    private static void bad(String name) {
        try {
            UploadNames.checkName(name);
            fail("accepted " + name);
        } catch (UploadError e) {
            assertEquals(UploadError.Code.BAD_NAME, e.code);
        }
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; ++i) b.append(s);
        return b.toString();
    }
}
