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
 * Outside the app is the cup, inside it the eight-petal emblem: ThothTerm's own
 * Garden masters turned cool blue by tools/garden/branding/recolor.py. No Arch
 * Linux artwork anywhere; garden-common carries no edition's artwork.
 */
public class ArchBrandingTest {
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

    private static File res() {
        return new File(ArchEditionTest.moduleDir(), "src/main/res");
    }

    private static File repo() {
        return ArchEditionTest.moduleDir().getParentFile();
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
        assertArrayEquals(new int[]{736, 736},
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
        File masters = new File(repo(), "docs/branding/arch/source");
        File args = new File(masters, "make_resources.args");
        File out = Files.createTempDirectory("arch-res").toFile();
        List<String> cmd = new ArrayList<>();
        cmd.add("python3");
        cmd.add(tool.getPath());
        cmd.add(new File(masters, "thothterm-arch-launcher-cup-master.png").getPath());
        cmd.add(new File(masters, "thothterm-arch-emblem-eight-petal-master.png").getPath());
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

    /** The masters are the approved, recorded files. */
    @Test
    public void mastersMatchTheirRecordedHashes() throws Exception {
        File masters = new File(repo(), "docs/branding/arch/source");
        String readme = new String(Files.readAllBytes(new File(masters, "README.md").toPath()),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("`(thothterm-arch-[a-z-]+\\.png)` \\|[^|]*\\|[^`]*`([0-9a-f]{64})`").matcher(readme);
        int seen = 0;
        while (m.find()) {
            byte[] bytes = Files.readAllBytes(new File(masters, m.group(1)).toPath());
            assertEquals(m.group(1), m.group(2), sha256(bytes));
            seen++;
        }
        assertEquals(3, seen);
    }

    /** The masters are the Garden masters under recolor.py, byte for byte. */
    @Test
    public void mastersAreDerivedFromTheGardenMasters() throws Exception {
        File out = Files.createTempDirectory("arch-masters").toFile();
        List<String> cmd = new ArrayList<>(java.util.Arrays.asList("python3",
                new File(repo(), "tools/garden/branding/recolor.py").getPath(),
                new File(repo(), "docs/branding/debian/source").getPath(), "debian",
                out.getPath(), "arch"));
        String args = new String(Files.readAllBytes(new File(repo(),
                "docs/branding/arch/source/recolor.args").toPath()), StandardCharsets.UTF_8).trim();
        cmd.addAll(java.util.Arrays.asList(args.split("\\s+")));
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
        for (String kind : new String[]{"branding-sheet", "launcher-cup-master", "emblem-eight-petal-master"}) {
            String name = "thothterm-arch-" + kind + ".png";
            assertArrayEquals(name, Files.readAllBytes(new File(out, name).toPath()),
                    Files.readAllBytes(new File(repo(), "docs/branding/arch/source/" + name).toPath()));
        }
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
