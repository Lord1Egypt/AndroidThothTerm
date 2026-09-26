/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionProcessesTest {
    @Test
    public void readsTheSessionField() {
        assertEquals(17910, SessionProcesses.sessionIdOf(
                "17935 (sleep) S 17910 17935 17910 0 -1 4194304 101 0 0 0 0 0"));
    }

    @Test
    public void countsFieldsFromTheLastParenthesis() {
        // The command name may contain spaces and ')' of its own.
        assertEquals(4242, SessionProcesses.sessionIdOf(
                "5000 (my) prog x) R 4242 5000 4242 34816 5000 0"));
    }

    @Test
    public void shellIsTheLeaderOfItsOwnSession() {
        assertEquals(17910, SessionProcesses.sessionIdOf(
                "17910 (sh) S 17542 17910 17910 34816 17950 4194560 900 0"));
    }

    @Test
    public void readsProcessGroupAndTerminalForegroundGroup() {
        // pid (comm) state ppid pgrp session tty_nr tpgid ...
        String busyShell = "17910 (sh) S 17542 17910 17910 34816 17950 4194560 900 0";
        assertEquals(17910, SessionProcesses.processGroupOf(busyShell));
        assertEquals(17950, SessionProcesses.terminalForegroundGroupOf(busyShell));

        String noTerminal = "17935 (sleep) S 1 17935 17910 0 -1 4194304 101 0";
        assertEquals(-1, SessionProcesses.terminalForegroundGroupOf(noTerminal));
    }

    @Test
    public void findsTheCurrentProcessInItsOwnSession() throws Exception {
        java.io.File self = new java.io.File("/proc/self/stat");
        org.junit.Assume.assumeTrue("needs Linux /proc", self.exists());
        String stat;
        try (java.util.Scanner in = new java.util.Scanner(self, "UTF-8")) {
            stat = in.useDelimiter("\\A").next();
        }
        int pid = Integer.parseInt(stat.substring(0, stat.indexOf(' ')));
        int session = SessionProcesses.sessionIdOf(stat);

        org.junit.Assert.assertTrue(SessionProcesses.members(session).contains(pid));
        org.junit.Assert.assertFalse(SessionProcesses.members(-7).contains(pid));
        int group = SessionProcesses.processGroupOf(stat);
        org.junit.Assert.assertTrue(SessionProcesses.groupInSession(group, session));
        org.junit.Assert.assertFalse(SessionProcesses.groupInSession(group, -7));
    }

    @Test
    public void rejectsMalformedLines() {
        assertEquals(-1, SessionProcesses.sessionIdOf(""));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 sleep S 1 2 3"));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 (sleep) S 1"));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 (sleep) S 1 2 x 0"));
    }
}
