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

package jackpal.androidterm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the processes of one terminal session.
 * <p>
 * Every shell is started with setsid(), so its pid is the session id of the
 * shell itself and of everything it runs -- the foreground job and background
 * jobs alike, each of which job control puts in a process group of its own.
 * Hanging up a closed window therefore means signalling the session, not just
 * the shell's process group. Android only lets an app see its own processes
 * in /proc, so the scan never reaches another app.
 */
final class SessionProcesses {
    private SessionProcesses() {
    }

    /** The pids currently in session {@code sessionId}, read from /proc. */
    static List<Integer> members(int sessionId) {
        List<Integer> pids = new ArrayList<>();
        String[] entries = new File("/proc").list();
        if (entries == null) return pids;
        for (String entry : entries) {
            int pid = parsePid(entry);
            if (pid <= 0) continue;
            try {
                if (sessionIdOf(readStat(pid)) == sessionId) pids.add(pid);
            } catch (IOException ignore) {
                // The process exited while we looked; nothing to signal.
            }
        }
        return pids;
    }

    /**
     * Whether process group {@code pgid} still has a member in session
     * {@code sessionId}. Checked right before signalling the group, so a
     * group id recycled by an unrelated process is never hit.
     */
    static boolean groupInSession(int pgid, int sessionId) {
        for (int pid : members(sessionId)) {
            try {
                if (processGroupOf(readStat(pid)) == pgid) return true;
            } catch (IOException ignore) {
                // Exited meanwhile.
            }
        }
        return false;
    }

    /** The terminal's foreground process group as seen by {@code pid}, or -1. */
    static int foregroundGroupOf(int pid) {
        try {
            return terminalForegroundGroupOf(readStat(pid));
        } catch (IOException e) {
            return -1;
        }
    }

    /** The process group (field 5) of a /proc/[pid]/stat line, or -1. */
    static int processGroupOf(String stat) {
        return field(stat, 5);
    }

    /** The session id (field 6) of a /proc/[pid]/stat line, or -1. */
    static int sessionIdOf(String stat) {
        return field(stat, 6);
    }

    /** The controlling terminal's foreground group (field 8, tpgid), or -1. */
    static int terminalForegroundGroupOf(String stat) {
        return field(stat, 8);
    }

    /**
     * Numeric field {@code number} (1-based, 3 or later) of a stat line.
     * <p>
     * Field 2 is the command name in parentheses and may itself contain
     * spaces and ')', so the fields after it are counted from the last ')'.
     */
    private static int field(String stat, int number) {
        int end = stat.lastIndexOf(')');
        if (end < 0) return -1;
        String[] rest = stat.substring(end + 1).trim().split("\\s+");
        int index = number - 3;
        if (index < 0 || rest.length <= index) return -1;
        try {
            return Integer.parseInt(rest[index]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readStat(int pid) throws IOException {
        byte[] buffer = new byte[1024];
        int length = 0;
        try (InputStream in = new FileInputStream("/proc/" + pid + "/stat")) {
            int read;
            while (length < buffer.length
                    && (read = in.read(buffer, length, buffer.length - length)) > 0) {
                length += read;
            }
        }
        return new String(buffer, 0, length, "UTF-8");
    }

    private static int parsePid(String name) {
        if (name.isEmpty()) return -1;
        for (int i = 0; i < name.length(); ++i) {
            if (!Character.isDigit(name.charAt(i))) return -1;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
