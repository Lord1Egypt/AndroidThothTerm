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

import static com.thothterm.blackarch.RootfsTools.available;
import static com.thothterm.blackarch.RootfsTools.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.thothterm.linux.DistroInfo;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The reviewed trust pins agree with the edition, and the builder cannot be told to weaken them. */
public class BlackArchRootfsPinsTest {
    /** The four BlackArch signing keys in strap.sh's KEYRING_SIGNERS (measured 2026-10-03). */
    private static final List<String> STRAP_SIGNERS = Arrays.asList(
            "4345771566D76038C7FEB43863EC0ADBEA87E4E3",
            "8F9A9793CB8591147C2EC70566E0CDBD1E01F333",
            "A0917C4147A37007CB54C1CFD295AA940EFDDF62",
            "F9A6E68A711354D84A9B91637533BAFE69A25079");

    private static File rootfs() {
        return new File(BlackArchEditionTest.moduleDir(), "rootfs");
    }

    private static Map<String, String> pins() throws Exception {
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("(?m)^([A-Z0-9_]+)=(.*)$")
                .matcher(BlackArchEditionTest.read(new File(rootfs(), "keyring.pins")));
        while (m.find()) {
            String v = m.group(2).trim();
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            map.put(m.group(1), v);
        }
        return map;
    }

    private static String code(String file) throws Exception {
        return BlackArchEditionTest.read(new File(rootfs(), file)).replaceAll("(?m)^\\s*#.*$", "");
    }

    @Test
    public void thePinnedKeysFileIsThePinnedBytes() throws Exception {
        Map<String, String> p = pins();
        assertEquals(p.get("KEYRING_GPG_SHA256"), RootfsTools.sha256(new File(rootfs(), "blackarch.gpg")));
        assertEquals("50a08f82ce3cd524a552da4bfa37f3e04b2c9da468e2fce5783474b58a34c518",
                RootfsTools.sha256(new File(rootfs(), "archlinuxarm.gpg")));
        assertEquals(RootfsTools.sha256(new File(rootfs(), "blackarch-repo.conf")), p.get("BLACKARCH_REPO_CONF_SHA256"));
    }

    @Test
    public void thePinnedKeysFileHoldsExactlyThePinnedKeys() throws Exception {
        assumeTrue("gpg is required", available("gpg"));
        File home = Files.createTempDirectory("pins-gpg").toFile();
        try {
            RootfsTools.Result r = run(home, null, "gpg", "--batch", "--with-colons", "--show-keys",
                    new File(rootfs(), "blackarch.gpg").getPath());
            assertEquals(r.output, 0, r.exit);
            java.util.List<String> fprs = new java.util.ArrayList<>();
            boolean primary = false;
            for (String line : r.output.split("\n")) {
                if (line.startsWith("pub:")) primary = true;
                else if (line.startsWith("fpr:") && primary) {
                    fprs.add(line.split(":")[9]);
                    primary = false;
                }
            }
            java.util.Collections.sort(fprs);
            assertEquals(pins().get("KEYRING_ALL_KEYS"), String.join(" ", fprs));
        } finally {
            RootfsTools.delete(home);
        }
    }

    /** The trusted keys are the four strap.sh trusts; the signer is the one the edition declares. */
    @Test
    public void theTrustAnchorsAgreeWithTheEditionAndWithBlackArchsOwnScript() throws Exception {
        Map<String, String> p = pins();
        assertEquals(STRAP_SIGNERS, Arrays.asList(p.get("KEYRING_TRUSTED").split(" ")));
        DistroInfo d = BlackArchEditionTest.distro();
        assertEquals(Arrays.asList("68B3537F39A313B3E574D06777193F152BDBE6A6", p.get("KEYRING_SIGNER")), d.packageSigningKeys());
        assertTrue(Arrays.asList(p.get("KEYRING_TRUSTED").split(" ")).contains(p.get("KEYRING_SIGNER")));
        assertEquals("5E210889BBB5C48500E0C4F9C75E985FF8B993B4", p.get("KEYRING_REVOKED"));
        for (String key : p.get("KEYRING_ALL_KEYS").split(" ")) assertTrue(key, key.matches("[0-9A-F]{40}"));
        assertEquals(7, p.get("KEYRING_ALL_KEYS").split(" ").length);
    }

