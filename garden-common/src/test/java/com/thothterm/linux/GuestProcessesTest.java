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

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class GuestProcessesTest {
    private static final String PROOT = "/data/app/~~x/com.thothterm.arch-y/lib/arm64/libproot.so";

    private static void process(File proc, String pid, String... argv) throws Exception {
        File dir = new File(proc, pid);
        dir.mkdirs();
        Files.write(new File(dir, "cmdline").toPath(),
                (String.join("\0", argv) + "\0").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void countsOnlyThisAppsProotProcesses() throws Exception {
        File proc = Files.createTempDirectory("proc").toFile();
        process(proc, "100", "com.thothterm.arch");
        process(proc, "200", PROOT, "--rootfs=/data/data/x", "/usr/bin/su");
        process(proc, "201", PROOT + ".old", "--rootfs=/x");
        process(proc, "202", "/usr/bin/pacman", "-Syu");
        process(proc, "300", PROOT, "--kill-on-exit");
        new File(proc, "self").mkdirs();
        new File(proc, "403").mkdirs(); // gone: no cmdline
        Files.write(new File(proc, "meminfo").toPath(), new byte[0]);

        assertEquals(2, GuestProcesses.countProot(proc, PROOT, 100));
        // The caller itself never counts.
        assertEquals(1, GuestProcesses.countProot(proc, PROOT, 300));
        assertEquals(0, GuestProcesses.countProot(new File(proc, "none"), PROOT, 1));
    }
}
