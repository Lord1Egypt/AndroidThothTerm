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

import java.io.IOException;
import java.util.Arrays;

/**
 * The current working directory of one terminal session, read from /proc.
 * <p>
 * Every terminal's process is started with setsid() on its own PTY, so the
 * session id is the leader's pid, and the kernel records the PTY's foreground
 * process group (field 8 of /proc/[pid]/stat). The directory is that group's
 * working directory: the shell's at a prompt, a subshell's or a program's
 * while one runs in front. Nothing is typed into the shell and nothing is read
 * from the screen, so any shell works and output cannot spoof it; the leader
 * pid is the app's own record of its session, never a value from a browser.
 * <p>
 * In a Garden edition the leader is PRoot, which is never the answer -- its
 * guest shell is -- and PRoot patch 0005 keeps each guest process's kernel
 * working directory in step with its guest one.
 */
public final class SessionDirectory {
    static final int DEFAULT_UMASK = 022;
    /** How often {@link #resolveWhenReady} looks again while the guest shell starts. */
    static final long POLL_MS = 200;
    private static final String STARTING = "no guest process in front yet";
    private static final String DELETED = " (deleted)";

    /** Read access to /proc; tests substitute their own. */
    public interface Proc {
        /** The text of /proc/[pid]/stat. */
        String stat(int pid) throws IOException;

        /** The text of /proc/[pid]/status. */
        String status(int pid) throws IOException;

        /** The target of /proc/[pid]/cwd. */
        String cwd(int pid) throws IOException;

        /** Every pid listed in /proc. */
        int[] pids() throws IOException;
    }

    /** Where uploads may go, and how a directory is shown. */
    public interface View {
        /** The path to show for {@code hostDirectory}; OUTSIDE if uploads may not go there. */
        String display(String hostDirectory) throws UploadError;
    }

    /** Waiting between attempts; tests substitute their own. */
    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private SessionDirectory() {
    }

    /**
     * As {@link #resolve}, but a session whose guest shell has not yet taken
     * the terminal -- PRoot itself still in front, as for a second or two
     * after a browser terminal opens -- is looked at again until
     * {@code timeoutMs} has passed.
     */
    public static UploadTarget resolveWhenReady(Proc proc, View view, int leader, boolean leaderIsShell,
                                                long timeoutMs, Sleeper sleeper) throws UploadError {
        long waited = 0;
        while (true) {
            try {
                return resolve(proc, view, leader, leaderIsShell);
            } catch (UploadError e) {
                if (!STARTING.equals(e.getMessage()) || waited >= timeoutMs) throw e;
            }
            try {
                sleeper.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UploadError(UploadError.Code.NO_DIRECTORY, "interrupted");
            }
            waited += POLL_MS;
        }
    }

    /**
     * The upload target of the session led by {@code leader}.
     *
     * @param leaderIsShell false when the leader is PRoot, whose own directory
     *                      is never the session's
     */
    public static UploadTarget resolve(Proc proc, View view, int leader, boolean leaderIsShell)
            throws UploadError {
        if (leader <= 0) throw error(UploadError.Code.NO_DIRECTORY, "no session");
        int foreground;
        try {
            String stat = proc.stat(leader);
            if (field(stat, 6) != leader) throw error(UploadError.Code.NO_DIRECTORY, "not a session leader");
            foreground = field(stat, 8);
        } catch (IOException e) {
            if (e instanceof UploadError) throw (UploadError) e;
            throw error(UploadError.Code.NO_DIRECTORY, "session ended");
        }
        if (foreground <= 0) throw error(UploadError.Code.NO_DIRECTORY, "no foreground group");

        int pid = member(proc, foreground, leader) ? foreground : -1;
        if (pid < 0) {
            // The group's leader has exited but the group lives on (a
            // pipeline whose first command finished): any member will do.
            try {
                int[] all = proc.pids();
                Arrays.sort(all);
                for (int p : all) {
                    if (member(proc, p, foreground, leader)) {
                        pid = p;
                        break;
                    }
                }
            } catch (IOException ignore) {
                // No listing: nothing more to try.
            }
        }
        if (pid < 0) throw error(UploadError.Code.NO_DIRECTORY, "foreground group is gone");
        if (pid == leader && !leaderIsShell) {
            throw error(UploadError.Code.NO_DIRECTORY, STARTING);
        }

        String cwd;
        try {
            cwd = proc.cwd(pid);
        } catch (IOException e) {
            throw error(UploadError.Code.NO_DIRECTORY, "working directory unreadable");
        }
        if (cwd.endsWith(DELETED)) throw error(UploadError.Code.DIRECTORY_GONE, "directory removed");
        if (!cwd.startsWith("/")) throw error(UploadError.Code.NO_DIRECTORY, "not a path");
        String shown = view.display(cwd);

        int umask = DEFAULT_UMASK;
        try {
            umask = umaskOf(proc.status(pid));
        } catch (IOException ignore) {
            // The process ended just now; the directory was still right.
        }
        return new UploadTarget(cwd, shown, umask);
    }

    /** Uploads may go to any directory the app's own shell can reach, but not a kernel file system. */
    public static View hostView() {
        return host -> {
            for (String virtual : new String[]{"/proc", "/sys", "/dev"}) {
                if (host.equals(virtual) || host.startsWith(virtual + "/")) {
                    throw error(UploadError.Code.OUTSIDE, "kernel file system");
                }
            }
            return host;
        };
    }

    /**
     * Uploads go only inside the guest rootfs at {@code rootfs} (the canonical
     * path PRoot was given), shown as guest paths. PRoot's bindings -- /proc,
     * /dev, /sys, resolv.conf -- lie outside it and are refused.
     */
    public static View guestView(String rootfs) {
        final String root = rootfs.endsWith("/") ? rootfs.substring(0, rootfs.length() - 1) : rootfs;
        return host -> {
            if (host.equals(root)) return "/";
            if (host.startsWith(root + "/")) return host.substring(root.length());
            throw error(UploadError.Code.OUTSIDE, "outside the guest rootfs");
        };
    }

    private static boolean member(Proc proc, int group, int session) {
        return member(proc, group, group, session);
    }

    private static boolean member(Proc proc, int pid, int group, int session) {
        try {
            String stat = proc.stat(pid);
            return field(stat, 5) == group && field(stat, 6) == session;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Numeric field {@code number} (1-based, 3 or later) of a stat line.
     * Field 2, the command name, may contain spaces and ')', so fields are
     * counted from the last ')'.
     */
    static int field(String stat, int number) {
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

    /** The {@code Umask:} line of /proc/[pid]/status (Linux 4.7+), or 022. */
    static int umaskOf(String status) {
        for (String line : status.split("\n")) {
            if (!line.startsWith("Umask:")) continue;
            try {
                return Integer.parseInt(line.substring("Umask:".length()).trim(), 8) & 0777;
            } catch (NumberFormatException e) {
                return DEFAULT_UMASK;
            }
        }
        return DEFAULT_UMASK;
    }

    private static UploadError error(UploadError.Code code, String why) {
        return new UploadError(code, why);
    }
}