    @Test
    public void theKeyringPackageIsPinnedByVersionAndByDigest() throws Exception {
        Map<String, String> p = pins();
        assertEquals("20251011-2", p.get("KEYRING_VERSION"));
        assertEquals("blackarch-keyring-20251011-2-any.pkg.tar.zst", p.get("KEYRING_FILE"));
        for (String key : new String[]{"KEYRING_URL", "KEYRING_SIG_URL"}) {
            String url = p.get(key);
            assertTrue(key, url.startsWith("https://blackarch.org/blackarch/blackarch/os/aarch64/blackarch-keyring-20251011-2-any.pkg.tar.zst"));
            assertFalse(key + " floats", url.contains("latest") || url.contains("*"));
        }
        for (String key : new String[]{"KEYRING_SHA256", "KEYRING_SIG_SHA256", "KEYRING_GPG_SHA256", "KEYRING_TRUSTED_SHA256",
                "KEYRING_REVOKED_SHA256", "KEYRING_HOOK_SHA256", "KEYRING_INSTALL_SHA256", "BLACKARCH_REPO_CONF_SHA256"}) {
            assertTrue(key, p.get(key).matches("[0-9a-f]{64}"));
        }
        assertTrue(Long.parseLong(p.get("KEYRING_SIZE")) > 0);
        assertEquals("'https://blackarch.org/blackarch/$repo/os/$arch'".replace("'", ""), p.get("BLACKARCH_SERVER"));
    }

    @Test
    public void theRepositorySectionAddsOnlyAServer() throws Exception {
        String conf = code("blackarch-repo.conf");
        assertEquals("[blackarch]\nServer = https://blackarch.org/blackarch/$repo/os/$arch\n", conf.replaceAll("(?m)^\\s*\\n", ""));
        assertFalse(conf.contains("SigLevel"));
    }

    /** The builder source: no shortcut around the pins, the signatures or the policy check. */
    @Test
    public void theBuilderCannotWeakenTrustAndNeverRunsStrapSh() throws Exception {
        String builder = code("build-rootfs.sh");
        for (String bad : new String[]{"SigLevel = Never", "SigLevel=Never", "TrustAll", "--nodeps", "--force",
                "--skippgpcheck", "--nosig", "--no-check-certificate", "curl -k", "curl --insecure", "gpg --trust-model always",
                "LocalFileSigLevel = Optional", "strap.sh"}) {
            String stripped = builder.replace("Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll", "");
            assertFalse("the builder contains " + bad, stripped.contains(bad));
        }
        assertTrue(builder.contains("verify-keyring.sh\" \"$INPUTS/blackarch\""));
        assertTrue("the keyring is installed with a required local-file signature",
                builder.contains("LocalFileSigLevel = Required"));
        assertTrue(builder.contains("--noscriptlet"));
        assertTrue("the final pacman.conf is validated", builder.contains("check-pacman-conf.sh $T/etc/pacman.conf"));
        assertTrue(builder.contains("cat /builder/blackarch-repo.conf >> $T/etc/pacman.conf"));
        assertTrue("no pacman keyring ships", builder.contains("test ! -e $T/etc/pacman.d/gnupg"));
        assertTrue("the base capture is verified before anything is built", builder.contains("sha256sum --quiet -c inputs.sha256"));
        assertFalse("the mirror list package is not installed", builder.contains("blackarch-mirrorlist"));
        assertFalse("no repository package beyond the keyring", Pattern.compile("pacman[^\\n]*-S[^\\n]*blackarch").matcher(builder).find());
        assertTrue("never a partial upgrade", !Pattern.compile("pacman[^\\n]*-Sy\\s+[a-z]").matcher(builder).find());
        // The check script is the only place the weak level names may be listed.
        String conf = BlackArchEditionTest.read(new File(rootfs(), "check-pacman-conf.sh"));
        assertTrue(conf.contains("Never|Optional|TrustAll"));
    }

    /** The initial image stays narrow: Rolling's five packages and nothing from BlackArch. */
    @Test
    public void thePackageListIsNarrow() throws Exception {
        List<String> packages = Arrays.asList(code("packages.txt").trim().split("\\s+"));
        assertEquals(Arrays.asList("base", "archlinuxarm-keyring", "sudo", "ca-certificates", "curl"), packages);
        assertEquals(BlackArchEditionTest.read(new File(BlackArchEditionTest.repo(), "garden-arch/rootfs/packages.txt"))
                        .replaceAll("(?m)^#.*$", "").trim(),
                BlackArchEditionTest.read(new File(rootfs(), "packages.txt")).replaceAll("(?m)^#.*$", "").trim());
    }

    /** The builder is a copy of Rolling's with named differences; Rolling's own files are untouched here. */
    @Test
    public void theTrustFilesOfRollingAreNotShared() throws Exception {
        assertEquals(RootfsTools.sha256(new File(BlackArchEditionTest.repo(), "garden-arch/rootfs/archlinuxarm.gpg")),
                RootfsTools.sha256(new File(rootfs(), "archlinuxarm.gpg")));
        assertFalse(new File(BlackArchEditionTest.repo(), "garden-arch/rootfs/blackarch.gpg").exists());
        assertFalse(BlackArchEditionTest.read(new File(BlackArchEditionTest.repo(), "garden-arch/rootfs/build-rootfs.sh"))
                .contains("blackarch"));
    }
}
