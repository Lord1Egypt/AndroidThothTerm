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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outside the app is the cup, inside it the eight-petal emblem: ThothTerm's own Garden
 * BlackArch artwork (black graphite and silver). No BlackArch, Arch Linux or Arch Linux ARM
 * artwork anywhere; garden-common carries no edition's artwork; the placeholder is gone.
 */
public class BlackArchBrandingTest {
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

    private static File res() {
        return new File(BlackArchEditionTest.moduleDir(), "src/main/res");
    }

    private static File repo() {
        return BlackArchEditionTest.moduleDir().getParentFile();
    }

    /** Width and height from a VP8/VP8L/VP8X WebP header. */
    private static int[] webpSize(File file) throws Exception {
        byte[] b = Files.readAllBytes(file.toPath());
        assertEquals("RIFF", new String(b, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WEBP", new String(b, 8, 4, StandardCharsets.US_ASCII));
        String chunk = new String(b, 12, 4, StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8L": {
                int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                return new int[]{(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
            }
            case "VP8X":
                return new int[]{1 + ((b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16),
                        1 + ((b[27] & 0xFF) | (b[28] & 0xFF) << 8 | (b[29] & 0xFF) << 16)};
            case "VP8 ":
                return new int[]{((b[26] & 0xFF) | (b[27] & 0xFF) << 8) & 0x3FFF,
                        ((b[28] & 0xFF) | (b[29] & 0xFF) << 8) & 0x3FFF};
            default:
                throw new AssertionError("unknown WebP chunk " + chunk);
        }
    }

    @Test
    public void everyLauncherLayerExistsAtEveryDensity() throws Exception {
        double[] scale = {1, 1.5, 2, 3, 4};
        for (int i = 0; i < DENSITIES.length; i++) {
            File dir = new File(res(), "mipmap-" + DENSITIES[i]);
            int canvas = (int) Math.round(108 * scale[i]);
            int legacy = (int) Math.round(48 * scale[i]);
            assertArrayEquals(new int[]{canvas, canvas}, webpSize(new File(dir, "ic_launcher_foreground.webp")));
            assertArrayEquals(new int[]{canvas, canvas}, webpSize(new File(dir, "ic_launcher_monochrome.webp")));
            assertArrayEquals(new int[]{legacy, legacy}, webpSize(new File(dir, "ic_launcher.webp")));
            assertArrayEquals(new int[]{legacy, legacy}, webpSize(new File(dir, "ic_launcher_round.webp")));
        }
        String adaptive = new String(Files.readAllBytes(
                new File(res(), "mipmap-anydpi-v26/ic_launcher.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue(adaptive.contains("@mipmap/ic_launcher_foreground"));
        assertTrue(adaptive.contains("@mipmap/ic_launcher_monochrome"));
    }

    /** The in-app mark is the emblem master itself, at its native size. */
    @Test
    public void insideTheAppIsTheEmblem() throws Exception {
        assertArrayEquals(new int[]{743, 743},
                webpSize(new File(res(), "drawable-nodpi/ic_splash_mark.webp")));
    }

    @Test
    public void garden_commonShipsNoEditionArtwork() {
        File common = new File(repo(), "garden-common/src/main/res");
        File[] dirs = common.listFiles((dir, name) -> name.startsWith("mipmap"));
        assertTrue("garden-common must not carry launcher icons", dirs == null || dirs.length == 0);
        assertFalse(new File(common, "drawable-nodpi/ic_splash_mark.webp").exists());
    }

    /**
     * Regenerates the resources from the masters and requires identical bytes:
     * nothing in res/ was drawn, recoloured or swapped by hand.
     */
    @Test
    public void resourcesAreReproducibleFromTheMasters() throws Exception {
        File tool = new File(repo(), "tools/garden/branding/make_resources.py");
        File masters = new File(repo(), "docs/branding/blackarch/source");
        File args = new File(masters, "make_resources.args");
        File out = Files.createTempDirectory("blackarch-res").toFile();
        List<String> cmd = new ArrayList<>();
        cmd.add("python3");
        cmd.add(tool.getPath());
        cmd.add(new File(masters, "thothterm-blackarch-launcher-cup-master.png").getPath());
        cmd.add(new File(masters, "thothterm-blackarch-emblem-eight-petal-master.png").getPath());
        cmd.add(out.getPath());
        for (String a : new String(Files.readAllBytes(args.toPath()), StandardCharsets.UTF_8).trim().split("\\s+")) {
            cmd.add(a);
        }
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assume.assumeNoException("python3 is not available", e);
            return;
        }
        String output = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        int code = p.waitFor();
        Assume.assumeFalse("Pillow is not available", output.contains("No module named 'PIL'"));
        assertEquals(output, 0, code);

        int compared = 0;
        for (String density : new String[]{"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"}) {
            File[] made = new File(out, "mipmap-" + density).listFiles();
            assertTrue(made != null);
            for (File f : made) {
                assertArrayEquals(density + "/" + f.getName(), Files.readAllBytes(f.toPath()),
                        Files.readAllBytes(new File(res(), "mipmap-" + density + "/" + f.getName()).toPath()));
                compared++;
            }
        }
        // The lossy splash depends on the libwebp build; compare the size only.
        assertArrayEquals(webpSize(new File(out, "drawable-nodpi/ic_splash_mark.webp")),
                webpSize(new File(res(), "drawable-nodpi/ic_splash_mark.webp")));
        assertEquals(22, compared);
    }

    /** make_resources.args is what measure_sheet.py measures on this sheet and cup master. */
    @Test
    public void theResourceArgumentsAreMeasuredNotChosen() throws Exception {
        File source = new File(repo(), "docs/branding/blackarch/source");
        Process p;
        try {
            p = new ProcessBuilder("python3", new File(repo(), "tools/garden/branding/measure_sheet.py").getPath(),
                    new File(source, "thothterm-blackarch-branding-sheet.png").getPath(),
                    new File(source, "thothterm-blackarch-launcher-cup-master.png").getPath())
                    .redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assume.assumeNoException("python3 is not available", e);
            return;
        }
        String output = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8).trim();
        p.waitFor();
        Assume.assumeFalse("Pillow or numpy is not available", output.contains("No module named"));
        assertEquals(new String(Files.readAllBytes(new File(source, "make_resources.args").toPath()),
                StandardCharsets.UTF_8).trim(), output);
    }

    /** The masters are the approved, recorded files. */
    @Test
    public void mastersMatchTheirRecordedHashes() throws Exception {
        File masters = new File(repo(), "docs/branding/blackarch/source");
        String readme = new String(Files.readAllBytes(new File(masters, "README.md").toPath()),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("`(thothterm-blackarch-[a-z-]+\\.png)` \\|[^|]*\\|[^`]*`([0-9a-f]{64})`").matcher(readme);
        int seen = 0;
        while (m.find()) {
            byte[] bytes = Files.readAllBytes(new File(masters, m.group(1)).toPath());
            assertEquals(m.group(1), m.group(2), sha256(bytes));
            seen++;
        }
        assertEquals(3, seen);
    }

    /** The masters are the sheet's, extracted byte for byte by extract_masters.py. */
    @Test
    public void mastersAreExtractedFromTheApprovedSheet() throws Exception {
        File out = Files.createTempDirectory("blackarch-masters").toFile();
        File sheet = new File(repo(), "docs/branding/blackarch/source/thothterm-blackarch-branding-sheet.png");
        List<String> cmd = new ArrayList<>(java.util.Arrays.asList("python3",
                new File(repo(), "tools/garden/branding/extract_masters.py").getPath(),
                sheet.getPath(), "blackarch", out.getPath()));
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assume.assumeNoException("python3 is not available", e);
            return;
        }
        String output = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        int code = p.waitFor();
        Assume.assumeFalse("Pillow or numpy is not available", output.contains("No module named"));
        assertEquals(output, 0, code);
        for (String kind : new String[]{"launcher-cup-master", "emblem-eight-petal-master"}) {
            String name = "thothterm-blackarch-" + kind + ".png";
            assertArrayEquals(name, Files.readAllBytes(new File(out, name).toPath()),
                    Files.readAllBytes(new File(repo(), "docs/branding/blackarch/source/" + name).toPath()));
        }
    }

    /** The approved sheet is the delivered artwork, byte for byte, and its provenance is written down. */
    @Test
    public void theSourceArtworkIsTheDeliveredSheetAndDocumented() throws Exception {
        File source = new File(repo(), "docs/branding/blackarch/source/thothterm-blackarch-branding-sheet.png");
        String hash = "4f7e13fe5c7884774e3d2bc7ef0bb98fcd4aca17b80d4c495916f53f0092dcba";
        assertEquals(hash, sha256(Files.readAllBytes(source.toPath())));
        File delivered = new File(repo().getParentFile(), "logos/ChatGPT Image Sep 26, 2026, 07_39_07 AM.png");
        if (delivered.isFile()) assertEquals(hash, sha256(Files.readAllBytes(delivered.toPath())));
        String readme = new String(Files.readAllBytes(new File(repo(),
                "docs/branding/blackarch/source/README.md").toPath()), StandardCharsets.UTF_8);
        assertTrue(readme.contains(hash));
        assertTrue(readme.contains("ChatGPT Image Sep 26, 2026, 07_39_07 AM.png"));
        assertTrue(readme.replaceAll("\\s+", " ").contains("no BlackArch, Arch Linux or Arch Linux ARM logo or artwork is used"));
    }

    /** No placeholder icon or neutral fallback survives: the launcher is the generated cup, the wordmark is nowhere in res. */
    @Test
    public void thePlaceholderIsGone() throws Exception {
        assertFalse(new File(res(), "drawable/ic_launcher_foreground.xml").exists());
        String background = new String(Files.readAllBytes(new File(res(), "values/ic_launcher_background.xml").toPath()),
                StandardCharsets.UTF_8);
        assertFalse(background.toLowerCase(java.util.Locale.ROOT).contains("placeholder"));
        for (String f : new String[]{"mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml"}) {
            String xml = new String(Files.readAllBytes(new File(res(), f).toPath()), StandardCharsets.UTF_8);
            assertFalse(f, xml.toLowerCase(java.util.Locale.ROOT).contains("placeholder"));
            assertTrue(f, xml.contains("@mipmap/ic_launcher_foreground"));
            assertTrue(f, xml.contains("@drawable/ic_launcher_background"));
        }
        assertTrue(new File(res(), "drawable/ic_launcher_background.xml").isFile());
        assertTrue("an edition palette", new File(BlackArchEditionTest.moduleDir(), "src/main/assets/garden/palette.properties").isFile());
        assertTrue("edition app chrome", new File(res(), "values/colors.xml").isFile());
    }

    /** Both marks are clean: the launcher cup is the same file set as Rolling's, none of it carries a wordmark. */
    @Test
    public void theResourceSetMatchesRollings() throws Exception {
        java.util.TreeSet<String> own = new java.util.TreeSet<>(), rolling = new java.util.TreeSet<>();
        collect(res(), res(), own);
        File arch = new File(repo(), "garden-arch/src/main/res");
        collect(arch, arch, rolling);
        assertEquals(rolling, own);
    }

    private static void collect(File root, File dir, java.util.Set<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(root, f, out);
            else out.add(root.toPath().relativize(f.toPath()).toString());
        }
    }

    /** The launcher's foreground is the cup inside the 66 dp safe zone: no pixel outside it is opaque. */
    @Test
    public void theForegroundStaysInsideTheSafeZone() throws Exception {
        Assume.assumeTrue(new File("/usr/bin/python3").canExecute() || new File("/usr/local/bin/python3").canExecute()
                || new File(System.getProperty("user.home") + "/miniconda3/bin/python3").canExecute());
        File xxxhdpi = new File(res(), "mipmap-xxxhdpi/ic_launcher_foreground.webp");
        String script = "import sys\nfrom PIL import Image\nim=Image.open(sys.argv[1]).convert('RGBA')\n"
                + "w,h=im.size; a=im.getchannel('A'); l,t,r,b=a.point(lambda v:255 if v>8 else 0).getbbox()\n"
                + "m=(w-round(66*4))//2\nprint(l>=m-1, t>=m-1, r<=w-m+1, b<=h-m+1, w)\n";
        Process p;
        try {
            p = new ProcessBuilder("python3", "-c", script, xxxhdpi.getPath()).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assume.assumeNoException("python3 is not available", e);
            return;
        }
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8).trim();
        p.waitFor();
        Assume.assumeFalse("Pillow is not available", out.contains("No module named"));
        assertEquals(out, "True True True True 432", out);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(String.format("%02x", b & 0xFF));
        return s.toString();
    }

    private static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
