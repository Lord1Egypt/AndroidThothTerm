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

package com.thothterm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Regular Terminal has no LAN Mode, and never will: no listening socket,
 * no HTTP or WebSocket server, no browser terminal, no remote upload. Its
 * uploads come only from the phone's own document picker.
 */
public class LanFreeTest {
    private static final File MAIN = new File("src/main");

    private static final String[] FORBIDDEN = {
            // Network listeners. (android.net.LocalServerSocket, the inherited
            // same-uid command socket, is not one.)
            "java.net.ServerSocket", "new ServerSocket(", "ServerSocketChannel",
            "java.net.InetSocketAddress", "WebSocket", "Sec-WebSocket",
            "com.thothterm.lan", "LanMode", "LanController", "LanServer",
            "xterm.js", "@xterm", "ThothXterm", "/api/upload", "X-ThothTerm-Upload",
    };

    @Test
    public void noServerOfAnyKind() throws IOException {
        List<File> files = new ArrayList<>();
        collect(MAIN, files);
        assertTrue(files.size() > 10);
        for (File file : files) {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            for (String token : FORBIDDEN) {
                assertFalse(file + " contains " + token, text.contains(token));
            }
        }
        assertFalse(new File(MAIN, "java/com/thothterm/lan").exists());
        assertFalse(new File(MAIN, "assets/lan").exists());
    }

    @Test
    public void uploadsComeFromTheDocumentPickerOnly() throws IOException {
        String term = new String(Files.readAllBytes(new File(MAIN, "java/jackpal/androidterm/Term.java").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(term.contains("new ActivityResultContracts.OpenMultipleDocuments()"));
        assertTrue(term.contains("new ActivityResultContracts.OpenDocumentTree()"));
        assertTrue(term.contains("SessionDirectory.hostView(), pid, true"));
        String manifest = new String(Files.readAllBytes(new File(MAIN, "AndroidManifest.xml").toPath()),
                StandardCharsets.UTF_8);
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"));
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out);
            else out.add(child);
        }
    }
}
