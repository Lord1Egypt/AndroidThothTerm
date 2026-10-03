/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void readsTheHangupBitOfSigIgn() {
        // nohup: SIGHUP (bit 0) ignored, alongside Android's ignored SIGPIPE.
        assertTrue(SessionProcesses.hangupIgnored(
                "Name:\tsleep\nSigBlk:\t0000000000000000\nSigIgn:\t0000000000001001\nSigCgt:\t0\n"));
        // An ordinary job after the libtermexec fix: SIGHUP at its default.
        assertFalse(SessionProcesses.hangupIgnored(
                "Name:\tsleep\nSigIgn:\t0000000000001000\nSigCgt:\t0000000000000000\n"));
        // bash ignores SIGINT/SIGQUIT/SIGTERM (bits 1, 2, 14) but not SIGHUP.
        assertFalse(SessionProcesses.hangupIgnored("SigIgn:\t0000000000384006\n"));
    }

    @Test
    public void unreadableHangupStateIsNotIgnored() {
        assertFalse(SessionProcesses.hangupIgnored(""));
        assertFalse(SessionProcesses.hangupIgnored("SigIgn:\n"));
        assertFalse(SessionProcesses.hangupIgnored("SigIgn:\tzz\n"));
        assertFalse(SessionProcesses.ignoresHangup(-7));
    }

    @Test
    public void seesAProcessThatIgnoresHangupLikeNohup() throws Exception {
        org.junit.Assume.assumeTrue("needs Linux /proc", new java.io.File("/proc/self/status").exists());
        java.lang.Process sleeper = new ProcessBuilder("sh", "-c", "trap '' HUP; exec sleep 30").start();
        try {
            long pid = pidOf(sleeper);
            boolean ignored = false;
            for (int i = 0; i < 100 && !ignored; ++i) {
                ignored = SessionProcesses.ignoresHangup((int) pid);
                if (!ignored) Thread.sleep(20);
            }
            assertTrue(ignored);
        } finally {
            sleeper.destroyForcibly();
        }
    }

    /** Java 8 has no Process.pid(); read it the way the JDK's UNIXProcess stores it. */
    private static long pidOf(java.lang.Process process) throws Exception {
        try {
            return (Long) java.lang.Process.class.getMethod("pid").invoke(process);
        } catch (NoSuchMethodException e) {
            java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            return field.getInt(process);
        }
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
    }

    @Test
    public void rejectsMalformedLines() {
        assertEquals(-1, SessionProcesses.sessionIdOf(""));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 sleep S 1 2 3"));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 (sleep) S 1"));
        assertEquals(-1, SessionProcesses.sessionIdOf("123 (sleep) S 1 2 x 0"));
    }
}
