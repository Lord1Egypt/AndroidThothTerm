/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcOverridesTest {
    @Test
    public void statHasBootTimeAndOneLinePerCpu() {
        String stat = ProcOverrides.statText(1793500000L, 8, 3600);
        assertEquals(1793500000L, ProcOverrides.bootTime(stat));
        String[] lines = stat.split("\n");
        // procps reads "cpu " first, then cpuN; all ten counters, idle > 0.
        assertTrue(lines[0].matches("cpu  0 0 0 [1-9][0-9]* 0 0 0 0 0 0"));
        for (int i = 0; i < 8; i++) assertTrue(lines[i + 1].startsWith("cpu" + i + " "));
        assertTrue(stat.contains("\nbtime 1793500000\n"));
        assertEquals(2880000L, Long.parseLong(lines[0].split(" +")[4]));
    }

    @Test
    public void statNeverHasZeroCpusOrZeroIdle() {
        String stat = ProcOverrides.statText(1L, 0, 0);
        assertTrue(stat.contains("\ncpu0 0 0 0 100 "));
    }

    @Test
    public void uptimeIsSecondsWithTwoDecimals() {
        assertEquals("3600.50 14402.00\n", ProcOverrides.uptimeText(3600500L, 4));
    }

    @Test
    public void bootTimeOfOtherTextIsMinusOne() {
        assertEquals(-1L, ProcOverrides.bootTime(""));
        assertEquals(-1L, ProcOverrides.bootTime("btime x\n"));
    }

    /** A process's start time must read the same in every session. */
    @Test
    public void writtenBootTimeIsKeptWithinASecond() {
        assertEquals(1000L, ProcOverrides.stableBootTime(1000L, 1001L));
        assertEquals(1000L, ProcOverrides.stableBootTime(1000L, 999L));
        assertEquals(1005L, ProcOverrides.stableBootTime(1000L, 1005L)); // rebooted or clock set
        assertEquals(1005L, ProcOverrides.stableBootTime(-1L, 1005L));
    }
}
