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

import com.thothterm.linux.DistroInfo;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The edition's identity and its rootfs pin. */
public class DebianEditionTest {
    static File moduleDir() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "src/main/assets/garden").isDirectory()) return here;
        return new File(here, "garden-debian");
    }

    static DistroInfo distro() throws Exception {
        return DistroInfo.load(new FileInputStream(
                new File(moduleDir(), "src/main/assets/garden/distro.properties")));
    }

    @Test
    public void identity() throws Exception {
        DistroInfo d = distro();
        assertEquals("ThothTerm Trixie", d.editionName());
        assertEquals("Debian", d.distroName());
        // The major release and codename only: the point release changes with
        // every apt upgrade, and the welcome banner reads it from the guest.
        assertEquals("13 (trixie)", d.distroVersion());
        assertEquals("debian-trixie", d.distroDir());
        assertEquals("aarch64", d.architecture());
        // ThothTerm Ubuntu listens on 7681; the two run side by side.
        assertEquals(7682, d.lanPort());
        assertEquals("usr/bin/sudo", d.sudoBinary());
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
        String published = "thothterm-debian-trixie-arm64-rootfs-" + prefix;
        assertEquals("https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/"
                        + "debian-rootfs-trixie-arm64-" + prefix + "/" + published + ".tar.gz",
                d.sourceUrl());
        // .tgz, because the asset packager would expand a *.gz asset.
        assertEquals(published + ".tgz", d.assetName());
        assertEquals("debian-trixie-arm64-" + prefix, d.imageId());
        assertTrue(d.uncompressedSize() > d.compressedSize());
    }

    /** Must never match what this edition's F-Droid UpdateCheckMode looks for. */
    @Test
    public void theRootfsTagIsNotAnAppReleaseTag() throws Exception {
        Matcher m = Pattern.compile("/releases/download/([^/]+)/").matcher(distro().sourceUrl());
        assertTrue(m.find());
        assertFalse(m.group(1).matches("^trixie-v[0-9.]+$"));
    }

    /** A rootfs build recorded next to the source must agree with the pin. */
    @Test
    public void provenanceRecordsThePinnedArchive() throws Exception {
        DistroInfo d = distro();
        File doc = new File(moduleDir().getParentFile(), "docs/garden/debian/ROOTFS_PROVENANCE.md");
        String text = new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8);
        assertTrue("provenance must record the sha256", text.contains(d.sha256()));
        assertTrue("provenance must record the size", text.contains(Long.toString(d.compressedSize())));
    }

    /** Every garden-common default that names the product or the distribution. */
    private static final String[] EDITION_STRINGS = {
            "application_terminal", "application_positioning", "about_title", "about_notice",
            "garden_welcome", "garden_extracting_percent", "garden_downloading",
            "garden_consent_title", "garden_consent_body", "garden_consent_accept",
            "garden_consent_declined", "service_notify_text", "lan_help_off",
    };

    private static java.util.Map<String, String> strings(File file) throws Exception {
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        java.util.Map<String, String> map = new java.util.HashMap<>();
        Matcher m = Pattern.compile("<string name=\"([a-z_]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL)
                .matcher(xml);
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    @Test
    public void theEditionNamesItselfEverywhere() throws Exception {
        java.util.Map<String, String> own = strings(new File(moduleDir(), "src/main/res/values/strings.xml"));
        java.util.Map<String, String> common = strings(new File(moduleDir().getParentFile(),
                "garden-common/src/main/res/values/strings.xml"));
        common.putAll(strings(new File(moduleDir().getParentFile(),
                "garden-common/src/main/res/values/strings_lan.xml")));
        for (String key : EDITION_STRINGS) {
            assertTrue("garden-common no longer defines " + key, common.containsKey(key));
            assertTrue("ThothTerm Trixie must override " + key, own.containsKey(key));
        }
        for (java.util.Map.Entry<String, String> e : own.entrySet()) {
            assertFalse(e.getKey() + " names another edition", e.getValue().contains("Ubuntu"));
        }
        String consent = own.get("garden_consent_body");
        for (String arg : new String[]{"%1$s", "%2$d", "%3$s"}) {
            assertTrue("consent text lost " + arg, consent.contains(arg));
        }
        assertEquals("ThothTerm Trixie", own.get("application_terminal"));
        assertEquals("ThothTerm Trixie is running", own.get("service_notify_text"));
        assertTrue(own.get("about_notice").contains(
                "not affiliated with or endorsed by the Debian Project"));
    }
}
