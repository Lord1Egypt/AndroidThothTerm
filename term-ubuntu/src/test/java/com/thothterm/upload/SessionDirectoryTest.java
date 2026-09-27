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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * A session's directory is its foreground process group's, read from /proc,
 * and only ever from processes of that same session.
 */
public class SessionDirectoryTest {
    private static final String ROOTFS = "/data/data/com.thothterm.ubuntu/files/linux/ubuntu-26.04/rootfs";

    /** A fake /proc: pid -> (comm, pgrp, session, tpgid, cwd, umask). */
    private static final class FakeProc implements SessionDirectory.Proc {
        final Map<Integer, String> stat = new HashMap<>();
        final Map<Integer, String> cwd = new HashMap<>();
        final Map<Integer, String> status = new HashMap<>();

        FakeProc process(int pid, String comm, int pgrp, int session, int tpgid, String dir) {
            stat.put(pid, pid + " (" + comm + ") S 1 " + pgrp + " " + session + " 34816 " + tpgid
                    + " 4194560 0 0 0 0 0 0 0 0 20 0 1 0 0 0 0");
            if (dir != null) cwd.put(pid, dir);
            status.put(pid, "Name:\t" + comm + "\nUmask:\t0022\nState:\tS (sleeping)\n");
            return this;
        }

        @Override
        public String stat(int pid) throws IOException {
            String s = stat.get(pid);
            if (s == null) throw new IOException("no such process");
            return s;
        }

        @Override
        public String status(int pid) throws IOException {
            String s = status.get(pid);
            if (s == null) throw new IOException("no such process");
            return s;
        }

        @Override
        public String cwd(int pid) throws IOException {
            String s = cwd.get(pid);
            if (s == null) throw new IOException("no such process");
            return s;
        }

        @Override
        public int[] pids() {
            TreeSet<Integer> all = new TreeSet<>(stat.keySet());
            int[] result = new int[all.size()];
            int i = 0;
            for (int pid : all) result[i++] = pid;
            return result;
        }
    }

    @Test
    public void aRegularShellAtItsPromptIsTheSession() throws Exception {
        FakeProc proc = new FakeProc().process(500, "sh", 500, 500, 500, "/data/user/0/com.thothterm/app_HOME/src");
        UploadTarget t = SessionDirectory.resolve(proc, SessionDirectory.hostView(), 500, true);
        assertEquals("/data/user/0/com.thothterm/app_HOME/src", t.hostPath);
        assertEquals("/data/user/0/com.thothterm/app_HOME/src", t.displayPath);
        assertEquals(022, t.umask);
    }

