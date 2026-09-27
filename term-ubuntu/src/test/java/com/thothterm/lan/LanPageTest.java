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

package com.thothterm.lan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The browser page's header, upload controls and what must not come back. */
public class LanPageTest {
    private static final File LAN = new File("src/main/assets/lan");

    private static String read(String name) throws IOException {
        return new String(Files.readAllBytes(new File(LAN, name).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void theHeaderOffersUploadFilesUploadFolderAndSignOutInThatOrder() throws IOException {
        String html = read("index.html");
        int files = html.indexOf("id=\"upload-files\"");
        int folder = html.indexOf("id=\"upload-folder\"");
        int signout = html.indexOf("id=\"signout\"");
        assertTrue(files > 0 && files < folder && folder < signout);
        assertTrue(html.contains("<input id=\"pick-files\" type=\"file\" multiple"));
        assertTrue(html.contains("<input id=\"pick-folder\" type=\"file\" webkitdirectory"));
    }

    @Test
    public void theCopyButtonIsGoneButKeyboardCopyStays() throws IOException {
        String html = read("index.html");
        String js = read("app.js");
        String css = read("app.css");
        assertFalse(html.contains("id=\"copy\""));
        assertFalse(js.contains("$('copy')"));
        assertFalse(css.contains("#copy"));
        // Ctrl+Shift+C and the browser's own selection and clipboard stay.
        assertTrue(js.contains("if (e.code === 'KeyC') { copySelection(); return false; }"));
        assertTrue(js.contains("function legacyCopy(text)"));
    }

    @Test
    public void uploadsNameTheTerminalNeverADirectory() throws IOException {
        String js = read("app.js");
        assertTrue(js.contains("api('/api/upload/begin', {\n      term: load(window.sessionStorage, TERM_KEY)"));
        assertFalse(js.contains("target:"));
        assertFalse(js.contains("cwd"));
        // Streamed by the browser from disk, never read into page memory.
        assertTrue(js.contains("xhr.send(item.file);"));
        assertFalse(js.contains("FileReader"));
        assertFalse(js.contains("arrayBuffer()"));
    }

    @Test
    public void thePageSaysUploadsAreNotEncrypted() throws IOException {
        assertTrue(read("index.html").contains(
                "LAN terminal traffic and uploaded file contents are not encrypted."));
        String strings = new String(Files.readAllBytes(new File("src/main/res/values/strings_lan.xml").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(strings.contains("LAN terminal traffic and uploaded file contents are not encrypted."));
    }

    @Test
    public void nothingIsLoadedFromElsewhere() throws IOException {
        for (String name : new String[]{"index.html", "app.js", "app.css"}) {
            String text = read(name);
            assertFalse(name, text.contains("https://"));
            assertFalse(name, text.matches("(?s).*src=\"//.*"));
        }
    }
}
