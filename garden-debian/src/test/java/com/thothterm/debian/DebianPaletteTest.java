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


package com.thothterm.debian;

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

/** ThothTerm Trixie's own colours, from its approved sheet, everywhere ThothTerm draws. */
public class DebianPaletteTest {
    /** The xterm-256 colours ThothTerm Ubuntu's banner and prompt use. */
    private static final Set<Integer> UBUNTU = new HashSet<>(Arrays.asList(214, 44, 252, 246, 39));

    static GardenPalette palette() throws Exception {
        return GardenPalette.load(new FileInputStream(
                new File(DebianEditionTest.moduleDir(), "src/main/assets/garden/palette.properties")));
    }

    private static Map<String, String> colors(File file) throws Exception {
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("<color name=\"(brand_[a-z_]+)\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        while (m.find()) map.put(m.group(1), m.group(2).toUpperCase(java.util.Locale.ROOT));
        return map;
    }

    @Test
    public void theDebianPaletteIsNotUbuntus() throws Exception {
        GardenPalette d = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            assertFalse(role + " draws with a ThothTerm Ubuntu colour", UBUNTU.contains(d.xterm256(role)));
        }
        assertEquals(204, d.xterm256("primary"));
        assertEquals(217, d.xterm256("secondary"));
        assertEquals(211, d.xterm256("highlight"));
        assertEquals(255, d.xterm256("foreground"));
        assertEquals(247, d.xterm256("muted"));
    }

    /** Every text role stays readable on the Debian terminal background, as drawn. */
    @Test
    public void textRolesAreReadable() throws Exception {
        GardenPalette d = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            int drawn = GardenPalette.xtermRgb(d.xterm256(role));
            double ratio = GardenPalette.contrast(drawn, d.rgb("background"));
            assertTrue(role + " contrast " + ratio, ratio >= 4.5);
        }
    }

    /** The Android chrome overrides every neutral role garden-common leaves grey. */
    @Test
    public void everyChromeRoleIsDebians() throws Exception {
        File repo = DebianEditionTest.moduleDir().getParentFile();
        Map<String, String> common = colors(new File(repo, "garden-common/src/main/res/values/colors.xml"));
        Map<String, String> own = colors(new File(DebianEditionTest.moduleDir(), "src/main/res/values/colors.xml"));
        assertTrue(common.size() >= 11);
        for (String name : common.keySet()) {
            assertTrue("ThothTerm Trixie must set " + name, own.containsKey(name));
            assertFalse(name + " is still garden-common's grey", own.get(name).equals(common.get(name)));
        }
        GardenPalette d = palette();
        assertEquals(d.hex("background").substring(1), own.get("brand_background"));
        assertEquals(d.hex("surface").substring(1), own.get("brand_surface"));
        assertEquals(d.hex("primary").substring(1), own.get("brand_accent"));
        assertEquals(d.hex("muted").substring(1), own.get("brand_on_surface_muted"));
    }

    @Test
    public void theTerminalsDefaultSchemeIsTheEditions() {
        assertEquals(Settings.color_schemes.length - 1, Settings.EDITION_SCHEME);
    }

    @Test
    public void theLanPageUsesDebianColours() throws Exception {
        String css = palette().css();
        assertTrue(css.contains("--accent: #F44F7A;"));
        assertTrue(css.contains("--bg: #110208;"));
        assertFalse(css.toLowerCase(java.util.Locale.ROOT).contains("#e95420"));
    }

    /** The banner drawn with Debian's palette: title, OS line, labels, values, mark. */
    @Test
    public void theBannerUsesDebianRoles() throws Exception {
        Assume.assumeTrue(new File("/bin/bash").canExecute());
        File root = Files.createTempDirectory("debian-guest").toFile();
        File managed = new File(root, "etc/thothterm");
        assertTrue(managed.mkdirs());
        Files.write(new File(managed, "edition").toPath(), "ThothTerm Trixie\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(managed, "palette").toPath(), palette().guestFile().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "etc/os-release").toPath(),
                "PRETTY_NAME=\"Debian GNU/Linux 13 (trixie)\"\n".getBytes(StandardCharsets.UTF_8));
        File script = new File(DebianEditionTest.moduleDir().getParentFile(), "garden-common/src/main/assets/linux/thothfetch");
        ProcessBuilder pb = new ProcessBuilder("bash", script.getPath());
        pb.environment().put("THOTHTERM_GUEST_ROOT", root.getPath());
        pb.environment().put("COLUMNS", "80");
        pb.environment().remove("NO_COLOR");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor());
        String e = "\u001b[";
        assertTrue(out.contains(e + "38;5;204m    /\\"));
        assertTrue(out.contains(e + "38;5;217m    >_"));
        assertTrue(out.contains(e + "38;5;211mThothTerm Trixie" + e + "0m"));
        assertTrue(out.contains(e + "38;5;255mDebian GNU/Linux 13 (trixie)" + e + "0m"));
        assertTrue(out.contains(e + "38;5;247mUser          " + e + "0m" + e + "38;5;255mthoth"));
        for (int u : UBUNTU) assertFalse("Ubuntu colour " + u, out.contains(e + "38;5;" + u + "m"));
    }

    /** tools/garden/branding/palette.py re-derives every value from the approved sheet. */
    @Test
    public void thePaletteIsSampledFromTheSheet() throws Exception {
        File repo = DebianEditionTest.moduleDir().getParentFile();
        List<String> cmd = Arrays.asList("python3",
                new File(repo, "tools/garden/branding/palette.py").getPath(),
                new File(repo, "docs/branding/debian/source/thothterm-debian-branding-sheet.png").getPath());
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
        GardenPalette d = palette();
        for (String role : new String[]{"primary", "secondary", "highlight", "foreground", "muted", "background", "surface"}) {
            assertTrue(role + " differs from the sheet: " + out, out.contains(role + "=" + d.hex(role) + "\n"));
        }
        Map<String, String> own = colors(new File(DebianEditionTest.moduleDir(), "src/main/res/values/colors.xml"));
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
