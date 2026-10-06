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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The product of com.thothterm.debian is "ThothTerm • ThothDock". The launcher
 * label, the edition name, the F-Droid AutoName and the store title are all
 * the same string, and this test fails when one of them drifts. The guest is
 * still Debian GNU/Linux 13 (trixie), and may say so.
 */
public class ProductNameTest {
    static final String PRODUCT = "ThothTerm • ThothDock";

    private static File repoRoot() {
        File here = new File(System.getProperty("user.dir")).getAbsoluteFile();
        return new File(here, "garden-debian").isDirectory() ? here : here.getParentFile();
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(repoRoot(), path).toPath()), StandardCharsets.UTF_8);
    }

    private static String string(String xml, String name) {
        Matcher m = Pattern.compile("<string name=\"" + name + "\"[^>]*>(.*?)</string>", Pattern.DOTALL).matcher(xml);
        assertTrue("no string " + name, m.find());
        return m.group(1);
    }

    @Test
    public void everyPlaceTheProductIsNamedAgrees() throws Exception {
        String overlay = read("garden-debian/src/thothdock/res/values/strings.xml");
        // The launcher label is @string/application_terminal.
        assertTrue(read("garden-debian/src/main/AndroidManifest.xml")
                .contains("android:label=\"@string/application_terminal\""));
        assertEquals("launcher label", PRODUCT, string(overlay, "application_terminal"));
        String edition = read("garden-debian/src/thothdock/assets/garden/distro.properties");
        Matcher m = Pattern.compile("(?m)^editionName=(.*)$").matcher(edition);
        assertTrue(m.find());
        assertEquals("edition name (banner, LAN page)", PRODUCT, m.group(1).trim());
        assertEquals("fastlane title", PRODUCT,
                read("garden-debian/fastlane/metadata/android/en-US/title.txt").trim());
        for (String file : new String[]{"docs/fdroid/com.thothterm.debian.yml"}) {
            Matcher a = Pattern.compile("(?m)^AutoName: (.*)$").matcher(read(file));
            assertTrue(file + " has no AutoName", a.find());
            assertEquals("F-Droid AutoName in " + file, PRODUCT, a.group(1).trim());
        }
    }

    @Test
    public void everyUserFacingStringThatNamesTheAppUsesTheProduct() throws Exception {
        String overlay = read("garden-debian/src/thothdock/res/values/strings.xml");
        for (String key : new String[]{"about_title", "service_notify_text", "about_notice",
                "garden_welcome", "garden_consent_body"}) {
            String v = string(overlay, key);
            assertTrue(key + " does not name " + PRODUCT, v.contains(PRODUCT));
            assertFalse(key + " still names the old product", v.contains("ThothTerm Trixie"));
        }
        // The consent text still says what the download is: Debian.
        assertTrue(string(overlay, "garden_consent_body").contains("Debian GNU/Linux %1$s"));
        String full = read("garden-debian/fastlane/metadata/android/en-US/full_description.txt");
        assertFalse(full.contains("ThothTerm Trixie"));
        assertTrue(full.contains(PRODUCT));
        assertTrue(full.contains("Debian\nGNU/Linux 13 (trixie)"));
    }

    @Test
    public void storeTextFitsFdroidLimits() throws Exception {
        String dir = "garden-debian/fastlane/metadata/android/en-US/";
        assertTrue(read(dir + "title.txt").trim().length() <= 50);
        assertTrue(read(dir + "short_description.txt").trim().length() <= 80);
        assertTrue(read(dir + "changelogs/301.txt").length() <= 500);
    }
}
