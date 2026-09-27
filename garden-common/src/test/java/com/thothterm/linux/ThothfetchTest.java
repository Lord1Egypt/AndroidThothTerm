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

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Locks the printable geometry of the {@code thothfetch} welcome banner: the
 * wide info column, the mirror-symmetric ASCII mark, and the width-driven
 * narrow fallback. ANSI color sequences must never contribute to width.
 */
public class ThothfetchTest {
    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;]*m");
    private static final int WIDE_INFO_COLUMN = 14;
    private static final int MARK_WIDTH = 10;
    private static final int NARROW_THRESHOLD = 44;

    private static final String EDITION = "ThothTerm Garden Test";
    private static final String PRETTY_NAME = "Garden GNU/Linux 1 (test)";

    private static final String[] WIDE_INFO = {
            EDITION,
            PRETTY_NAME,
            "Architecture  ",
            "Shell         Bash",
            "Home          /home/thoth",
            "User          thoth",
    };

    private static File script() {
        File direct = new File("src/main/assets/linux/thothfetch");
        if (direct.isFile()) return direct;
        return new File("garden-common/src/main/assets/linux/thothfetch");
    }

    /** A guest /etc with the two files thothfetch reads. */
    private static File fakeRoot(String edition, String osRelease) throws Exception {
        File root = Files.createTempDirectory("thothfetch").toFile();
        File etc = new File(root, "etc/thothterm");
        assertTrue(etc.mkdirs());
        Files.write(new File(etc, "edition").toPath(),
                (edition + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "etc/os-release").toPath(),
                osRelease.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(etc, "palette").toPath(), ("primary=38;5;204\nsecondary=38;5;217\n"
                + "highlight=38;5;211\nforeground=38;5;255\nmuted=38;5;247\n").getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static List<String> render(int columns) throws Exception {
        return render(columns, fakeRoot(EDITION,
                "NAME=Garden\nPRETTY_NAME=\"" + PRETTY_NAME + "\"\nID=garden\n"), true);
    }

    private static List<String> render(int columns, File root, boolean stripAnsi)
            throws Exception {
        assumeTrue("bash is required to exercise the banner",
                new File("/bin/bash").canExecute());
        File script = script();
        assertTrue("thothfetch asset is missing: " + script.getAbsolutePath(),
                script.isFile());

        ProcessBuilder builder = new ProcessBuilder("bash", script.getAbsolutePath());
        builder.environment().put("COLUMNS", Integer.toString(columns));
        builder.environment().put("THOTHTERM_GUEST_ROOT", root.getAbsolutePath());
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        builder.redirectErrorStream(true);
        Process process = builder.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(stripAnsi ? ANSI.matcher(line).replaceAll("") : line);
            }
        }
        assertEquals("thothfetch exit code", 0, process.waitFor());
        return lines;
    }

    private static String markOf(String line) {
        String field = line.length() <= WIDE_INFO_COLUMN
                ? line : line.substring(0, WIDE_INFO_COLUMN);
        return field.stripTrailing();
    }

    private static boolean mirrorSymmetric(String mark) {
        StringBuilder padded = new StringBuilder(mark);
        while (padded.length() < MARK_WIDTH) padded.append(' ');
        for (int i = 0; i < MARK_WIDTH / 2; i++) {
            char left = padded.charAt(i);
            char right = padded.charAt(MARK_WIDTH - 1 - i);
            if (left == ' ' && right == ' ') continue;
            char mirrored = left == '/' ? '\\' : (left == '\\' ? '/' : left);
            if (mirrored != right) return false;
        }
        return true;
    }

    @Test
    public void assetHasNoTabsOrCarriageReturns() throws Exception {
        String raw = new String(Files.readAllBytes(script().toPath()),
                StandardCharsets.UTF_8);
        assertFalse("thothfetch must not use tabs", raw.contains("\t"));
        assertFalse("thothfetch must not use CR", raw.contains("\r"));
    }

    @Test
    public void wideInfoStartsAtAFixedColumn() throws Exception {
        List<String> lines = render(80);
        int seen = 0;
        for (String line : lines) {
            for (String info : WIDE_INFO) {
                int at = line.indexOf(info);
                if (at >= 0) {
                    assertEquals("info column for '" + info + "'", WIDE_INFO_COLUMN, at);
                    seen++;
                    break;
                }
            }
        }
        assertEquals("every wide info row must render", WIDE_INFO.length, seen);
    }

    @Test
    public void wideValuesStartAtAFixedColumn() throws Exception {
        List<String> lines = render(80);
        String[] labels = {"Architecture", "Shell", "Home", "User"};
        int seen = 0;
        for (String line : lines) {
            if (line.length() <= WIDE_INFO_COLUMN) continue;
            String info = line.substring(WIDE_INFO_COLUMN);
            for (String label : labels) {
                if (info.startsWith(label)) {
                    int value = WIDE_INFO_COLUMN * 2;
                    assertTrue("value column for " + label, line.length() > value
                            && line.charAt(value - 1) == ' ' && line.charAt(value) != ' ');
                    seen++;
                    break;
                }
            }
        }
        assertEquals("every label/value row must render", labels.length, seen);
    }

    @Test
    public void markIsMirrorSymmetric() throws Exception {
        List<String> lines = render(80);
        int checked = 0;
        for (String line : lines) {
            String mark = markOf(line);
            String trimmed = mark.strip();
            if (trimmed.equals(">_")) continue;
            if (trimmed.isEmpty()) break;
            assertTrue("mark row is not symmetric: '" + mark + "'",
                    mirrorSymmetric(mark));
            checked++;
        }
        assertEquals("expected six flower rows", 6, checked);
    }

    @Test
    public void wideLayoutFitsAndDoesNotNeedTrailingWhitespace() throws Exception {
        List<String> lines = render(80);
        int max = 0;
        for (String line : lines) {
            assertEquals("no trailing whitespace is required: " + line, line.stripTrailing(), line);
            max = Math.max(max, line.length());
        }
        assertTrue("wide layout must fit its own threshold (" + max + ")",
                max <= NARROW_THRESHOLD);
    }

    @Test
    public void narrowLayoutUsedBelowThresholdAndFits() throws Exception {
        List<String> lines = render(30);
        for (String line : lines) {
            assertTrue("narrow line overflows 30 columns: " + line, line.length() <= 30);
        }
        assertFalse("narrow layout must drop the wide-only details",
                lines.stream().anyMatch(l -> l.contains("Architecture")));
        assertTrue(lines.contains(EDITION));
        assertTrue(lines.contains(PRETTY_NAME));
        assertTrue(lines.contains("User  thoth"));
        assertTrue(lines.contains("Home  /home/thoth"));
    }

    @Test
    public void colourSequencesDoNotChangeWidth() throws Exception {
        List<String> raw = render(80, fakeRoot(EDITION,
                "PRETTY_NAME=\"" + PRETTY_NAME + "\"\n"), false);
        assertTrue("expected ANSI colour in the banner",
                raw.stream().anyMatch(l -> l.contains("\u001b[")));
        assertEquals(render(80), raw.stream().map(l -> ANSI.matcher(l).replaceAll(""))
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void guestFilesAreDataNeverCodeOrTerminalControls() throws Exception {
        File marker = new File(Files.createTempDirectory("thothfetch-marker").toFile(), "ran");
        String hostile = "PRETTY_NAME=\"x\u001b]0;pwned\u0007 $(touch " + marker + ") `touch "
                + marker + "`\"\n";
        List<String> raw = render(80, fakeRoot("Ed\u001b[2Jition", hostile), false);
        assertFalse("os-release must not be executed", marker.exists());
        for (String line : raw) {
            String visible = ANSI.matcher(line).replaceAll("");
            assertFalse("control characters must be stripped: " + line,
                    visible.chars().anyMatch(c -> c < 0x20 || c == 0x7f));
            assertTrue("wide layout must stay within its threshold: " + visible,
                    visible.length() <= NARROW_THRESHOLD);
        }
        assertTrue(raw.stream().anyMatch(l -> l.contains("Ed[2Jition")));
    }

    @Test
    public void missingGuestFilesFallBack() throws Exception {
        File empty = Files.createTempDirectory("thothfetch-empty").toFile();
        List<String> lines = render(30, empty, true);
        assertTrue(lines.contains("ThothTerm"));
        assertTrue(lines.contains("Linux"));
    }
}
