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
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DistroInfoTest {
    static final String SHA = "d88047a5c2a4b8d6e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b071d1";

    /** A complete, valid distro.properties; tests override single keys. */
    static String valid(String... overrides) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put("editionName", "ThothTerm Garden Test");
        map.put("distroName", "Garden");
        map.put("distroVersion", "1 (test)");
        map.put("distroDir", "garden-test");
        map.put("imageId", "garden-test-arm64-d88047a5c2a4");
        map.put("architecture", "aarch64");
        map.put("sourceUrl", "https://example.org/garden-test-arm64-rootfs-d88047a5c2a4.tar.gz");
        map.put("assetName", "garden-test-arm64-rootfs-d88047a5c2a4.tar.gz");
        map.put("sha256", SHA);
        map.put("compressedSize", "63775639");
        map.put("uncompressedSize", "210000000");
        map.put("schemaVersion", "1");
        map.put("lanPort", "7699");
        map.put("sudoBinary", "usr/bin/sudo");
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i + 1] == null) map.remove(overrides[i]);
            else map.put(overrides[i], overrides[i + 1]);
        }
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            text.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return text.toString();
    }

    static DistroInfo load(String text) throws IOException {
        return DistroInfo.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parsesAnEdition() throws Exception {
        DistroInfo info = load(valid());

        assertEquals("ThothTerm Garden Test", info.editionName());
        assertEquals("Garden", info.distroName());
        assertEquals("1 (test)", info.distroVersion());
        assertEquals("garden-test", info.distroDir());
        assertEquals("garden-test-arm64-d88047a5c2a4", info.imageId());
        assertEquals("aarch64", info.architecture());
        assertEquals("garden-test-arm64-rootfs-d88047a5c2a4.tar.gz", info.assetName());
        assertEquals(SHA, info.sha256());
        assertEquals(63775639L, info.compressedSize());
        assertEquals(210000000L, info.uncompressedSize());
        assertEquals(1, info.schemaVersion());
        assertEquals(7699, info.lanPort());
        assertEquals("usr/bin/sudo", info.sudoBinary());
        // Unset, an edition is a dpkg guest with Debian's admin group.
        assertEquals(DistroInfo.DPKG, info.packageManager());
        assertEquals("sudo", info.adminGroup());
    }

    @Test
    public void parsesAPacmanEdition() throws Exception {
        DistroInfo info = load(valid("packageManager", "pacman", "adminGroup", "wheel",
                "pacmanKeyring", "archlinuxarm",
                "packageSigningKey", "68b3537f39a313b3e574d06777193f152bdbe6a6"));
        assertEquals(DistroInfo.PACMAN, info.packageManager());
        assertEquals("wheel", info.adminGroup());
        assertEquals("archlinuxarm", info.pacmanKeyring());
        assertEquals("68B3537F39A313B3E574D06777193F152BDBE6A6", info.packageSigningKey());
    }

    private static final String ALARM_KEY = "68B3537F39A313B3E574D06777193F152BDBE6A6";
    private static final String BLACKARCH_KEY = "F9A6E68A711354D84A9B91637533BAFE69A25079";

    /** A single value is a list of one: the first-entry accessors are what they always were. */
    @Test
    public void aSingleKeyringIsAListOfOne() throws Exception {
        DistroInfo info = load(valid("packageManager", "pacman", "adminGroup", "wheel",
                "pacmanKeyring", "archlinuxarm", "packageSigningKey", ALARM_KEY.toLowerCase(java.util.Locale.ROOT)));
        assertEquals(java.util.Collections.singletonList("archlinuxarm"), info.pacmanKeyrings());
        assertEquals(java.util.Collections.singletonList(ALARM_KEY), info.packageSigningKeys());
        assertEquals("archlinuxarm", info.pacmanKeyring());
        assertEquals(ALARM_KEY, info.packageSigningKey());
    }

    /** An edition may trust its base distribution and one more repository. */
    @Test
    public void anEditionMayDeclareSeveralKeyringsAndSigningKeys() throws Exception {
        DistroInfo info = load(valid("packageManager", "pacman", "adminGroup", "wheel",
                "pacmanKeyring", "archlinuxarm  blackarch",
                "packageSigningKey", ALARM_KEY + " " + BLACKARCH_KEY.toLowerCase(java.util.Locale.ROOT)));
        assertEquals(java.util.Arrays.asList("archlinuxarm", "blackarch"), info.pacmanKeyrings());
        assertEquals(java.util.Arrays.asList(ALARM_KEY, BLACKARCH_KEY), info.packageSigningKeys());
        // The singular accessors name the base distribution, first.
        assertEquals("archlinuxarm", info.pacmanKeyring());
        assertEquals(ALARM_KEY, info.packageSigningKey());
    }

    @Test
    public void theTrustAnchorListsCannotBeChangedAfterLoading() throws Exception {
        DistroInfo info = load(valid("packageManager", "pacman", "pacmanKeyring", "a b",
                "packageSigningKey", ALARM_KEY + " " + BLACKARCH_KEY));
        try {
            info.packageSigningKeys().add(ALARM_KEY.replace('6', '7'));
            fail("the list is writable");
        } catch (UnsupportedOperationException expected) {
            // read-only
        }
    }

    /** Every entry is checked on its own, and a list never weakens the single-value rules. */
    @Test
    public void refusesMalformedTrustAnchorLists() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 9; i++) many.append("k").append(i).append(' ');
        String[][] bad = {
                {"pacmanKeyring", "archlinuxarm blackarch ../x", "packageSigningKey", ALARM_KEY},
                {"pacmanKeyring", "archlinuxarm; blackarch", "packageSigningKey", ALARM_KEY},
                {"pacmanKeyring", "archlinuxarm -blackarch", "packageSigningKey", ALARM_KEY},
                {"pacmanKeyring", "archlinuxarm archlinuxarm", "packageSigningKey", ALARM_KEY},
                {"pacmanKeyring", many.toString().trim(), "packageSigningKey", ALARM_KEY},
                {"pacmanKeyring", "archlinuxarm blackarch", "packageSigningKey", ALARM_KEY + " 77193F152BDBE6A6"},
                {"pacmanKeyring", "archlinuxarm blackarch", "packageSigningKey", ALARM_KEY + " " + ALARM_KEY.toLowerCase(java.util.Locale.ROOT)},
                {"pacmanKeyring", "archlinuxarm blackarch", "packageSigningKey", ALARM_KEY + " " + BLACKARCH_KEY + "' ; x '"},
                {"pacmanKeyring", "archlinuxarm blackarch", "packageSigningKey", ALARM_KEY + BLACKARCH_KEY},
        };
        for (String[] o : bad) {
            try {
                load(valid("packageManager", "pacman", o[0], o[1], o[2], o[3]));
                fail("accepted " + java.util.Arrays.toString(o));
            } catch (IOException expected) {
                // fail closed
            }
        }
    }

    /** A dpkg edition has no trust anchors to declare, and says so. */
    @Test
    public void aDpkgEditionHasNoKeyrings() throws Exception {
        DistroInfo info = load(valid());
        assertEquals(java.util.Collections.emptyList(), info.pacmanKeyrings());
        assertEquals(java.util.Collections.emptyList(), info.packageSigningKeys());
        assertEquals("", info.pacmanKeyring());
        assertEquals("", info.packageSigningKey());
    }

    @Test
    public void digestIsNormalizedToLowerCase() throws Exception {
        assertEquals(SHA, load(valid("sha256", SHA.toUpperCase(java.util.Locale.ROOT))).sha256());
    }

    /** A pacman edition must say which keyring and key its first run proves. */
    @Test
    public void refusesAPacmanEditionWithoutItsTrustAnchors() {
        String key = "68B3537F39A313B3E574D06777193F152BDBE6A6";
        String[][] bad = {
                {"pacmanKeyring", null, "packageSigningKey", key},
                {"pacmanKeyring", "../x", "packageSigningKey", key},
                {"pacmanKeyring", "archlinuxarm; rm -rf /", "packageSigningKey", key},
                {"pacmanKeyring", "archlinuxarm", "packageSigningKey", null},
                {"pacmanKeyring", "archlinuxarm", "packageSigningKey", "77193F152BDBE6A6"},
                {"pacmanKeyring", "archlinuxarm", "packageSigningKey", key + "' ; x '"},
        };
        for (String[] o : bad) {
            try {
                load(valid("packageManager", "pacman", o[0], o[1], o[2], o[3]));
                fail("accepted " + java.util.Arrays.toString(o));
            } catch (IOException expected) {
                // fail closed
            }
        }
    }

    /** Anything the download check or the filesystem relies on must be present and sane. */
    @Test
    public void refusesIncompleteOrUnsafeMetadata() {
        String[][] bad = {
                {"sha256", null}, {"sha256", "abc"}, {"sha256", SHA + "0"},
                {"compressedSize", null}, {"compressedSize", "0"}, {"compressedSize", "-5"},
                {"uncompressedSize", "x"},
                {"sourceUrl", "http://example.org/rootfs.tar.gz"}, {"sourceUrl", null},
                {"distroDir", "../escape"}, {"distroDir", "a/b"}, {"distroDir", ""},
                {"assetName", "../../x.tar.gz"}, {"assetName", "a/b.tar.gz"},
                {"imageId", null},
                {"editionName", null}, {"editionName", "Two\\nlines"},
                {"distroVersion", "red\\u001b[31m"}, {"distroName", "a\\u0007b"},
                {"lanPort", "80"}, {"lanPort", "70000"}, {"lanPort", null},
                {"sudoBinary", "/usr/bin/sudo"}, {"sudoBinary", "usr/../../etc/passwd"},
                {"packageManager", "rpm"}, {"packageManager", "PACMAN"},
                {"adminGroup", "wheel;rm"}, {"adminGroup", "Wheel"}, {"adminGroup", "a b"},
        };
        for (String[] override : bad) {
            try {
                load(valid(override[0], override[1]));
                fail("accepted " + override[0] + "=" + override[1]);
            } catch (IOException expected) {
                // fail closed
            }
        }
    }
}
