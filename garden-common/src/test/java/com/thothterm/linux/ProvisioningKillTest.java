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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A provisioning command that times out or is interrupted must really stop.
 * On Android destroyForcibly() is only SIGTERM, which PRoot survives while a
 * traced command hangs (measured on a device), so every stop goes through
 * killHard(), which escalates to SIGKILL. The behaviour itself is checked on a
 * device by the optional-setup hang gates.
 */
public class ProvisioningKillTest {
    private static String source() throws IOException {
        return new String(Files.readAllBytes(
                new File("src/main/java/com/thothterm/linux/RootfsManager.java").toPath()),
                StandardCharsets.UTF_8);
    }

    @Test
    public void everyStopEscalatesToSigkill() throws IOException {
        String s = source();
        int start = s.indexOf("private static void killHard(Process process)");
        assertTrue(start > 0);
        String killHard = s.substring(start, s.indexOf("\n    }\n", start));
        assertTrue(killHard.contains("process.destroyForcibly();"));
        assertTrue(killHard.contains("process.waitFor(2, TimeUnit.SECONDS)"));
        assertTrue(killHard.contains("if (!process.isAlive()) return;"));
        assertTrue(killHard.contains("android.system.Os.kill(pid.getInt(process), android.system.OsConstants.SIGKILL)"));

        int doc = s.lastIndexOf("/**", start);
        String outside = s.substring(0, doc) + s.substring(start + killHard.length());
        assertEquals("destroyForcibly() outside killHard()", -1, outside.indexOf("destroyForcibly()"));
        assertEquals("destroy() outside killHard()", -1, outside.indexOf("process.destroy()"));
    }

    @Test
    public void timeoutAndInterruptBothKillHard() throws IOException {
        String s = source();
        int start = s.indexOf("private String runProvisioning(");
        String run = s.substring(start, s.indexOf("private static void killHard", start));
        int calls = 0;
        for (int i = run.indexOf("killHard(process);"); i >= 0; i = run.indexOf("killHard(process);", i + 1)) {
            calls++;
        }
        assertEquals(2, calls);
        assertTrue(run.contains("// A guest command can take minutes: on the UI thread that is an ANR."));
    }
}
