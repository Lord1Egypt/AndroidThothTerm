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


package com.thothterm.security;

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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ThothTerm Security's identity, its base system, and the optional third-party
 * repository: nothing of that repository is shipped, and what the app fetches
 * at run time is pinned.
 */
public class SecurityEditionTest {
    private static final String ALARM_KEY = "68B3537F39A313B3E574D06777193F152BDBE6A6";
    private static final String REPO_SIGNER = "F9A6E68A711354D84A9B91637533BAFE69A25079";

    static File moduleDir() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "src/main/assets/garden").isDirectory()) return here;
        return new File(here, "garden-security");
    }

    static File repo() {
        return moduleDir().getParentFile();
    }

    static DistroInfo distro(String module) throws Exception {
        try (FileInputStream in = new FileInputStream(
                new File(repo(), module + "/src/main/assets/garden/distro.properties"))) {
            return DistroInfo.load(in);
        }
    }

    static DistroInfo distro() throws Exception {
        return distro("garden-security");
    }

    static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void identity() throws Exception {
        DistroInfo d = distro();
        assertEquals("ThothTerm Security", d.editionName());
        assertEquals("aarch64", d.architecture());
        assertEquals("security-aarch64", d.distroDir());
        assertEquals("usr/bin/sudo", d.sudoBinary());
        String gradle = read(new File(moduleDir(), "build.gradle"));
        assertTrue(gradle.contains(
                "applicationId rootProject.ext.thothtermApplicationId('com.thothterm.security')"));
        assertTrue(gradle.contains("namespace = \"com.thothterm.security\""));
        // F-Droid's checkupdates reads these two literally.
        assertTrue(gradle.contains("versionCode 100\n"));
        assertTrue(gradle.contains("versionName '0.1.0'\n"));
        assertTrue(read(new File(repo(), "applicationId.gradle")).contains("'com.thothterm.security'"));
        assertTrue(read(new File(repo(), "settings.gradle")).contains("include ':garden-security'"));
        assertEquals("ThothTerm Security", read(new File(moduleDir(),
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
        assertTrue(ports.contains("| 7684 | ThothTerm Security | `com.thothterm.security` |"));
        assertTrue("the registry names the next free port", ports.contains("The next edition takes **7685**"));
    }

    /** Only Arch Linux ARM's keyring and key: the third-party keyring is never part of the base. */
    @Test
    public void theBaseTrustsOnlyArchLinuxArm() throws Exception {
        DistroInfo d = distro();
        assertEquals(DistroInfo.PACMAN, d.packageManager());
        assertEquals("wheel", d.adminGroup());
        assertEquals("archlinuxarm", d.pacmanKeyring());
        assertEquals(ALARM_KEY, d.packageSigningKey());
        DistroInfo rolling = distro("garden-arch");
        assertEquals(rolling.pacmanKeyring(), d.pacmanKeyring());
        assertEquals(rolling.packageSigningKey(), d.packageSigningKey());
    }

    /**
     * The base system is Rolling's published archive, byte for byte: same
     * URL, size and digest. It was rebuilt twice from its pinned inputs and
     * reproduced (docs/garden/security/ROOTFS_PROVENANCE.md).
     */
    @Test
    public void theRootfsIsRollingsPublishedImage() throws Exception {
        DistroInfo d = distro();
        DistroInfo rolling = distro("garden-arch");
        assertEquals(rolling.sha256(), d.sha256());
        assertEquals(rolling.compressedSize(), d.compressedSize());
        assertEquals(rolling.uncompressedSize(), d.uncompressedSize());
        assertEquals(rolling.sourceUrl(), d.sourceUrl());
        assertEquals(rolling.assetName(), d.assetName());
        assertEquals("03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1", d.sha256());
        assertFalse(d.sourceUrl().toLowerCase(java.util.Locale.ROOT).contains("blackarch"));
        assertEquals("arch-aarch64-" + d.sha256().substring(0, 12), d.imageId());
        assertTrue(d.sourceUrl().startsWith("https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/"
                + "arch-rootfs-aarch64-" + d.sha256().substring(0, 12) + "/"));
    }

    /** Both flavours extract the same bytes; only the full flavour embeds them. */
    @Test
    public void onlyTheFullFlavourEmbedsTheRootfs() throws Exception {
        String gradle = read(new File(moduleDir(), "build.gradle"));
        assertTrue(gradle.contains("full {\n            assets.srcDirs += \"$buildDir/garden/rootfs-assets\""));
        assertTrue(gradle.contains("if (task.name ==~ /^pre[Ff]ull.*Build$/) task.dependsOn stageRootfs"));
        assertFalse(gradle.contains("fdroid {\n            assets"));
        assertTrue(gradle.contains("f.length() == expectedSize && sha256(f) == expectedSha"));
        assertFalse(new File(moduleDir(), "src/main/assets/garden/rootfs").exists());
        assertFalse(new File(moduleDir(), "src/fdroid").exists());
    }

    /**
     * A third-party trust bundle is never redistributed: no keyring package,
     * key file, repository configuration or pin list in the module, and no
     * binary of any kind in its assets.
     */
    @Test
    public void noThirdPartyKeyringOrRepositoryPayloadIsShipped() throws Exception {
        assertFalse("no rootfs builder here: the base is Rolling's", new File(moduleDir(), "rootfs").exists());
        java.util.List<File> stack = new java.util.ArrayList<>();
        stack.add(new File(moduleDir(), "src/main"));
        stack.add(new File(moduleDir(), "fastlane"));
        while (!stack.isEmpty()) {
            File f = stack.remove(stack.size() - 1);
            if (f.isDirectory()) {
                stack.addAll(Arrays.asList(f.listFiles()));
                continue;
            }
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(f + " is a package or key file", name.endsWith(".gpg") || name.endsWith(".zst")
                    || name.endsWith(".sig") || name.contains("pkg.tar") || name.endsWith(".asc"));
            assertFalse(f + " is rootfs content", f.getPath().contains("/assets/garden/rootfs"));
        }
        // The rootfs identity names no third-party package either.
        File[] assets = new File(moduleDir(), "src/main/assets/garden").listFiles();
        assertTrue(assets != null);
        for (File a : assets) {
            assertTrue(a.getName(), Arrays.asList("distro.properties", "palette.properties").contains(a.getName()));
        }
    }

    /** What the app is allowed to fetch at run time, and nothing it could be tricked into fetching instead. */
    @Test
    public void theOptionalRepositoryIsPinnedAndFactual() throws Exception {
        DistroInfo d = distro();
        assertTrue(d.hasExtraRepo());
        assertEquals("blackarch", d.extraRepo());
        assertEquals("BlackArch repository", d.extraRepoLabel());
        assertEquals("https://blackarch.org/blackarch/$repo/os/$arch", d.extraRepoServer());
        assertEquals("https://blackarch.org/blackarch/blackarch/os/aarch64/blackarch-keyring-20251011-2-any.pkg.tar.zst",
                d.extraRepoKeyringUrl());
        assertEquals(d.extraRepoKeyringUrl() + ".sig", d.extraRepoKeyringSigUrl());
        assertEquals(18445L, d.extraRepoKeyringSize());
        assertEquals("d40c1301f84195164a2843f2447f5b3833bd9ea30e5cfdaf4271ad9c9771ee07", d.extraRepoKeyringSha256());
        assertEquals(566L, d.extraRepoKeyringSigSize());
        assertEquals("c2e21fcd878e26ed0b91ebc53f5fbb3d9a5d28278f0eb218ea866b7a2c04499d", d.extraRepoKeyringSigSha256());
        assertEquals(Arrays.asList("4345771566D76038C7FEB43863EC0ADBEA87E4E3", "8F9A9793CB8591147C2EC70566E0CDBD1E01F333",
                "A0917C4147A37007CB54C1CFD295AA940EFDDF62", REPO_SIGNER), d.extraRepoTrusted());
        assertEquals(Arrays.asList("5E210889BBB5C48500E0C4F9C75E985FF8B993B4"), d.extraRepoRevoked());
        assertEquals(REPO_SIGNER, d.extraRepoSigner());
        for (String revoked : d.extraRepoRevoked()) assertFalse(d.extraRepoTrusted().contains(revoked));
        // Only Rolling and the other editions have no such feature.
        assertFalse(distro("garden-arch").hasExtraRepo());
        assertFalse(distro("garden-debian").hasExtraRepo());
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
            assertTrue("ThothTerm Security must override " + key, own.containsKey(key));
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
        assertTrue(consent.contains("SHA\u2011256"));
        assertTrue(consent.contains("not built or verified by F\u2011Droid"));
        assertTrue("the base download says no third-party repository is in it",
                consent.contains("No third-party security repository is included"));
        assertEquals("ThothTerm Security", own.get("application_terminal"));
        assertEquals("ThothTerm Security is running", own.get("service_notify_text"));
        assertTrue(own.get("about_notice").contains(
                "not affiliated with or endorsed by the BlackArch, Arch Linux or Arch Linux ARM projects"));
    }

    /**
     * "BlackArch" is a factual name of the optional repository, never the
     * product: it must not appear in the app's name, label, title or icon text,
     * and never next to "Edition".
     */
    @Test
    public void blackArchIsNeverTheProductName() throws Exception {
        Map<String, String> own = strings(new File(moduleDir(), "src/main/res/values/strings.xml"));
        for (String key : new String[]{"application_terminal", "about_title", "garden_welcome", "service_notify_text"}) {
            assertFalse(key, own.get(key).toLowerCase(java.util.Locale.ROOT).contains("blackarch"));
        }
        assertFalse(read(new File(moduleDir(), "fastlane/metadata/android/en-US/title.txt"))
                .toLowerCase(java.util.Locale.ROOT).contains("blackarch"));
        assertFalse(read(new File(moduleDir(), "build.gradle")).toLowerCase(java.util.Locale.ROOT).contains("blackarch"));
        String manifest = read(new File(moduleDir(), "src/main/AndroidManifest.xml"));
        assertFalse(manifest.toLowerCase(java.util.Locale.ROOT).contains("blackarch"));
        for (File f : new File[]{new File(moduleDir(), "src/main/res/values/strings.xml"),
                new File(moduleDir(), "fastlane/metadata/android/en-US/full_description.txt")}) {
            assertFalse(f + " pairs BlackArch with Edition",
                    Pattern.compile("blackarch\\s+edition", Pattern.CASE_INSENSITIVE).matcher(read(f)).find());
        }
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
            assertFalse(f + " claims tools are bundled", text.contains("preinstalled tools")
                    || text.contains("comes with tools") || text.contains("includes the blackarch"));
        }
    }

    /**
     * The measured dependency analysis is quoted with its limit, never as a
     * claim that packages install.
     */
    @Test
    public void theDesignRecordWordsTheDependencyAnalysisCarefully() throws Exception {
        String design = read(new File(repo(), "docs/garden/security/DESIGN.md"));
        assertTrue(design.replace("\n> ", " ").replace("\n", " ").contains(
                "Approximately 94% of the current aarch64 catalog passed the present dependency-resolution "
                        + "analysis; this is not equivalent to successful installation/runtime validation."));
        assertFalse(design.contains("installable"));
        assertTrue(design.contains("UNVERIFIED"));
    }
}
