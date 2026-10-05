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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * An edition's colours for what ThothTerm itself draws: the welcome banner,
 * the managed prompt, the terminal's default background and foreground, and
 * the LAN page's chrome. Programs' own ANSI output is never touched.
 *
 * <p>Read from the edition's {@code assets/garden/palette.properties}, one
 * {@code #RRGGBB} per role. The phone terminal speaks 256-colour SGR only, so
 * text roles are drawn with the nearest xterm-256 colour (CIE Lab distance
 * over indices 16-255; 0-15 are left out because terminals retheme them).
 * The phone renderer and xterm.js share that table, so both show the same
 * colour for the same index.
 */
public final class GardenPalette {
    public static final String ASSET = "garden/palette.properties";

    /** Roles used as ANSI text colours inside the guest. */
    public static final String[] TEXT_ROLES = {
            "primary", "secondary", "highlight", "foreground", "muted",
            "promptUser", "promptHost", "promptPath",
    };
    /** Roles used as full colours by the app and the LAN page. */
    public static final String[] SURFACE_ROLES = {"background", "surface"};
    /**
     * Optional roles an edition may add; absent, the terminal behaves as it
     * always did. {@code selection}: background of selected text (default: the
     * cursor colour). {@code ansiBlue}: a legible replacement for ANSI colour 4
     * on a dark background.
     */
    public static final String[] OPTIONAL_ROLES = {"selection", "ansiBlue"};

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final Map<String, Integer> colors;

    private GardenPalette(Map<String, Integer> colors) {
        this.colors = colors;
    }

    public static GardenPalette load(InputStream in) throws IOException {
        Properties p = new Properties();
        try {
            p.load(new InputStreamReader(in, "UTF-8"));
        } finally {
            in.close();
        }
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String[] roles : new String[][]{TEXT_ROLES, SURFACE_ROLES}) {
            for (String role : roles) {
                String value = p.getProperty(role, "").trim();
                if (!HEX.matcher(value).matches()) {
                    throw new IOException("Invalid or missing " + role + " in " + ASSET);
                }
                colors.put(role, Integer.parseInt(value.substring(1), 16));
            }
        }
        for (String role : OPTIONAL_ROLES) {
            String value = p.getProperty(role, "").trim();
            if (value.isEmpty()) continue;
            if (!HEX.matcher(value).matches()) {
                throw new IOException("Invalid " + role + " in " + ASSET);
            }
            colors.put(role, Integer.parseInt(value.substring(1), 16));
        }
        return new GardenPalette(colors);
    }

    /** True if the palette defines the role (always true for the mandatory ones). */
    public boolean has(String role) {
        return colors.containsKey(role);
    }

    /** The role's colour as 0xRRGGBB. */
    public int rgb(String role) {
        Integer value = colors.get(role);
        if (value == null) throw new IllegalArgumentException("Unknown palette role " + role);
        return value;
    }

    /** The role's colour as opaque ARGB, for Android. */
    public int argb(String role) {
        return 0xFF000000 | rgb(role);
    }

    public String hex(String role) {
        return String.format(Locale.ROOT, "#%06X", rgb(role));
    }

    /** The xterm-256 index that draws the role. */
    public int xterm256(String role) {
        return nearestXterm256(rgb(role));
    }

    /**
     * The guest's /etc/thothterm/palette: one {@code role=SGR} line per text
     * role, read as data by thothfetch and the managed prompt.
     */
    public String guestFile() {
        StringBuilder out = new StringBuilder("# Managed by ThothTerm: this edition's colours for its own banner and prompt.\n");
        for (String role : TEXT_ROLES) {
            out.append(role).append("=38;5;").append(xterm256(role)).append('\n');
        }
        return out.toString();
    }

    /** CSS custom properties for the LAN page's chrome and the browser terminal's defaults. */
    public String css() {
        return ":root {\n"
                + "  --bg: " + hex("background") + ";\n"
                + "  --bar: " + hex("surface") + ";\n"
                + "  --text: " + hex("foreground") + ";\n"
                + "  --muted: " + hex("muted") + ";\n"
                + "  --accent: " + hex("primary") + ";\n"
                + "  --accent-soft: " + hex("secondary") + ";\n"
                + "  --term-bg: " + hex("background") + ";\n"
                + "  --term-fg: " + hex("foreground") + ";\n"
                + "  --term-cursor: " + hex("primary") + ";\n"
                + "}\n";
    }

    /** xterm's 256-colour table: 16-231 a 6x6x6 cube, 232-255 a grey ramp. */
    public static int xtermRgb(int index) {
        if (index >= 232) {
            int v = 8 + 10 * (index - 232);
            return (v << 16) | (v << 8) | v;
        }
        int[] levels = {0, 95, 135, 175, 215, 255};
        int i = index - 16;
        return (levels[i / 36] << 16) | (levels[(i / 6) % 6] << 8) | levels[i % 6];
    }

    static int nearestXterm256(int rgb) {
        double[] target = lab(rgb);
        int best = 16;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 16; index < 256; index++) {
            double[] candidate = lab(xtermRgb(index));
            double d = 0;
            for (int k = 0; k < 3; k++) d += (target[k] - candidate[k]) * (target[k] - candidate[k]);
            if (d < bestDistance) {
                bestDistance = d;
                best = index;
            }
        }
        return best;
    }

    /** sRGB to CIE L*a*b* (D65). */
    static double[] lab(int rgb) {
        double r = linear((rgb >> 16) & 0xFF), g = linear((rgb >> 8) & 0xFF), b = linear(rgb & 0xFF);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    /** WCAG contrast ratio between two colours. */
    public static double contrast(int rgbA, int rgbB) {
        double a = luminance(rgbA), b = luminance(rgbB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear((rgb >> 16) & 0xFF) + 0.7152 * linear((rgb >> 8) & 0xFF)
                + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }
}