    @Test
    public void aGuestShellBehindPRootIsShownAsAGuestPath() throws Exception {
        // proot (leader, own group), su (proot's group), bash (own group, in front)
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 712, "/")
                .process(705, "su", 700, 700, 712, ROOTFS + "/home/thoth")
                .process(712, "bash", 712, 700, 712, ROOTFS + "/home/thoth/projects/مشروع جديد");
        UploadTarget t = SessionDirectory.resolve(proc, SessionDirectory.guestView(ROOTFS), 700, false);
        assertEquals(ROOTFS + "/home/thoth/projects/مشروع جديد", t.hostPath);
        assertEquals("/home/thoth/projects/مشروع جديد", t.displayPath);
    }

    @Test
    public void theForegroundJobDecidesNotTheShell() throws Exception {
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 900, "/")
                .process(712, "bash", 712, 700, 900, ROOTFS + "/home/thoth")
                .process(900, "bash", 900, 700, 900, ROOTFS + "/tmp/sub shell");
        UploadTarget t = SessionDirectory.resolve(proc, SessionDirectory.guestView(ROOTFS), 700, false);
        assertEquals("/tmp/sub shell", t.displayPath);
    }

    @Test
    public void aGroupWhoseLeaderExitedIsFoundThroughItsMembers() throws Exception {
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 950, "/")
                .process(951, "less", 950, 700, 950, ROOTFS + "/home/thoth/logs");
        UploadTarget t = SessionDirectory.resolve(proc, SessionDirectory.guestView(ROOTFS), 700, false);
        assertEquals("/home/thoth/logs", t.displayPath);
    }

    @Test
    public void anotherSessionsProcessIsNeverUsed() throws Exception {
        // The tpgid names a group, but its process belongs to another session
        // (a reused pid): refused rather than guessed.
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 800, "/")
                .process(800, "bash", 800, 799, 800, ROOTFS + "/home/other");
        expect(proc, SessionDirectory.guestView(ROOTFS), 700, false, UploadError.Code.NO_DIRECTORY);
    }

    @Test
    public void twoSessionsKeepTheirOwnDirectories() throws Exception {
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 712, "/")
                .process(712, "bash", 712, 700, 712, ROOTFS + "/home/thoth/upload-test/a")
                .process(800, "proot", 800, 800, 812, "/")
                .process(812, "bash", 812, 800, 812, ROOTFS + "/home/thoth/upload-test/b");
        SessionDirectory.View view = SessionDirectory.guestView(ROOTFS);
        assertEquals("/home/thoth/upload-test/a", SessionDirectory.resolve(proc, view, 700, false).displayPath);
        assertEquals("/home/thoth/upload-test/b", SessionDirectory.resolve(proc, view, 800, false).displayPath);
    }

    @Test
    public void pRootItselfInFrontIsNotAnAnswer() {
        FakeProc proc = new FakeProc().process(700, "proot", 700, 700, 700, ROOTFS + "/home/thoth");
        expect(proc, SessionDirectory.guestView(ROOTFS), 700, false, UploadError.Code.NO_DIRECTORY);
    }

    @Test
    public void aDeletedDirectoryIsReported() {
        FakeProc proc = new FakeProc().process(500, "sh", 500, 500, 500, "/data/x/gone (deleted)");
        expect(proc, SessionDirectory.hostView(), 500, true, UploadError.Code.DIRECTORY_GONE);
    }

    @Test
    public void bindingsAndKernelFileSystemsAreOutside() {
        for (String dir : new String[]{"/proc", "/proc/self", "/dev", "/sys/fs", "/",
                "/data/data/com.thothterm.ubuntu/files/linux/ubuntu-26.04/rootfsX/home"}) {
            FakeProc proc = new FakeProc()
                    .process(700, "proot", 700, 700, 712, "/")
                    .process(712, "bash", 712, 700, 712, dir);
            expect(proc, SessionDirectory.guestView(ROOTFS), 700, false, UploadError.Code.OUTSIDE);
        }
        for (String dir : new String[]{"/proc", "/proc/1", "/dev/pts", "/sys"}) {
            FakeProc proc = new FakeProc().process(500, "sh", 500, 500, 500, dir);
            expect(proc, SessionDirectory.hostView(), 500, true, UploadError.Code.OUTSIDE);
        }
    }

    @Test
    public void theRootfsItselfIsTheGuestRoot() throws Exception {
        FakeProc proc = new FakeProc()
                .process(700, "proot", 700, 700, 712, "/")
                .process(712, "bash", 712, 700, 712, ROOTFS);
        assertEquals("/", SessionDirectory.resolve(proc, SessionDirectory.guestView(ROOTFS), 700, false)
                .displayPath);
    }

    @Test
    public void aSessionThatEndedOrNeverWasHasNoDirectory() {
        FakeProc proc = new FakeProc();
        expect(proc, SessionDirectory.hostView(), 500, true, UploadError.Code.NO_DIRECTORY);
        expect(proc, SessionDirectory.hostView(), -1, true, UploadError.Code.NO_DIRECTORY);
        // Not a session leader: the pid is not what the app started.
        proc.process(500, "sh", 500, 400, 500, "/data/x");
        expect(proc, SessionDirectory.hostView(), 500, true, UploadError.Code.NO_DIRECTORY);
    }

    @Test
    public void commandNamesWithParenthesesDoNotConfuseTheParser() throws Exception {
        FakeProc proc = new FakeProc();
        proc.stat.put(500, "500 (my (odd) sh) S 1 500 500 34816 500 4194560");
        proc.cwd.put(500, "/data/x");
        proc.status.put(500, "Umask:\t0077\n");
        UploadTarget t = SessionDirectory.resolve(proc, SessionDirectory.hostView(), 500, true);
        assertEquals("/data/x", t.hostPath);
        assertEquals(077, t.umask);
    }

    @Test
    public void umaskFallsBackTo022() {
        assertEquals(022, SessionDirectory.umaskOf("Name:\tsh\n"));
        assertEquals(027, SessionDirectory.umaskOf("Umask:\t0027\n"));
        assertEquals(022, SessionDirectory.umaskOf("Umask:\tbogus\n"));
    }

    private static void expect(FakeProc proc, SessionDirectory.View view, int leader, boolean shell,
                               UploadError.Code code) {
        try {
            SessionDirectory.resolve(proc, view, leader, shell);
            fail("resolved; expected " + code);
        } catch (UploadError e) {
            assertEquals(code, e.code);
        }
    }
}
