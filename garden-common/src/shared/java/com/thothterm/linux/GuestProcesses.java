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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Whether any guest can be running. Every guest process is a tracee of one of
 * this app's PRoot processes, and PRoot takes its tracees down when it exits
 * (PTRACE_O_EXITKILL), so no PRoot means no guest process at all. Android only
 * shows an app its own processes in /proc.
 */
final class GuestProcesses {
    private GuestProcesses() {
    }

    /**
     * The processes under {@code proc} whose command line starts with
     * {@code prootPath}, other than {@code selfPid}. A process that exits
     * while it is being read is simply not counted.
     */
    static int countProot(File proc, String prootPath, int selfPid) {
        String[] entries = proc.list();
        if (entries == null) return 0;
        int count = 0;
        for (String name : entries) {
            int pid = parsePid(name);
            if (pid <= 0 || pid == selfPid) continue;
            String argv0 = firstArgument(new File(new File(proc, name), "cmdline"));
            if (prootPath.equals(argv0)) count++;
        }
        return count;
    }

    private static String firstArgument(File cmdline) {
        byte[] buffer = new byte[4096];
        int length = 0;
        try (InputStream in = new FileInputStream(cmdline)) {
            int read;
            while (length < buffer.length && (read = in.read(buffer, length, buffer.length - length)) > 0) {
                length += read;
            }
        } catch (IOException e) {
            return null;
        }
        int end = 0;
        while (end < length && buffer[end] != 0) end++;
        return new String(buffer, 0, end, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int parsePid(String name) {
        if (name.isEmpty() || name.length() > 9) return -1;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) < '0' || name.charAt(i) > '9') return -1;
        }
        return Integer.parseInt(name);
    }
}
