/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

public class ProcOverridesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
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

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Unreadable real files: both stand-ins are written, read-only, and bound. */
    @Test
    public void refreshWritesTheStandInsAndBindsThem() throws Exception {
        File root = temporaryFolder.newFolder("runtime");
        File dir = new File(root, "proc");
        JvmFileOps ops = new JvmFileOps();
        ProcOverrides.refresh(ops, root, dir, 1_000_000_000_000L, 3_600_000L, 4, false, false);
        assertEquals(1_000_000_000L - 3600, ProcOverrides.bootTime(read(new File(dir, "stat"))));
        assertEquals("3600.00 14400.00\n", read(new File(dir, "uptime")));
        assertEquals(0444, ops.permissions(new File(dir, "stat")) & 0777);
        assertEquals(Arrays.asList(
                "--bind=" + new File(dir, "stat").getAbsolutePath() + ":/proc/stat",
                "--bind=" + new File(dir, "uptime").getAbsolutePath() + ":/proc/uptime"),
                ProcOverrides.binds(ops, dir));
    }

    /** A later session keeps the boot time it wrote when the new one is within a second. */
    @Test
    public void aLaterSessionKeepsTheBootTime() throws Exception {
        File root = temporaryFolder.newFolder("runtime2");
        File dir = new File(root, "proc");
        JvmFileOps ops = new JvmFileOps();
        ProcOverrides.refresh(ops, root, dir, 1_000_000_000_400L, 3_600_000L, 4, false, false);
        long first = ProcOverrides.bootTime(read(new File(dir, "stat")));
        // One minute later; integer rounding makes the new computation one second lower.
        ProcOverrides.refresh(ops, root, dir, 1_000_000_060_000L, 3_660_600L, 4, false, false);
        assertEquals(first, ProcOverrides.bootTime(read(new File(dir, "stat"))));
        assertEquals("3660.60 14642.40\n", read(new File(dir, "uptime")));
    }

    /** Readable real files: the stand-ins go away and nothing is bound. */
    @Test
    public void readableFilesNeedNoStandIns() throws Exception {
        File root = temporaryFolder.newFolder("runtime3");
        File dir = new File(root, "proc");
        JvmFileOps ops = new JvmFileOps();
        ProcOverrides.refresh(ops, root, dir, 1_000_000_000_000L, 1000L, 1, false, false);
        ProcOverrides.refresh(ops, root, dir, 1_000_000_000_000L, 1000L, 1, true, true);
        assertEquals(Collections.emptyList(), ProcOverrides.binds(ops, dir));
        assertEquals(Collections.emptyList(), ProcOverrides.binds(ops, new File(root, "missing")));
    }
}
