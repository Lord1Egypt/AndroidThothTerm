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


package com.thothterm.blackarch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.thothterm.Settings;
import com.thothterm.linux.GardenPalette;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ThothTerm BlackArch's own metallic colours, everywhere ThothTerm draws: bright
 * silver, gunmetal and graphite on near-black, nothing blue like Rolling.
 * docs/branding/blackarch/PALETTE.md.
 */
public class BlackArchPaletteTest {
    /** xterm-256 colours the other editions draw their banner and prompt with. */
    private static final Set<Integer> OTHERS = new HashSet<>(Arrays.asList(
            214, 44, 252, 246, 39,          // Ubuntu
            204, 217, 211, 247,             // Trixie
            69, 111, 68, 255, 103));        // Rolling
    private static final String[] ROLES = {"primary", "secondary", "highlight", "foreground", "muted",
            "promptUser", "promptHost", "promptPath", "background", "surface"};

    static File module() {
        return BlackArchEditionTest.moduleDir();
    }

    static GardenPalette palette() throws Exception {
        return GardenPalette.load(new FileInputStream(new File(module(), "src/main/assets/garden/palette.properties")));
    }

    private static GardenPalette rolling() throws Exception {
        return GardenPalette.load(new FileInputStream(
                new File(BlackArchEditionTest.repo(), "garden-arch/src/main/assets/garden/palette.properties")));
    }

    private static Map<String, String> colors(File file) throws Exception {
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("<color name=\"(brand_[a-z_]+)\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        while (m.find()) map.put(m.group(1), m.group(2).toUpperCase(java.util.Locale.ROOT));
        return map;
    }

    /** The edition asset has every role exactly once, each a well-formed colour. */
    @Test
    public void everyRoleIsPresentOnceAndWellFormed() throws Exception {
        String text = new String(Files.readAllBytes(new File(module(), "src/main/assets/garden/palette.properties").toPath()),
                StandardCharsets.UTF_8);
        for (String role : ROLES) {
            Matcher m = Pattern.compile("(?m)^" + role + "=(.*)$").matcher(text);
            assertTrue("missing " + role, m.find());
            assertTrue(role + " malformed: " + m.group(1), m.group(1).matches("#[0-9A-Fa-f]{6}"));
            assertFalse("duplicate " + role, m.find());
        }
        palette(); // GardenPalette.load fails closed on anything else
    }

    @Test
    public void thePaletteIsNoOtherEditions() throws Exception {
        GardenPalette a = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            assertFalse(role + " draws with another edition's colour", OTHERS.contains(a.xterm256(role)));
        }
        assertEquals(254, a.xterm256("primary"));
        assertEquals(245, a.xterm256("secondary"));
        assertEquals(251, a.xterm256("highlight"));
        assertEquals(231, a.xterm256("foreground"));
        assertEquals(243, a.xterm256("muted"));
        // The five distinct text roles are five different colours on the terminal.
        assertEquals(5, new HashSet<>(Arrays.asList(a.xterm256("primary"), a.xterm256("secondary"),
                a.xterm256("highlight"), a.xterm256("foreground"), a.xterm256("muted"))).size());
        // The prompt's three roles are the coordinated silver / gunmetal / near-white.
        assertEquals(a.xterm256("primary"), a.xterm256("promptUser"));
        assertEquals(a.xterm256("secondary"), a.xterm256("promptHost"));
        assertEquals(a.xterm256("foreground"), a.xterm256("promptPath"));
        assertEquals(3, new HashSet<>(Arrays.asList(a.xterm256("promptUser"), a.xterm256("promptHost"),
                a.xterm256("promptPath"))).size());
    }

