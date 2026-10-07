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

package com.thothterm.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

/** A signal is only sent to the process that was started, never to a process that inherited its pid. */
public class ThothDockIdentityTest {
    @Test
    public void aProcessIsRecognisedByPidAndStartTime() throws Exception {
        Process p = shell("echo $$; exec sleep 30");
        try {
            int pid = pidOf(p);
            long start = ThothDock.startTimeOf(pid);
            assertTrue(start > 0);
            assertTrue(ThothDock.sameProcess(pid, start));
            // A pid that now belongs to someone else has a different start time.
            assertFalse(ThothDock.sameProcess(pid, start + 1));
            assertFalse(ThothDock.sameProcess(pid, 0));
            assertFalse(ThothDock.sameProcess(0, start));
        } finally {
            p.destroyForcibly();
        }
    }

    @Test
    public void aProcessThatIsGoneIsNeverTheOneRecorded() throws Exception {
        Process p = shell("echo $$");
        int pid = pidOf(p);
        long start = ThothDock.startTimeOf(pid);
        p.waitFor();
        assertEquals(0, p.exitValue());
        // Reaped: the pid is free (or reused), the record no longer matches.
        assertFalse(ThothDock.sameProcess(pid, start == 0 ? 1 : start));
    }

    /** Starts sh -c script; the script prints its own pid first (java.lang.Process has no pid() at this API level). */
    private static Process shell(String script) throws IOException {
        return new ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start();
    }

    private static int pidOf(Process p) throws IOException {
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
        return Integer.parseInt(r.readLine().trim());
    }
}
