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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Locks the {@code fastfetch-fit} helper's one decision: drop the logo only when
 * the terminal is too narrow for fastfetch's fixed layout.
 *
 * <p>Measured on the device: fastfetch emits the same bytes at every width,
 * placing its info block at a fixed column 47 with its widest line ending at
 * column 106, and turns autowrap off while drawing — so under 106 columns the
 * right-hand text is clipped rather than wrapped.
 */
public class FastfetchFitTest {
    private static final int REQUIRED_COLUMNS = 106;

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private static File script() {
        File direct = new File("src/main/assets/linux/fastfetch-fit");
        if (direct.isFile()) return direct;
        return new File("term-ubuntu/src/main/assets/linux/fastfetch-fit");
    }

    /**
     * Runs the helper with a stub on PATH in place of the real fastfetch, so the
     * test observes exactly which arguments it would have been given.
     */
    private String argumentsAtWidth(int columns) throws Exception {
        assumeTrue("a POSIX shell is required", new File("/bin/sh").canExecute());
        File bin = temp.newFolder();
        File record = new File(bin, "args.txt");
        File stub = new File(bin, "fastfetch");
        Files.write(stub.toPath(),
                ("#!/bin/sh\necho \"$@\" > " + record.getAbsolutePath() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertTrue(stub.setExecutable(true));

        // The helper calls /usr/bin/fastfetch by absolute path so that it can
        // never re-enter itself; point that at the stub for the test.
        String source = new String(Files.readAllBytes(script().toPath()),
                StandardCharsets.UTF_8);
        assertTrue("helper must call fastfetch by absolute path",
                source.contains("FASTFETCH=/usr/bin/fastfetch"));
        String patched = source.replace("FASTFETCH=/usr/bin/fastfetch",
                "FASTFETCH=" + stub.getAbsolutePath());
        File copy = new File(temp.newFolder(), "fastfetch-fit");
        Files.write(copy.toPath(), patched.getBytes(StandardCharsets.UTF_8));
        assertTrue(copy.setExecutable(true));

        ProcessBuilder builder = new ProcessBuilder("sh", copy.getAbsolutePath());
        // No tty here, so the helper falls back to COLUMNS.
        builder.environment().put("COLUMNS", Integer.toString(columns));
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) { /* drain */ }
        }
        assertEquals("helper must succeed", 0, process.waitFor());
        return new String(Files.readAllBytes(record.toPath()),
                StandardCharsets.UTF_8).trim();
    }

    @Test
    public void assetHasNoTabsOrCarriageReturns() throws Exception {
        String raw = new String(Files.readAllBytes(script().toPath()),
                StandardCharsets.UTF_8);
        assertFalse("helper must not use tabs", raw.contains("\t"));
        assertFalse("helper must not use CR", raw.contains("\r"));
    }

    @Test
    public void narrowTerminalsDropTheLogo() throws Exception {
        // 63 columns is this device's default portrait width.
        assertEquals("--logo none", argumentsAtWidth(63));
        assertEquals("--logo none", argumentsAtWidth(REQUIRED_COLUMNS - 1));
    }

    @Test
    public void wideTerminalsKeepUpstreamOutput() throws Exception {
        // Landscape is 123 columns, and portrait at the smallest font is 108.
        assertEquals("", argumentsAtWidth(REQUIRED_COLUMNS));
        assertEquals("", argumentsAtWidth(123));
        assertEquals("", argumentsAtWidth(200));
    }

    @Test
    public void neverShadowsOrRewritesUpstreamFastfetch() throws Exception {
        String raw = new String(Files.readAllBytes(script().toPath()),
                StandardCharsets.UTF_8);
        // The helper is a separate command: it must not be named fastfetch, and
        // must not install an alias or otherwise redirect the real one.
        assertEquals("fastfetch-fit", script().getName());
        assertFalse("must not define an alias", raw.contains("alias "));
        assertTrue("must invoke upstream fastfetch directly",
                raw.contains("FASTFETCH=/usr/bin/fastfetch"));
    }
}
