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


package com.thothterm.arch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thothterm.Settings;
import com.thothterm.linux.GardenPalette;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ThothTerm Rolling's own cool-blue colours, everywhere ThothTerm draws. */
public class ArchPaletteTest {
    /** The xterm-256 colours ThothTerm Ubuntu's banner and prompt use. */
    private static final Set<Integer> UBUNTU = new HashSet<>(Arrays.asList(214, 44, 252, 246, 39));
    /** ThothTerm Trixie's accents and muted grey (its foreground is the neutral 255). */
    private static final Set<Integer> TRIXIE = new HashSet<>(Arrays.asList(204, 217, 211, 247));

    static GardenPalette palette() throws Exception {
        return GardenPalette.load(new FileInputStream(
                new File(ArchEditionTest.moduleDir(), "src/main/assets/garden/palette.properties")));
    }

    private static Map<String, String> colors(File file) throws Exception {
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("<color name=\"(brand_[a-z_]+)\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        while (m.find()) map.put(m.group(1), m.group(2).toUpperCase(java.util.Locale.ROOT));
        return map;
    }

    @Test
    public void thePaletteIsNeitherUbuntusNorTrixies() throws Exception {
        GardenPalette a = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            assertFalse(role + " draws with a ThothTerm Ubuntu colour", UBUNTU.contains(a.xterm256(role)));
            assertFalse(role + " draws with a ThothTerm Trixie colour", TRIXIE.contains(a.xterm256(role)));
        }
        assertEquals(69, a.xterm256("primary"));
        assertEquals(111, a.xterm256("secondary"));
        assertEquals(68, a.xterm256("highlight"));
        assertEquals(255, a.xterm256("foreground"));
        assertEquals(103, a.xterm256("muted"));
        // Three accents, three different colours on the terminal.
        assertEquals(3, new HashSet<>(Arrays.asList(a.xterm256("primary"),
                a.xterm256("secondary"), a.xterm256("highlight"))).size());
    }

    /** Cool blue, and never Arch Linux's own brand blue (#1793D1, hue 200). */
    @Test
    public void theBlueIsCoolAndNotArchLinuxBlue() throws Exception {
        int rgb = palette().rgb("primary");
        double hue = hue(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
        assertEquals(200, hue(0x17, 0x93, 0xD1), 1);
        assertTrue("primary hue " + hue, hue > 200 + 15 && hue < 240);
        assertFalse(palette().hex("primary").equalsIgnoreCase("#1793D1"));
    }

    /** HSV hue in degrees. */
    private static double hue(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double d = max - min;
        if (d == 0) return 0;
        double h = max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
        return (h * 60 + 360) % 360;
    }

    /** Every text role stays readable on the terminal background, as drawn. */
    @Test
    public void textRolesAreReadable() throws Exception {
        GardenPalette a = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            int drawn = GardenPalette.xtermRgb(a.xterm256(role));
            double ratio = GardenPalette.contrast(drawn, a.rgb("background"));
            assertTrue(role + " contrast " + ratio, ratio >= 4.5);
        }
        // App chrome: muted text on the surface, and the accent on the background.
        assertTrue(GardenPalette.contrast(a.rgb("muted"), a.rgb("surface")) >= 4.5);
        assertTrue(GardenPalette.contrast(a.rgb("primary"), a.rgb("background")) >= 4.5);
    }

    /** The Android chrome overrides every neutral role garden-common leaves grey. */
    @Test
    public void everyChromeRoleIsTheEditions() throws Exception {
        File repo = ArchEditionTest.repo();
        Map<String, String> common = colors(new File(repo, "garden-common/src/main/res/values/colors.xml"));
        Map<String, String> own = colors(new File(ArchEditionTest.moduleDir(), "src/main/res/values/colors.xml"));
        Map<String, String> trixie = colors(new File(repo, "garden-debian/src/main/res/values/colors.xml"));
        assertTrue(common.size() >= 11);
        for (String name : common.keySet()) {
            assertTrue("ThothTerm Rolling must set " + name, own.containsKey(name));
            assertFalse(name + " is still garden-common's grey", own.get(name).equals(common.get(name)));
            assertFalse(name + " is ThothTerm Trixie's", own.get(name).equals(trixie.get(name)));
        }
        GardenPalette a = palette();
        assertEquals(a.hex("background").substring(1), own.get("brand_background"));
        assertEquals(a.hex("surface").substring(1), own.get("brand_surface"));
        assertEquals(a.hex("primary").substring(1), own.get("brand_accent"));
        assertEquals(a.hex("muted").substring(1), own.get("brand_on_surface_muted"));
    }

