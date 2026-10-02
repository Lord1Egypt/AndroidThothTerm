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

import com.thothterm.linux.DistroInfo;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The edition's identity, its rootfs pin and the two tracks. */
public class ArchEditionTest {
    static File moduleDir() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "src/main/assets/garden").isDirectory()) return here;
        return new File(here, "garden-arch");
    }

    static File repo() {
        return moduleDir().getParentFile();
    }

    static DistroInfo distro() throws Exception {
        return DistroInfo.load(new FileInputStream(
                new File(moduleDir(), "src/main/assets/garden/distro.properties")));
    }

    static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void identity() throws Exception {
        DistroInfo d = distro();
        // The neutral name: docs/branding/arch/TRADEMARK.md.
        assertEquals("ThothTerm Rolling", d.editionName());
        assertEquals("Arch Linux ARM", d.distroName());
        assertEquals("arch-aarch64", d.distroDir());
        assertEquals("aarch64", d.architecture());
        assertEquals("usr/bin/sudo", d.sudoBinary());
        String gradle = read(new File(moduleDir(), "build.gradle"));
        assertTrue(gradle.contains("applicationId \"com.thothterm.arch\""));
        assertTrue(gradle.contains("namespace = \"com.thothterm.arch\""));
    }

    /** Ubuntu is 7681 and Trixie 7682; the Garden port registry is docs/garden/PORTS.md. */
    @Test
    public void lanPortIsTheRegistrysNext() throws Exception {
        assertEquals(7683, distro().lanPort());
        for (String[] other : new String[][]{{"garden-debian", "7682"}}) {
            String props = read(new File(repo(), other[0] + "/src/main/assets/garden/distro.properties"));
            assertTrue(other[0] + " keeps its port", props.contains("\nlanPort=" + other[1] + "\n"));
        }
        assertTrue(read(new File(repo(), "docs/garden/PORTS.md")).contains("| 7683 |"));
    }

    /** pacman, the wheel group, and the anchors the first run proves its keyring against. */
    @Test
    public void thePackageManagerIsPacman() throws Exception {
        DistroInfo d = distro();
        assertEquals(DistroInfo.PACMAN, d.packageManager());
        assertEquals("wheel", d.adminGroup());
        assertEquals("archlinuxarm", d.pacmanKeyring());
        // The Arch Linux ARM Build System key, as archlinuxarm.org publishes it.
        assertEquals("68B3537F39A313B3E574D06777193F152BDBE6A6", d.packageSigningKey());
        String builder = read(new File(moduleDir(), "rootfs/build-rootfs.sh"));
        assertTrue(builder.contains("SIGNING_KEY=\"" + d.packageSigningKey() + "\""));
        String packages = read(new File(moduleDir(), "rootfs/packages.txt"));
        assertTrue("the keyring the first run populates must be installed",
                packages.contains("\narchlinuxarm-keyring\n"));
    }

    /** Signature checks are never switched off, in the builder or the image. */
    @Test
    public void theBuilderNeverWeakensSignatureChecking() throws Exception {
        String builder = read(new File(moduleDir(), "rootfs/build-rootfs.sh"));
        // The one place the weak level names may appear: the check that
        // refuses them in the shipped pacman.conf, globally and per repository.
        String refuseWeak = "grep -Eqx \"Never|Optional|TrustAll|PackageNever|PackageOptional"
                + "|PackageTrustAll\" /tmp/siglevels.shipped";
        assertTrue("the shipped signature levels are checked", builder.contains(refuseWeak));
        assertTrue(builder.contains("$C SigLevel | tr \" \" \"\\n\" | grep -qx Required"));
        String code = builder.replaceAll("(?m)^\\s*#.*$", "").replace(refuseWeak, "");
        assertFalse(code.contains("SigLevel = Never"));
        assertFalse(code.contains("TrustAll"));
        assertFalse(code.contains("--nodeps"));
        assertFalse("never a partial upgrade", Pattern.compile("pacman[^\\n]*-Sy\\s+[a-z]").matcher(code).find());
        assertTrue("the image ships no keyring", code.contains("test ! -e $T/etc/pacman.d/gnupg"));
        assertTrue("nothing may be left to upgrade", code.contains("there is nothing to do"));
    }

    /**
     * Android kernels have no Landlock, so pacman's filesystem sandbox is the
     * one layer the image turns off; the seccomp filter and the unprivileged
     * DownloadUser must stay on.
     */
    @Test
    public void theImageTurnsOffOnlyTheSandboxLayerAndroidLacks() throws Exception {
        String builder = read(new File(moduleDir(), "rootfs/build-rootfs.sh"));
        assertTrue(builder.contains("grep -qx \"DisableSandboxFilesystem\" $T/etc/pacman.conf"));
        assertTrue(builder.contains("grep -qx \"#DisableSandboxSyscalls\" $T/etc/pacman.conf"));
        assertTrue(builder.contains("grep -q \"^DownloadUser = alpm\" $T/etc/pacman.conf"));
        assertFalse(builder.replaceAll("(?m)^\\s*#.*$", "").contains("s/^#DisableSandboxSyscalls"));
    }

    /**
     * The archive is content-addressed: its name carries the first 12 hex
     * digits of its SHA-256, in the published file, the embedded asset and the
     * release it is published under.
     */
    @Test
    public void theRootfsPinIsContentAddressed() throws Exception {
        DistroInfo d = distro();
        String prefix = d.sha256().substring(0, 12);
        String published = "thothterm-arch-aarch64-rootfs-" + prefix;
        assertEquals("https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/"
                        + "arch-rootfs-aarch64-" + prefix + "/" + published + ".tar.gz",
                d.sourceUrl());
        // .tgz, because the asset packager would expand a *.gz asset.
        assertEquals(published + ".tgz", d.assetName());
        assertEquals("arch-aarch64-" + prefix, d.imageId());
        assertTrue(d.uncompressedSize() > d.compressedSize());
    }

    /** Must never match what this edition's F-Droid UpdateCheckMode looks for. */
    @Test
    public void theRootfsTagIsNotAnAppReleaseTag() throws Exception {
        Matcher m = Pattern.compile("/releases/download/([^/]+)/").matcher(distro().sourceUrl());
        assertTrue(m.find());
        assertFalse(m.group(1).matches("^arch-v[0-9.]+$"));
    }

    /** A rootfs build recorded next to the source must agree with the pin. */
    @Test
    public void provenanceRecordsThePinnedArchive() throws Exception {
        DistroInfo d = distro();
        String text = read(new File(repo(), "docs/garden/arch/ROOTFS_PROVENANCE.md"));
        assertTrue("provenance must record the sha256", text.contains(d.sha256()));
        assertTrue("provenance must record the size", text.contains(Long.toString(d.compressedSize())));
    }

    /**
     * Only the full flavour stages the rootfs; the fdroid flavour must not
     * depend on it, and the staging refuses anything but the pinned bytes.
     */
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

    /** Every garden-common default that names the product or the distribution. */
    private static final String[] EDITION_STRINGS = {
            "application_terminal", "application_positioning", "about_title", "about_notice",
            "garden_welcome", "garden_extracting_percent", "garden_downloading",
            "garden_consent_title", "garden_consent_body", "garden_consent_accept",
            "garden_consent_declined", "service_notify_text", "lan_help_off",
    };

    private static java.util.Map<String, String> strings(File file) throws Exception {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        Matcher m = Pattern.compile("<string name=\"([a-z_]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL)
                .matcher(read(file));
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    @Test
    public void theEditionNamesItselfEverywhere() throws Exception {
        java.util.Map<String, String> own = strings(new File(moduleDir(), "src/main/res/values/strings.xml"));
        java.util.Map<String, String> common = strings(new File(repo(), "garden-common/src/main/res/values/strings.xml"));
        common.putAll(strings(new File(repo(), "garden-common/src/main/res/values/strings_lan.xml")));
        for (String key : EDITION_STRINGS) {
            assertTrue("garden-common no longer defines " + key, common.containsKey(key));
            assertTrue("ThothTerm Rolling must override " + key, own.containsKey(key));
        }
        for (java.util.Map.Entry<String, String> e : own.entrySet()) {
            for (String other : new String[]{"Ubuntu", "Debian", "Trixie"}) {
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
        assertEquals("ThothTerm Rolling", own.get("application_terminal"));
        assertEquals("ThothTerm Rolling is running", own.get("service_notify_text"));
        assertTrue(own.get("about_notice").contains(
                "not affiliated with or endorsed by the Arch Linux or Arch Linux ARM projects"));
    }

    /** The trademark decision: no user-visible "ThothTerm Arch", nothing official. */
    @Test
    public void noUserVisibleTextUsesArchAsTheProductName() throws Exception {
        java.util.List<File> visible = new java.util.ArrayList<>();
        visible.add(new File(moduleDir(), "src/main/res/values/strings.xml"));
        visible.add(new File(moduleDir(), "src/main/assets/garden/distro.properties"));
        File fastlane = new File(moduleDir(), "fastlane/metadata/android/en-US");
        for (String name : new String[]{"title.txt", "short_description.txt", "full_description.txt"}) {
            visible.add(new File(fastlane, name));
        }
        File[] changelogs = new File(fastlane, "changelogs").listFiles();
        assertTrue(changelogs != null && changelogs.length > 0);
        visible.addAll(java.util.Arrays.asList(changelogs));
        for (File f : visible) {
            String text = read(f).replaceAll("(?m)^#.*$", "").replaceAll("(?s)<!--.*?-->", "");
            assertFalse(f + " calls the product ThothTerm Arch", text.contains("ThothTerm Arch"));
            for (String word : new String[]{"official", "certified", "endorsed by Arch", "supported by Arch"}) {
                assertFalse(f + " says " + word, text.toLowerCase(java.util.Locale.ROOT)
                        .replace("not affiliated with or endorsed by", "").contains(word.toLowerCase(java.util.Locale.ROOT)));
            }
        }
        assertEquals("ThothTerm Rolling", read(new File(fastlane, "title.txt")).trim());
    }
}
