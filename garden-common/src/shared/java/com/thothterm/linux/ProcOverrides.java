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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stand-ins for {@code /proc/stat} and {@code /proc/uptime}, which Android's
 * SELinux policy denies to apps (Android 8+). Without them procps cannot find
 * the boot time: {@code ps -o lstart} fails ("Unable to get system boot
 * time"), and so does every program that records a process's start time
 * with it, such as Codex's background server. The files are bound over the
 * real paths only when those cannot be read.
 *
 * <p>Only {@code btime} has to be right: process start times in
 * {@code /proc/PID/stat} count from boot, so {@code btime} turns them into
 * wall-clock times. The CPU counters are plausible constants, and the uptime
 * is the value at the session's start.
 */
final class ProcOverrides {
    /** Clock ticks per second of every Android kernel (USER_HZ). */
    static final int USER_HZ = 100;
    /** The /proc files this class stands in for, by their names under /proc. */
    private static final String[] NAMES = {"stat", "uptime"};

    private ProcOverrides() {
    }

    /** A {@code /proc/stat} with {@code bootTimeSeconds} as its btime. */
    static String statText(long bootTimeSeconds, int cpus, long uptimeSeconds) {
        int n = Math.max(1, cpus);
        // Every CPU idle since boot: tools that compute usage from deltas see
        // a non-zero total and no load.
        long idle = Math.max(1, uptimeSeconds) * USER_HZ;
        StringBuilder out = new StringBuilder();
        out.append(cpuLine("cpu ", idle * n));
        for (int i = 0; i < n; i++) out.append(cpuLine("cpu" + i, idle));
        out.append("intr 0\nctxt 0\n");
        out.append("btime ").append(bootTimeSeconds).append('\n');
        out.append("processes 0\nprocs_running 1\nprocs_blocked 0\n");
        out.append("softirq 0 0 0 0 0 0 0 0 0 0 0\n");
        return out.toString();
    }

    private static String cpuLine(String name, long idle) {
        return name + " 0 0 0 " + idle + " 0 0 0 0 0 0\n";
    }

    /** A {@code /proc/uptime}: seconds since boot and idle seconds summed over CPUs. */
    static String uptimeText(long uptimeMillis, int cpus) {
        double up = uptimeMillis / 1000.0;
        return String.format(Locale.ROOT, "%.2f %.2f\n", up, up * Math.max(1, cpus));
    }

    /** The btime of a {@code /proc/stat} text, or -1. */
    static long bootTime(String statText) {
        for (String line : statText.split("\n")) {
            if (line.startsWith("btime ")) {
                try {
                    return Long.parseLong(line.substring(6).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Writes the stand-in for each file that cannot be read and removes the
     * one for each file that can, in {@code dir} below {@code root}.
     *
     * @param statReadable   whether the real /proc/stat can be read
     * @param uptimeReadable whether the real /proc/uptime can be read
     */
    static void refresh(FileOps ops, File root, File dir, long nowMillis, long uptimeMillis, int cpus,
                        boolean statReadable, boolean uptimeReadable) throws IOException {
        File stat = new File(dir, "stat");
        File uptime = new File(dir, "uptime");
        if (statReadable) {
            if (ops.type(stat) != FileOps.Type.NONE) ops.unlink(stat);
        } else {
            long computed = (nowMillis - uptimeMillis) / 1000;
            long written = ops.type(stat) == FileOps.Type.REGULAR
                    ? bootTime(ManagedFiles.read(ops, root, stat)) : -1;
            ManagedFiles.writeIfChanged(ops, root, stat,
                    statText(stableBootTime(written, computed), cpus, uptimeMillis / 1000), 0444);
        }
        if (uptimeReadable) {
            if (ops.type(uptime) != FileOps.Type.NONE) ops.unlink(uptime);
        } else {
            ManagedFiles.writeIfChanged(ops, root, uptime, uptimeText(uptimeMillis, cpus), 0444);
        }
    }

    /** PRoot binds for the stand-ins present in {@code dir}. */
    static List<String> binds(FileOps ops, File dir) throws IOException {
        List<String> binds = new ArrayList<>();
        for (String name : NAMES) {
            File file = new File(dir, name);
            if (ops.type(file) == FileOps.Type.REGULAR) {
                binds.add("--bind=" + file.getAbsolutePath() + ":/proc/" + name);
            }
        }
        return binds;
    }

    /**
     * The boot time to write: the one already written while it is within a
     * second of the new computation, so that a process's start time reads
     * the same in every session (programs compare it to recognise a process).
     */
    static long stableBootTime(long written, long computed) {
        return written >= 0 && Math.abs(written - computed) <= 1 ? written : computed;
    }
}