    @Test
    public void theTerminalsDefaultSchemeIsTheEditions() {
        assertEquals(Settings.color_schemes.length - 1, Settings.EDITION_SCHEME);
    }

    @Test
    public void theLanPageUsesTheEditionsColours() throws Exception {
        String css = palette().css();
        assertTrue(css.contains("--accent: #4F7FF4;"));
        assertTrue(css.contains("--bg: #020811;"));
        String lower = css.toLowerCase(java.util.Locale.ROOT);
        for (String other : new String[]{"#e95420", "#f44f7a", "#110208", "#1793d1"}) {
            assertFalse(other, lower.contains(other));
        }
    }

    /** The banner drawn with this palette: title, OS line, labels, values, mark. */
    @Test
    public void theBannerUsesTheEditionsRoles() throws Exception {
        Assume.assumeTrue(new File("/bin/bash").canExecute());
        File root = Files.createTempDirectory("arch-guest").toFile();
        File managed = new File(root, "etc/thothterm");
        assertTrue(managed.mkdirs());
        Files.write(new File(managed, "edition").toPath(), "ThothTerm Rolling\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(managed, "palette").toPath(), palette().guestFile().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "etc/os-release").toPath(),
                "NAME=\"Arch Linux ARM\"\nPRETTY_NAME=\"Arch Linux ARM\"\nID=archarm\n".getBytes(StandardCharsets.UTF_8));
        File script = new File(ArchEditionTest.repo(), "garden-common/src/main/assets/linux/thothfetch");
        ProcessBuilder pb = new ProcessBuilder("bash", script.getPath());
        pb.environment().put("THOTHTERM_GUEST_ROOT", root.getPath());
        pb.environment().put("COLUMNS", "80");
        pb.environment().remove("NO_COLOR");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor());
        String e = "\u001b[";
        assertTrue(out, out.contains(e + "38;5;69m    /\\"));
        assertTrue(out, out.contains(e + "38;5;111m    >_"));
        assertTrue(out, out.contains(e + "38;5;68mThothTerm Rolling" + e + "0m"));
        assertTrue(out, out.contains(e + "38;5;255mArch Linux ARM" + e + "0m"));
        assertTrue(out, out.contains(e + "38;5;103mUser          " + e + "0m" + e + "38;5;255mthoth"));
        for (int u : UBUNTU) assertFalse("Ubuntu colour " + u, out.contains(e + "38;5;" + u + "m"));
        for (int t : TRIXIE) assertFalse("Trixie colour " + t, out.contains(e + "38;5;" + t + "m"));
    }

    /**
     * tools/garden/branding/palette.py re-derives every value: the Garden
     * sheet's own values, turned by the artwork's hue rotation.
     */
    @Test
    public void thePaletteIsDerivedFromTheGardenSheet() throws Exception {
        File repo = ArchEditionTest.repo();
        String[] args = new String(Files.readAllBytes(new File(repo,
                "docs/branding/arch/source/palette.args").toPath()), StandardCharsets.UTF_8).trim().split("\\s+");
        List<String> cmd = new java.util.ArrayList<>(Arrays.asList("python3",
                new File(repo, "tools/garden/branding/palette.py").getPath(),
                new File(repo, "docs/branding/debian/source/thothterm-debian-branding-sheet.png").getPath()));
        cmd.addAll(Arrays.asList(args));
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assume.assumeNoException("python3 is not available", e);
            return;
        }
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        int code = p.waitFor();
        Assume.assumeFalse("Pillow is not available", out.contains("No module named 'PIL'"));
        assertEquals(out, 0, code);
        GardenPalette a = palette();
        for (String role : new String[]{"primary", "secondary", "highlight", "foreground", "muted", "background", "surface"}) {
            assertTrue(role + " differs from the derivation: " + out, out.contains(role + "=" + a.hex(role) + "\n"));
        }
        Map<String, String> own = colors(new File(ArchEditionTest.moduleDir(), "src/main/res/values/colors.xml"));
        assertTrue(out.contains("accent_dark=#" + own.get("brand_accent_dark") + "\n"));
        assertTrue(out.contains("surface_light=#" + own.get("brand_surface_light") + "\n"));
        assertTrue(out.contains("on_surface=#" + own.get("brand_on_surface") + "\n"));
        assertTrue(out.contains("deep=#" + own.get("brand_light_primary") + "\n"));
    }

    private static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
