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
import static org.junit.Assert.assertTrue;

import com.thothterm.linux.DistroInfo;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The edition's identity, its trust anchors and what the first slice leaves open. */
public class BlackArchEditionTest {
    private static final String ALARM_KEY = "68B3537F39A313B3E574D06777193F152BDBE6A6";
    private static final String BLACKARCH_KEY = "F9A6E68A711354D84A9B91637533BAFE69A25079";

    static File moduleDir() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "src/main/assets/garden").isDirectory()) return here;
        return new File(here, "garden-blackarch");
    }

    static File repo() {
        return moduleDir().getParentFile();
    }

    static DistroInfo distro() throws Exception {
        try (FileInputStream in = new FileInputStream(
                new File(moduleDir(), "src/main/assets/garden/distro.properties"))) {
            return DistroInfo.load(in);
        }
    }

    static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void identity() throws Exception {
        DistroInfo d = distro();
        assertEquals("ThothTerm BlackArch", d.editionName());
        assertEquals("aarch64", d.architecture());
        assertEquals("blackarch-aarch64", d.distroDir());
        assertEquals("usr/bin/sudo", d.sudoBinary());
        String gradle = read(new File(moduleDir(), "build.gradle"));
        assertTrue(gradle.contains(
                "applicationId rootProject.ext.thothtermApplicationId('com.thothterm.blackarch')"));
        assertTrue(gradle.contains("namespace = \"com.thothterm.blackarch\""));
        // F-Droid's checkupdates reads these two literally.
        assertTrue(gradle.contains("versionCode 100\n"));
        assertTrue(gradle.contains("versionName '0.1.0'\n"));
        assertTrue(read(new File(repo(), "applicationId.gradle")).contains("'com.thothterm.blackarch'"));
        assertTrue(read(new File(repo(), "settings.gradle")).contains("include ':garden-blackarch'"));
        assertEquals("ThothTerm BlackArch", read(new File(moduleDir(),
                "fastlane/metadata/android/en-US/title.txt")).trim());
    }

    /** Ubuntu 7681, Trixie 7682, Rolling 7683; the registry is docs/garden/PORTS.md. */
    @Test
    public void lanPortIsTheRegistrysNextAndTakesNoOtherEditionsPort() throws Exception {
        assertEquals(7684, distro().lanPort());
        for (String[] other : new String[][]{{"garden-debian", "7682"}, {"garden-arch", "7683"}}) {
            String props = read(new File(repo(), other[0] + "/src/main/assets/garden/distro.properties"));
            assertTrue(other[0] + " keeps its port", props.contains("\nlanPort=" + other[1] + "\n"));
        }
        String ports = read(new File(repo(), "docs/garden/PORTS.md"));
        assertTrue(ports.contains("| 7684 | ThothTerm BlackArch | `com.thothterm.blackarch` |"));
        assertTrue("the registry names the next free port", ports.contains("The next edition takes **7685**"));
    }

    /** Arch Linux ARM first, then BlackArch, each with its signing key. */
    @Test
    public void declaresBothKeyringsAndBothSigningKeys() throws Exception {
        DistroInfo d = distro();
        assertEquals(DistroInfo.PACMAN, d.packageManager());
        assertEquals("wheel", d.adminGroup());
        assertEquals(Arrays.asList("archlinuxarm", "blackarch"), d.pacmanKeyrings());
        assertEquals(Arrays.asList(ALARM_KEY, BLACKARCH_KEY), d.packageSigningKeys());
        // The base distribution's anchors are the ones Rolling has, unchanged.
        DistroInfo rolling;
        try (FileInputStream in = new FileInputStream(
                new File(repo(), "garden-arch/src/main/assets/garden/distro.properties"))) {
            rolling = DistroInfo.load(in);
        }
        assertEquals(rolling.pacmanKeyring(), d.pacmanKeyring());
        assertEquals(rolling.packageSigningKey(), d.packageSigningKey());
    }

    /**
     * The rootfs does not exist yet. The pin is a placeholder that nothing can
     * match, so a build of this slice can neither embed nor download a
     * rootfs. The rootfs slice replaces the pin and this test with the real
     * content-addressed checks.
     */
    @Test
    public void theRootfsPinIsAPlaceholderNothingCanMatch() throws Exception {
        DistroInfo d = distro();
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", d.sha256());
        assertTrue(d.sourceUrl().startsWith("https://"));
        assertTrue("a reserved, never-resolving host", d.sourceUrl().contains(".invalid/"));
        assertFalse(new File(moduleDir(), "src/main/assets/garden/rootfs").exists());
        assertFalse(new File(moduleDir(), "rootfs").exists());
        String gradle = read(new File(moduleDir(), "build.gradle"));
        assertTrue(gradle.contains("f.length() == expectedSize && sha256(f) == expectedSha"));
        assertTrue(gradle.contains("if (task.name ==~ /^pre[Ff]ull.*Build$/) task.dependsOn stageRootfs"));
    }

    /** No weakened signature level anywhere in the edition's data or sources. */
    @Test
    public void nothingInTheEditionRelaxesSignatureChecking() throws Exception {
        for (String path : new String[]{"src/main/assets/garden/distro.properties", "build.gradle"}) {
            String code = read(new File(moduleDir(), path)).replaceAll("(?m)^\\s*(#|//).*$", "");
            assertFalse(path, code.contains("SigLevel"));
            assertFalse(path, code.contains("TrustAll"));
        }
    }

    private static final String[] EDITION_STRINGS = {
            "application_terminal", "application_positioning", "about_title", "about_notice",
            "garden_welcome", "garden_extracting_percent", "garden_downloading",
            "garden_consent_title", "garden_consent_body", "garden_consent_accept",
            "garden_consent_declined", "service_notify_text", "lan_help_off",
    };

    private static Map<String, String> strings(File file) throws Exception {
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("<string name=\"([a-z_]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL)
                .matcher(read(file));
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    @Test
    public void theEditionNamesItselfEverywhere() throws Exception {
        Map<String, String> own = strings(new File(moduleDir(), "src/main/res/values/strings.xml"));
        Map<String, String> common = strings(new File(repo(), "garden-common/src/main/res/values/strings.xml"));
        common.putAll(strings(new File(repo(), "garden-common/src/main/res/values/strings_lan.xml")));
        for (String key : EDITION_STRINGS) {
            assertTrue("garden-common no longer defines " + key, common.containsKey(key));
            assertTrue("ThothTerm BlackArch must override " + key, own.containsKey(key));
        }
        for (Map.Entry<String, String> e : own.entrySet()) {
            for (String other : new String[]{"Ubuntu", "Debian", "Trixie", "Rolling"}) {
                assertFalse(e.getKey() + " names another edition", e.getValue().contains(other));
            }
        }
        String consent = own.get("garden_consent_body");
        for (String arg : new String[]{"%1$s", "%2$d", "%3$s"}) {
            assertTrue("consent text lost " + arg, consent.contains(arg));
        }
        assertTrue(consent.contains("not encrypted in transit"));
        assertTrue(consent.contains("SHA‑256"));
        assertTrue(consent.contains("not built or verified by F‑Droid"));
        assertEquals("ThothTerm BlackArch", own.get("application_terminal"));
        assertEquals("ThothTerm BlackArch is running", own.get("service_notify_text"));
        assertTrue(own.get("about_notice").contains(
                "not affiliated with or endorsed by the BlackArch, Arch Linux or Arch Linux ARM projects"));
    }

    /** The catalog is never oversold: no count of tools, no x86_64 promise, no endorsement. */
    @Test
    public void noUserVisibleTextOversellsOrImpliesEndorsement() throws Exception {
        java.util.List<File> visible = new java.util.ArrayList<>();
        visible.add(new File(moduleDir(), "src/main/res/values/strings.xml"));
        File fastlane = new File(moduleDir(), "fastlane/metadata/android/en-US");
        for (String name : new String[]{"title.txt", "short_description.txt", "full_description.txt"}) {
            visible.add(new File(fastlane, name));
        }
        File[] changelogs = new File(fastlane, "changelogs").listFiles();
        assertTrue(changelogs != null && changelogs.length > 0);
        visible.addAll(Arrays.asList(changelogs));
        for (File f : visible) {
            String text = read(f).replaceAll("(?m)^#.*$", "").replaceAll("(?s)<!--.*?-->", "")
                    .toLowerCase(java.util.Locale.ROOT);
            String unaffiliated = text.replace("not affiliated with or endorsed by", "");
            for (String word : new String[]{"official", "certified", "endorsed by", "supported by"}) {
                assertFalse(f + " says " + word, unaffiliated.contains(word));
            }
            assertFalse(f + " quotes a tool count", Pattern.compile("[0-9][0-9,.]*\\+? (security )?tools").matcher(text).find());
            assertFalse(f + " promises blackarch-officials", text.contains("blackarch-officials"));
            assertFalse(f + " promises every tool", text.contains("all tools") || text.contains("every tool"));
        }
    }

    /**
     * The measured dependency analysis is quoted with its limit, never as a
     * claim that packages install.
     */
    @Test
    public void theDesignRecordWordsTheDependencyAnalysisCarefully() throws Exception {
        String design = read(new File(repo(), "docs/garden/blackarch/DESIGN.md"));
        assertTrue(design.replace("\n> ", " ").replace("\n", " ").contains(
                "Approximately 94% of the current aarch64 catalog passed the present dependency-resolution "
                        + "analysis; this is not equivalent to successful installation/runtime validation."));
        assertFalse(design.contains("installable"));
        assertTrue("trademark review is a release gate", design.contains("Release gate."));
        assertTrue(design.contains("UNVERIFIED"));
    }
}
