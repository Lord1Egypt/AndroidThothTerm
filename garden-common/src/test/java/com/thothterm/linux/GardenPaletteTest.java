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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class GardenPaletteTest {
    static GardenPalette ubuntu() throws IOException {
        InputStream in = GardenPaletteTest.class.getResourceAsStream("/garden/ubuntu-palette.properties");
        assertTrue("fixture missing", in != null);
        return GardenPalette.load(in);
    }

    static GardenPalette load(String text) throws IOException {
        return GardenPalette.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Ubuntu's palette resolves to exactly the colours term-ubuntu hardcodes. */
    @Test
    public void ubuntuResolvesToUbuntusColours() throws Exception {
        GardenPalette u = ubuntu();
        assertEquals(214, u.xterm256("primary"));
        assertEquals(44, u.xterm256("secondary"));
        assertEquals(252, u.xterm256("highlight"));
        assertEquals(246, u.xterm256("foreground"));
        assertEquals(246, u.xterm256("muted"));
        assertEquals(39, u.xterm256("promptUser"));
        assertEquals(214, u.xterm256("promptHost"));
        assertEquals(252, u.xterm256("promptPath"));
    }

    /** Every xterm colour maps back to itself, so a palette can name any of them exactly. */
    @Test
    public void everyXtermColourRoundTrips() {
        for (int index = 16; index < 256; index++) {
            int rgb = GardenPalette.xtermRgb(index);
            int back = GardenPalette.nearestXterm256(rgb);
            assertEquals("index " + index, rgb, GardenPalette.xtermRgb(back));
        }
    }

    @Test
    public void theGuestFileNamesEveryTextRoleAsSgr() throws Exception {
        String file = ubuntu().guestFile();
        for (String role : GardenPalette.TEXT_ROLES) {
            assertTrue(role, file.matches("(?s).*\\n" + role + "=38;5;\\d{1,3}\\n.*"));
        }
        for (String line : file.split("\n")) {
            assertTrue(line, line.startsWith("#") || line.matches("[A-Za-z]+=[0-9;]+"));
        }
    }

    @Test
    public void cssCarriesTheChromeRoles() throws Exception {
        String css = ubuntu().css();
        assertTrue(css.contains("--bg: #07111F;"));
        assertTrue(css.contains("--accent: #FFAF00;"));
        assertTrue(css.contains("--term-cursor: #FFAF00;"));
        assertTrue(css.matches("(?s):root \\{\\n(  --[a-z-]+: #[0-9A-F]{6};\\n)+\\}\\n"));
    }

    @Test
    public void refusesMissingOrMalformedColours() throws Exception {
        StringBuilder good = new StringBuilder();
        for (String r : GardenPalette.TEXT_ROLES) good.append(r).append("=#112233\n");
        for (String r : GardenPalette.SURFACE_ROLES) good.append(r).append("=#112233\n");
        load(good.toString());
        for (String bad : new String[]{"primary=#11223\n", "primary=red\n", "primary=#1122334\n",
                "primary=112233\n", "primary=#11\\u001b33\n"}) {
            try {
                load(good.toString().replace("primary=#112233\n", bad));
                fail("accepted " + bad);
            } catch (IOException expected) {
                // fail closed
            }
        }
        try {
            load(good.toString().replace("muted=#112233\n", ""));
            fail("accepted a palette without muted");
        } catch (IOException expected) {
            // fail closed
        }
    }
}
