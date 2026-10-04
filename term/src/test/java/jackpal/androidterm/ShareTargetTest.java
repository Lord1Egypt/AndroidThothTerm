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

package jackpal.androidterm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * "Term here" (ACTION_SEND from any app) opens a window in the shared
 * directory. The path is the new shell's working directory, never text typed
 * into the terminal (docs/security/THORNS.md).
 */
public class ShareTargetTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void directoryIsUsedAsIs() throws IOException {
        File dir = tmp.newFolder("shared");
        assertEquals(dir, RemoteInterface.shareDirectory(dir.getPath()));
    }

    @Test
    public void fileOpensItsDirectory() throws IOException {
        File file = tmp.newFile("notes.txt");
        assertEquals(file.getParentFile(), RemoteInterface.shareDirectory(file.getPath()));
    }

    @Test
    public void nothingUsableOpensAPlainWindow() {
        assertNull(RemoteInterface.shareDirectory(null));
        assertNull(RemoteInterface.shareDirectory(""));
        assertNull(RemoteInterface.shareDirectory("relative/dir"));
        assertNull(RemoteInterface.shareDirectory(new File(tmp.getRoot(), "missing/file").getPath()));
    }

    /** Terminal control characters in a name are just part of a directory name. */
    @Test
    public void controlCharactersStayInThePath() throws IOException {
        String name = "d\r\n\u0003\u0015\u001b[2J\u007f";
        File dir = tmp.newFolder(name);
        assertEquals(dir, RemoteInterface.shareDirectory(dir.getPath()));
        File file = new File(dir, name);
        assertTrue(file.createNewFile());
        assertEquals(dir, RemoteInterface.shareDirectory(file.getPath()));
    }

    @Test
    public void shareTargetTypesNothing() throws IOException {
        String source = new String(Files.readAllBytes(
                new File("src/main/java/jackpal/androidterm/RemoteInterface.java").toPath()),
                StandardCharsets.UTF_8);
        String send = source.substring(source.indexOf("private void processSendAction"),
                source.indexOf("static File shareDirectory"));
        assertFalse(send, send.contains("quoteForBash"));
        assertFalse(send, send.contains("\"cd"));
        assertFalse(send, send.contains("openNewWindow("));
        assertFalse(source.contains("\"cd \""));
    }
}