    /** Metal, not blue: every text role is a (near-)neutral, and what the terminal draws is exactly neutral. */
    @Test
    public void theTextRolesAreMetallicNotBlue() throws Exception {
        GardenPalette a = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            int rgb = a.rgb(role);
            int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
            int spread = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            assertTrue(role + " tint " + spread, spread <= 0x20);
            int drawn = GardenPalette.xtermRgb(a.xterm256(role));
            assertEquals(role + " is drawn neutral", drawn >> 16 & 0xFF, drawn & 0xFF);
            assertEquals(role + " is drawn neutral", drawn >> 8 & 0xFF, drawn & 0xFF);
        }
        // The ramp runs light to dark: foreground > primary > highlight > secondary > muted.
        int[] ramp = {a.xterm256("foreground"), a.xterm256("primary"), a.xterm256("highlight"),
                a.xterm256("secondary"), a.xterm256("muted")};
        for (int i = 1; i < ramp.length; i++) {
            assertTrue("ramp " + i, GardenPalette.xtermRgb(ramp[i - 1]) > GardenPalette.xtermRgb(ramp[i]));
        }
    }

    /** Materially different from Rolling's blue, role by role, and nowhere near Arch Linux's brand blue. */
    @Test
    public void itDiffersMateriallyFromRolling() throws Exception {
        GardenPalette a = palette(), r = rolling();
        for (String role : ROLES) {
            assertNotEquals(role, r.hex(role), a.hex(role));
        }
        // The foreground is near-white in every edition by design; it must differ, and draw
        // with a different terminal colour (231 vs Rolling's 255), but is not held to a distance.
        assertNotEquals(r.xterm256("foreground"), a.xterm256("foreground"));
        for (String role : new String[]{"primary", "secondary", "highlight", "muted"}) {
            int x = a.rgb(role), y = r.rgb(role);
            double d = Math.sqrt(Math.pow((x >> 16 & 0xFF) - (y >> 16 & 0xFF), 2) + Math.pow((x >> 8 & 0xFF) - (y >> 8 & 0xFF), 2)
                    + Math.pow((x & 0xFF) - (y & 0xFF), 2));
            assertTrue(role + " is too close to Rolling's (RGB distance " + d + ")", d > 40);
        }
        assertFalse(a.hex("primary").equalsIgnoreCase("#1793D1"));
    }

    @Test
    public void textRolesAreReadable() throws Exception {
        GardenPalette a = palette();
        for (String role : GardenPalette.TEXT_ROLES) {
            int drawn = GardenPalette.xtermRgb(a.xterm256(role));
            double ratio = GardenPalette.contrast(drawn, a.rgb("background"));
            assertTrue(role + " contrast " + ratio, ratio >= 4.5);
        }
        assertTrue(GardenPalette.contrast(a.rgb("muted"), a.rgb("surface")) >= 4.5);
        assertTrue(GardenPalette.contrast(a.rgb("primary"), a.rgb("background")) >= 4.5);
        // Foreground on background: the terminal's own default text.
        assertTrue(GardenPalette.contrast(a.rgb("foreground"), a.rgb("background")) >= 15);
    }

    /** The Android chrome overrides every neutral role garden-common leaves grey, and agrees with the palette. */
    @Test
    public void everyChromeRoleIsTheEditions() throws Exception {
        File repo = BlackArchEditionTest.repo();
        Map<String, String> common = colors(new File(repo, "garden-common/src/main/res/values/colors.xml"));
        Map<String, String> own = colors(new File(module(), "src/main/res/values/colors.xml"));
        Map<String, String> trixie = colors(new File(repo, "garden-debian/src/main/res/values/colors.xml"));
        Map<String, String> rolling = colors(new File(repo, "garden-arch/src/main/res/values/colors.xml"));
        assertTrue(common.size() >= 11);
        for (String name : common.keySet()) {
            assertTrue("ThothTerm BlackArch must set " + name, own.containsKey(name));
            assertFalse(name + " is still garden-common's grey", own.get(name).equals(common.get(name)));
            assertFalse(name + " is ThothTerm Trixie's", own.get(name).equals(trixie.get(name)));
            assertFalse(name + " is ThothTerm Rolling's", own.get(name).equals(rolling.get(name)));
        }
        GardenPalette a = palette();
        assertEquals(a.hex("background").substring(1), own.get("brand_background"));
        assertEquals(a.hex("surface").substring(1), own.get("brand_surface"));
        assertEquals(a.hex("primary").substring(1), own.get("brand_accent"));
        assertEquals(a.hex("muted").substring(1), own.get("brand_on_surface_muted"));
        assertEquals("on-surface text is the foreground", a.hex("foreground").substring(1), own.get("brand_on_surface"));
        // The light theme's accent is drawn on near-white: 3:1 for a control.
        assertTrue(GardenPalette.contrast(Integer.parseInt(own.get("brand_accent_dark"), 16),
                Integer.parseInt(own.get("brand_light_background"), 16)) >= 3);
    }

    @Test
    public void theTerminalsDefaultSchemeIsTheEditions() {
        assertEquals(Settings.color_schemes.length - 1, Settings.EDITION_SCHEME);
    }

    @Test
    public void theLanPageUsesTheEditionsColours() throws Exception {
        String css = palette().css();
        assertTrue(css, css.contains("--accent: #DFE4E8;"));
        assertTrue(css, css.contains("--bg: #020406;"));
        String lower = css.toLowerCase(java.util.Locale.ROOT);
        for (String other : new String[]{"#4f7ff4", "#020811", "#e95420", "#f44f7a", "#110208", "#1793d1"}) {
            assertFalse(other, lower.contains(other));
        }
    }

    // ---- the shared path, exercised: nothing BlackArch-specific writes the palette ----

    /** thothfetch with this palette: mark, edition, distro, labels, values. The shared ASCII is unchanged. */
    @Test
    public void theBannerUsesTheEditionsRoles() throws Exception {
        Assume.assumeTrue(new File("/bin/bash").canExecute());
        File root = guest();
        File script = new File(BlackArchEditionTest.repo(), "garden-common/src/main/assets/linux/thothfetch");
        String out = run(root, script, "bash", script.getPath());
        String e = "\u001b[";
        assertTrue(out, out.contains(e + "38;5;254m    /\\"));                       // mark petals: primary
        assertTrue(out, out.contains(e + "38;5;245m    >_"));                        // mark centre: secondary
        assertTrue(out, out.contains(e + "38;5;251mThothTerm BlackArch" + e + "0m")); // edition: highlight
        assertTrue(out, out.contains(e + "38;5;231mArch Linux ARM" + e + "0m"));      // distro line: foreground
        assertTrue(out, out.contains(e + "38;5;243mUser          " + e + "0m" + e + "38;5;231mthoth")); // label muted, value foreground
        for (int other : OTHERS) assertFalse("another edition's colour " + other, out.contains(e + "38;5;" + other + "m"));
        // NO_COLOR still switches it all off.
        String plain = run(root, script, "env", "NO_COLOR=1", "bash", script.getPath());
        assertFalse(plain, plain.contains(e));
        assertTrue(plain, plain.contains("ThothTerm BlackArch"));
    }

    /** The managed prompt: thoth@thothterm:DIR$ with user, host and path in separate roles. */
    @Test
    public void theManagedPromptUsesTheEditionsRoles() throws Exception {
        Assume.assumeTrue(new File("/bin/bash").canExecute());
        File root = guest();
        File script = new File(BlackArchEditionTest.repo(), "garden-common/src/main/assets/linux/thothterm-garden.sh");
        String ps1 = run(root, script, "bash", "-c", ". '" + script.getPath() + "'; printf %s \"$PS1\"");
        String user = "\\[\\e[38;5;254m\\]thoth", host = "\\[\\e[38;5;245m\\]thothterm", path = "\\[\\e[38;5;231m\\]\\w";
        assertTrue(ps1, ps1.contains(user + "\\[\\e[0m\\]@" + host + "\\[\\e[0m\\]:" + path));
        assertTrue(ps1, ps1.endsWith("\\[\\e[0m\\]\\$ "));
        String plain = run(root, script, "env", "NO_COLOR=1", "bash", "-c", ". '" + script.getPath() + "'; printf %s \"$PS1\"");
        assertEquals("thoth@thothterm:\\w\\$ ", plain);
    }

    private static File guest() throws Exception {
        File root = Files.createTempDirectory("blackarch-guest").toFile();
        File managed = new File(root, "etc/thothterm");
        assertTrue(managed.mkdirs());
        Files.write(new File(managed, "edition").toPath(), "ThothTerm BlackArch\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(managed, "palette").toPath(), palette().guestFile().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "etc/os-release").toPath(),
                "NAME=\"Arch Linux ARM\"\nPRETTY_NAME=\"Arch Linux ARM\"\nID=archarm\n".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static String run(File root, File script, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("THOTHTERM_GUEST_ROOT", root.getPath());
        pb.environment().put("COLUMNS", "80");
        if (!Arrays.asList(cmd).contains("NO_COLOR=1")) pb.environment().remove("NO_COLOR");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        assertEquals(out, 0, p.waitFor());
        return out;
    }

    /** The palette reaches the guest through the shared managed-config path, and BlackArch adds no path of its own. */
    @Test
    public void theEditionPaletteReachesTheManagedGuestConfigThroughTheSharedPath() throws Exception {
        File repo = BlackArchEditionTest.repo();
        String manager = new String(Files.readAllBytes(new File(repo,
                "garden-common/src/main/java/com/thothterm/linux/RootfsManager.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(manager.contains("GardenPalette.load(appContext.getAssets().open(GardenPalette.ASSET))"));
        assertTrue(manager.contains("new File(managedDir, \"palette\")"));
        assertTrue(manager.contains("managed(\"palette\", () -> writeGuest(root, paletteFile, palette.guestFile()));"));
        assertEquals("garden/palette.properties", GardenPalette.ASSET);
        // The asset the shared loader opens is this edition's; the guest file is its text roles.
        String guest = palette().guestFile();
        for (String role : GardenPalette.TEXT_ROLES) assertTrue(role, guest.contains("\n" + role + "=38;5;"));
        assertTrue(guest, guest.contains("promptUser=38;5;254\npromptHost=38;5;245\npromptPath=38;5;231\n"));
        // No edition-specific code: the module has no Java source at all.
        assertFalse(new File(module(), "src/main/java").exists());
    }

    /** No BlackArch colour is hard-coded in shared scripts or garden-common code, and none leaks into other editions. */
    @Test
    public void noBrandingLeaksAnywhere() throws Exception {
        File repo = BlackArchEditionTest.repo();
        List<File> shared = new ArrayList<>();
        shared.add(new File(repo, "garden-common/src/main/assets/linux/thothfetch"));
        shared.add(new File(repo, "garden-common/src/main/assets/linux/thothterm-garden.sh"));
        collect(new File(repo, "garden-common/src/main/java"), shared);
        collect(new File(repo, "garden-common/src/main/res"), shared);
        for (File f : shared) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1).toLowerCase(java.util.Locale.ROOT);
            for (String hex : new String[]{"dfe4e8", "7e8c98", "c3cad2", "fbfbfc", "6d7d89", "020406", "080c0f", "151c22", "2f363c"}) {
                assertFalse(f + " hard-codes " + hex, text.contains(hex));
            }
        }
        for (String other : new String[]{"garden-arch", "garden-debian", "term-ubuntu", "term"}) {
            List<File> files = new ArrayList<>();
            collect(new File(repo, other + "/src/main/res"), files);
            collect(new File(repo, other + "/src/main/assets"), files);
            for (File f : files) {
                if (!f.getName().matches(".*\\.(xml|properties|sh|css|js|html|txt)$")) continue;
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                assertFalse(f + " mentions blackarch", text.contains("blackarch"));
                for (String hex : new String[]{"dfe4e8", "7e8c98", "c3cad2", "020406", "080c0f"}) {
                    assertFalse(f + " carries a BlackArch colour " + hex, text.contains(hex));
                }
            }
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, out); else out.add(f);
        }
    }

    /** tools/garden/branding/palette_metallic.py re-derives every value from the delivered sheet. */
    @Test
    public void thePaletteIsDerivedFromTheCanonicalSheet() throws Exception {
        File repo = BlackArchEditionTest.repo();
        Process p;
        try {
            p = new ProcessBuilder("python3", new File(repo, "tools/garden/branding/palette_metallic.py").getPath(),
                    new File(repo, "docs/branding/blackarch/source/thothterm-blackarch-branding-sheet.png").getPath())
                    .redirectErrorStream(true).start();
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
        Map<String, String> own = colors(new File(module(), "src/main/res/values/colors.xml"));
        assertTrue(out.contains("accent_dark=#" + own.get("brand_accent_dark") + "\n"));
        assertTrue(out.contains("surface_light=#" + own.get("brand_surface_light") + "\n"));
        assertTrue(out.contains("on_surface=#" + own.get("brand_on_surface") + "\n"));
        assertTrue(out.contains("deep=#" + own.get("brand_light_primary") + "\n"));
        // The derivation is documented.
        String doc = new String(Files.readAllBytes(new File(repo, "docs/branding/blackarch/PALETTE.md").toPath()), StandardCharsets.UTF_8);
        for (String role : new String[]{"primary", "secondary", "highlight", "foreground", "muted"}) {
            assertTrue(doc, doc.contains("`" + a.hex(role) + "`"));
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
