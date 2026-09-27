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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The welcome banner and managed prompt take every colour from the edition
 * palette. With Ubuntu's palette they look exactly like term-ubuntu's own
 * scripts; with any other palette only the colours change; and nothing they
 * colour leaks into what the user types or runs next.
 */
public class GardenBrandingScriptsTest {
    private static final String ESC = "\u001b";

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }

    private static File gardenAsset(String name) {
        return new File(repo(), "garden-common/src/main/assets/linux/" + name);
    }

    private static File ubuntuAsset(String name) {
        return new File(repo(), "term-ubuntu/src/main/assets/linux/" + name);
    }

    static File guestRoot(String edition, String prettyName, String palette) throws Exception {
        File root = Files.createTempDirectory("garden-guest").toFile();
        File managed = new File(root, "etc/thothterm");
        assertTrue(managed.mkdirs());
        Files.write(new File(managed, "edition").toPath(), (edition + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "etc/os-release").toPath(),
                ("PRETTY_NAME=\"" + prettyName + "\"\n").getBytes(StandardCharsets.UTF_8));
        if (palette != null) {
            Files.write(new File(managed, "palette").toPath(), palette.getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    static String bash(String script, File root, Map<String, String> extraEnv) throws Exception {
        assumeTrue("bash is required", new File("/bin/bash").canExecute());
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
        pb.environment().remove("NO_COLOR");
        pb.environment().put("COLUMNS", "80");
        if (root != null) pb.environment().put("THOTHTERM_GUEST_ROOT", root.getAbsolutePath());
        if (extraEnv != null) pb.environment().putAll(extraEnv);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        assertEquals(0, p.waitFor());
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Text as (character, SGR foreground in force) cells; what a user sees. */
    static List<String> cells(String ansi) {
        List<String> cells = new ArrayList<>();
        String fg = "default";
        for (int i = 0; i < ansi.length(); i++) {
            char c = ansi.charAt(i);
            if (c == '\u001b' && i + 1 < ansi.length() && ansi.charAt(i + 1) == '[') {
                int end = ansi.indexOf('m', i);
                String params = ansi.substring(i + 2, end);
                fg = (params.isEmpty() || params.equals("0")) ? "default" : params;
                i = end;
                continue;
            }
            cells.add(c + "|" + (c == ' ' || c == '\n' ? "-" : fg));
        }
        return cells;
    }

    private static String hostArch() throws Exception {
        return bash("printf %s \"$HOSTTYPE\"", null, null);
    }

    @Test
    public void ubuntusBannerIsReproducedExactly() throws Exception {
        File root = guestRoot("ThothTerm Ubuntu", "Ubuntu 26.04.1 LTS", GardenPaletteTest.ubuntu().guestFile());
        for (String columns : new String[]{"80", "30"}) {
            Map<String, String> env = java.util.Collections.singletonMap("COLUMNS", columns);
            String theirs = bash("bash " + ubuntuAsset("thothfetch"), null, env);
            String ours = bash("bash " + gardenAsset("thothfetch"), root, env)
                    .replace("Architecture  " + ESC + "[0m" + ESC + "[38;5;246m" + hostArch(),
                            "Architecture  " + ESC + "[0m" + ESC + "[38;5;246maarch64");
            assertEquals("banner at " + columns + " columns", cells(theirs), cells(ours));
        }
    }

    @Test
    public void ubuntusPromptIsReproducedExactly() throws Exception {
        File root = guestRoot("ThothTerm Ubuntu", "Ubuntu 26.04.1 LTS", GardenPaletteTest.ubuntu().guestFile());
        String show = "; cd /; printf '%s' \"${PS1@P}\"";
        String theirs = bash(". " + ubuntuAsset("thothterm-ubuntu.sh") + show, null, null);
        String ours = bash(". " + gardenAsset("thothterm-garden.sh") + show, root, null);
        assertEquals(theirs, ours);
        assertTrue(theirs.contains(ESC + "[38;5;39mthoth"));
    }

    /** A palette with a distinct colour per role: every element uses its role. */
    @Test
    public void everyElementUsesItsRole() throws Exception {
        String palette = "primary=38;5;201\nsecondary=38;5;202\nhighlight=38;5;203\n"
                + "foreground=38;5;204\nmuted=38;5;205\n"
                + "promptUser=38;5;206\npromptHost=38;5;207\npromptPath=38;5;208\n";
        File root = guestRoot("ThothTerm Test", "Test GNU/Linux 1 (one)", palette);
        String banner = bash("bash " + gardenAsset("thothfetch"), root, null);
        assertTrue(banner.contains(ESC + "[38;5;201m    /\\"));
        assertTrue(banner.contains(ESC + "[38;5;202m/  \\  /  \\"));
        assertTrue(banner.contains(ESC + "[38;5;202m    >_"));
        assertTrue(banner.contains(ESC + "[38;5;203mThothTerm Test" + ESC + "[0m"));
        assertTrue(banner.contains(ESC + "[38;5;204mTest GNU/Linux 1 (one)" + ESC + "[0m"));
        assertTrue(banner.contains(ESC + "[38;5;205mShell         " + ESC + "[0m" + ESC + "[38;5;204mBash" + ESC + "[0m"));
        String prompt = bash(". " + gardenAsset("thothterm-garden.sh") + "; cd /; printf '%s' \"${PS1@P}\"", root, null);
        assertEquals(ESC + "[38;5;206mthoth" + ESC + "[0m@" + ESC + "[38;5;207mthothterm" + ESC + "[0m:"
                + ESC + "[38;5;208m/" + ESC + "[0m$ ", prompt);
    }

    /** What the user types or runs after the banner and prompt starts in the default colour. */
    @Test
    public void coloursDoNotLeakIntoUserOutput() throws Exception {
        File root = guestRoot("ThothTerm Test", "Test", GardenPaletteTest.ubuntu().guestFile());
        String out = bash("bash " + gardenAsset("thothfetch") + "; printf USER-OUTPUT", root, null);
        List<String> cells = cells(out);
        String tail = String.join("", cells.subList(cells.size() - 11, cells.size()));
        assertEquals("U|defaultS|defaultE|defaultR|default-|defaultO|defaultU|defaultT|defaultP|defaultU|defaultT|default",
                tail.replace("-|default", "-|default"));
        for (String line : out.split("\n")) {
            if (line.contains(ESC + "[38;")) assertTrue("unterminated colour: " + line, line.endsWith(ESC + "[0m"));
        }
        String prompt = bash(". " + gardenAsset("thothterm-garden.sh") + "; printf '%s' \"${PS1@P}\"", root, null);
        assertTrue("typed text must start after a reset", prompt.endsWith(ESC + "[0m$ "));
    }

    @Test
    public void noColorOrNoPaletteGivesPlainText() throws Exception {
        File withPalette = guestRoot("ThothTerm Test", "Test", GardenPaletteTest.ubuntu().guestFile());
        Map<String, String> noColor = java.util.Collections.singletonMap("NO_COLOR", "1");
        File noPalette = guestRoot("ThothTerm Test", "Test", null);
        for (String out : new String[]{
                bash("bash " + gardenAsset("thothfetch"), withPalette, noColor),
                bash("bash " + gardenAsset("thothfetch"), noPalette, null),
                bash(". " + gardenAsset("thothterm-garden.sh") + "; printf '%s' \"${PS1@P}\"", withPalette, noColor),
                bash(". " + gardenAsset("thothterm-garden.sh") + "; printf '%s' \"${PS1@P}\"", noPalette, null)}) {
            assertFalse("escape in plain output: " + out, out.contains(ESC));
        }
        String plain = bash(". " + gardenAsset("thothterm-garden.sh") + "; cd /; printf '%s' \"${PS1@P}\"", noPalette, null);
        assertEquals("thoth@thothterm:/$ ", plain);
    }

    /** Palette lines that are not plain SGR numbers are ignored, never executed or printed. */
    @Test
    public void hostilePaletteLinesAreIgnored() throws Exception {
        File marker = new File(Files.createTempDirectory("palette-marker").toFile(), "ran");
        String palette = "primary=38;5;201$(touch " + marker + ")\nsecondary=38;5;2`touch " + marker + "`\n"
                + "highlight=38;5;203\u001b]0;x\u0007\npromptUser=1;31m\\]$(touch " + marker + ")\n";
        File root = guestRoot("ThothTerm Test", "Test", palette);
        String banner = bash("bash " + gardenAsset("thothfetch"), root, null);
        String prompt = bash(". " + gardenAsset("thothterm-garden.sh") + "; printf '%s' \"${PS1@P}\"", root, null);
        assertFalse("palette content was executed", marker.exists());
        assertFalse(banner.contains("201") || banner.contains("]0;"));
        assertFalse(prompt.contains(ESC));
    }
}
