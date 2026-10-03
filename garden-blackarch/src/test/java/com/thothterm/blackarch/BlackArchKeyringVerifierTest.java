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
import static com.thothterm.blackarch.RootfsTools.ok;
import static com.thothterm.blackarch.RootfsTools.run;
import static com.thothterm.blackarch.RootfsTools.sha256;
import static com.thothterm.blackarch.RootfsTools.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * verify-keyring.sh on a synthetic keyring package signed with throwaway
 * keys: the builder accepts exactly what keyring.pins describes and refuses
 * everything else. (The real package is checked against the real pins by the
 * builder itself and by tests/garden-blackarch/host/validate-rootfs.sh.)
 */
public class BlackArchKeyringVerifierTest {
    private File work;
    private File gnupg;
    private final Map<String, String> env = new HashMap<>();
    private String signer;     // trusted, signs the package
    private String second;     // trusted
    private String untrusted;  // in the keyring, not trusted
    private String revoked;    // listed as revoked
    private String stranger;   // never part of the pinned set
    private String other;      // a signer nobody pinned

    @Before
    public void keys() throws Exception {
        assumeTrue("gpg, gpgv, tar, zstd and sh are required", available("gpg", "gpgv", "tar", "zstd", "sh"));
        work = Files.createTempDirectory("verify-keyring").toFile();
        gnupg = new File(work, "gnupg");
        assertTrue(gnupg.mkdirs());
        run(null, null, "chmod", "700", gnupg.getPath());
        env.put("GNUPGHOME", gnupg.getPath());
        signer = key("Test Signer");
        second = key("Test Second");
        untrusted = key("Test Untrusted");
        revoked = key("Test Revoked");
        stranger = key("Test Stranger");
        other = key("Test Other Signer");
    }

    @After
    public void clean() {
        if (work != null) RootfsTools.delete(work);
    }

    private String key(String name) throws Exception {
        // gpg refuses a signature whose key is dated "in the future": WSL clocks jitter by a second or two.
        for (int attempt = 0; ; attempt++) {
            RootfsTools.Result r = run(work, env, "gpg", "--batch", "--passphrase", "", "--quick-generate-key",
                    name + " <t@example.org>", "ed25519", "sign", "never");
            if (r.exit == 0) break;
            if (attempt >= 4 || !r.output.contains("in the future")) throw new IllegalStateException(r.output);
            Thread.sleep(2500);
            run(work, env, "gpg", "--batch", "--yes", "--delete-secret-and-public-keys", name);
        }
        String out = ok(work, env, "gpg", "--batch", "--with-colons", "--list-keys", name).output;
        for (String line : out.split("\n")) {
            if (line.startsWith("fpr:")) return line.split(":")[9];
        }
        throw new IllegalStateException(out);
    }

    /** What a mutation changes; everything else is the faithful package. */
    private static final class Variant {
        String signWith;               // default: signer
        String pinnedSigner;           // default: signer
        String extraTrustedLine;       // an extra line in blackarch-trusted
        String extraKeyInGpg;          // an extra key in blackarch.gpg (and the pinned copy)
        String revokedLine;            // default: revoked
        boolean tamperAfterPinning;    // alter the package once the pins are written
        boolean dropSignature;
        boolean changePinnedKeysFile;  // alter blackarch.gpg beside the pins afterwards
        boolean changeHookAfterPinning;
        String extraFile;              // an unexpected file in the package
    }

    private File build(Variant v) throws Exception {
        File dir = new File(work, "case" + System.nanoTime());
        File inputs = new File(dir, "inputs");
        File pkgdir = new File(dir, "pkg");
        File pins = new File(dir, "pins/keyring.pins");
        assertTrue(inputs.mkdirs());
        List<String> keys = new ArrayList<>(Arrays.asList(signer, second, untrusted, revoked));
        if (v.extraKeyInGpg != null) keys.add(v.extraKeyInGpg);
        File gpgFile = new File(pkgdir, "usr/share/pacman/keyrings/blackarch.gpg");
        gpgFile.getParentFile().mkdirs();
        Files.write(gpgFile.toPath(), exportBytes(keys));
        write(new File(pkgdir, "usr/share/pacman/keyrings/blackarch-trusted"),
                signer + ":4:\n" + second + ":4:\n" + (v.extraTrustedLine == null ? "" : v.extraTrustedLine + "\n"));
        write(new File(pkgdir, "usr/share/pacman/keyrings/blackarch-revoked"),
                (v.revokedLine == null ? revoked : v.revokedLine) + "\n");
        write(new File(pkgdir, "etc/pacman.d/hooks/blackarch-key.hook"), "[Trigger]\n");
        write(new File(pkgdir, ".INSTALL"), "post_install() { :; }\n");
        write(new File(pkgdir, ".PKGINFO"), "pkgname = blackarch-keyring\n");
        if (v.extraFile != null) write(new File(pkgdir, v.extraFile), "x\n");
        File pinnedKeys = new File(pins.getParentFile(), "blackarch.gpg");
        pinnedKeys.getParentFile().mkdirs();
        Files.copy(gpgFile.toPath(), pinnedKeys.toPath());

        File pkg = new File(inputs, "blackarch-keyring-1-1-any.pkg.tar.zst");
        ok(dir, null, "tar", "--zstd", "-cf", pkg.getPath(), "-C", pkgdir.getPath(), ".");
        File sig = new File(inputs, pkg.getName() + ".sig");
        ok(dir, env, "gpg", "--batch", "--detach-sign", "--local-user", v.signWith == null ? signer : v.signWith,
                "-o", sig.getPath(), pkg.getPath());

        List<String> all = new ArrayList<>(Arrays.asList(signer, second, untrusted, revoked));
        Collections.sort(all);
        List<String> trusted = new ArrayList<>(Arrays.asList(signer, second));
        Collections.sort(trusted);
        write(pins, "KEYRING_NAME=blackarch-keyring\nKEYRING_VERSION=1-1\n"
                + "KEYRING_FILE=" + pkg.getName() + "\n"
                + "KEYRING_SIZE=" + pkg.length() + "\n"
                + "KEYRING_SHA256=" + sha256(pkg) + "\n"
                + "KEYRING_SIG_SHA256=" + sha256(sig) + "\n"
                + "KEYRING_SIGNER=" + (v.pinnedSigner == null ? signer : v.pinnedSigner) + "\n"
                + "KEYRING_GPG_SHA256=" + sha256(gpgFile) + "\n"
                + "KEYRING_TRUSTED_SHA256=" + sha256(new File(pkgdir, "usr/share/pacman/keyrings/blackarch-trusted")) + "\n"
                + "KEYRING_REVOKED_SHA256=" + sha256(new File(pkgdir, "usr/share/pacman/keyrings/blackarch-revoked")) + "\n"
                + "KEYRING_HOOK_SHA256=" + sha256(new File(pkgdir, "etc/pacman.d/hooks/blackarch-key.hook")) + "\n"
                + "KEYRING_INSTALL_SHA256=" + sha256(new File(pkgdir, ".INSTALL")) + "\n"
                + "KEYRING_TRUSTED=\"" + String.join(" ", trusted) + "\"\n"
                + "KEYRING_REVOKED=\"" + revoked + "\"\n"
                + "KEYRING_ALL_KEYS=\"" + String.join(" ", all) + "\"\n");

        if (v.tamperAfterPinning) Files.write(pkg.toPath(), "x".getBytes(), java.nio.file.StandardOpenOption.APPEND);
        if (v.dropSignature) assertTrue(sig.delete());
        if (v.changePinnedKeysFile) Files.write(pinnedKeys.toPath(), exportBytes(Arrays.asList(signer, second)));
        if (v.changeHookAfterPinning) {
            // a different hook, packaged anew with the pins left alone: the size changes too, so re-pin only the bytes
            write(new File(pkgdir, "etc/pacman.d/hooks/blackarch-key.hook"), "[Trigger]\nExec = /bin/evil\n");
            File pkg2 = pkg;
            assertTrue(pkg2.delete());
            ok(dir, null, "tar", "--zstd", "-cf", pkg2.getPath(), "-C", pkgdir.getPath(), ".");
            ok(dir, env, "gpg", "--batch", "--yes", "--detach-sign", "--local-user", signer, "-o", sig.getPath(), pkg2.getPath());
            String text = RootfsTools.read(pins)
                    .replaceAll("(?m)^KEYRING_SIZE=.*$", "KEYRING_SIZE=" + pkg2.length())
                    .replaceAll("(?m)^KEYRING_SHA256=.*$", "KEYRING_SHA256=" + sha256(pkg2))
                    .replaceAll("(?m)^KEYRING_SIG_SHA256=.*$", "KEYRING_SIG_SHA256=" + sha256(sig));
            write(pins, text);
        }
        return dir;
    }

    private byte[] exportBytes(List<String> keys) throws Exception {
        File out = new File(work, "export" + System.nanoTime());
        List<String> cmd = new ArrayList<>(Arrays.asList("gpg", "--batch", "--export", "-o", out.getPath()));
        cmd.addAll(keys);
        ok(work, env, cmd.toArray(new String[0]));
        return Files.readAllBytes(out.toPath());
    }

    private RootfsTools.Result verify(File dir) throws Exception {
        File script = new File(BlackArchEditionTest.moduleDir(), "rootfs/verify-keyring.sh");
        return run(dir, env, "sh", script.getPath(), new File(dir, "inputs").getPath(), new File(dir, "pins/keyring.pins").getPath());
    }

    private void refused(Variant v, String why) throws Exception {
        RootfsTools.Result r = verify(build(v));
        assertTrue("accepted: " + why + "\n" + r.output, r.exit != 0);
        assertTrue("no reason given for: " + why + "\n" + r.output, r.output.contains("verify-keyring: FAIL"));
    }

    @Test
    public void acceptsTheFaithfulPackageAndReportsTheKeyThatIsNotTrusted() throws Exception {
        RootfsTools.Result r = verify(build(new Variant()));
        assertEquals(r.output, 0, r.exit);
        assertTrue(r.output, r.output.contains("verify-keyring: OK"));
        assertTrue("an untrusted key in the keyring is reported", r.output.contains("NOTICE: " + untrusted));
        assertTrue(!r.output.contains("NOTICE: " + signer));
    }

    @Test
    public void refusesAnAlteredPackage() throws Exception {
        Variant v = new Variant();
        v.tamperAfterPinning = true;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("package size"));
    }

    @Test
    public void refusesAPackageWhoseHookWasChanged() throws Exception {
        Variant v = new Variant();
        v.changeHookAfterPinning = true;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("hook differs"));
    }

    @Test
    public void refusesAMissingSignature() throws Exception {
        Variant v = new Variant();
        v.dropSignature = true;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("no detached signature"));
    }

    /** The package and its pins are consistent, so only the signature is wrong. */
    @Test
    public void refusesTheWrongSigner() throws Exception {
        Variant v = new Variant();
        v.signWith = other;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("does not verify with " + signer));
    }

    /** Signed by a key the keyring holds but nobody pinned as the signer. */
    @Test
    public void refusesASignerThatIsNotTheOnePinned() throws Exception {
        Variant v = new Variant();
        v.signWith = second;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("FAIL"));
    }

    @Test
    public void refusesAPinnedSignerThatPacmanWouldNotTrust() throws Exception {
        Variant v = new Variant();
        v.signWith = untrusted;
        v.pinnedSigner = untrusted;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("the signer is not a trusted key"));
    }

    @Test
    public void refusesAnUnexpectedExtraTrustedIdentity() throws Exception {
        Variant v = new Variant();
        v.extraTrustedLine = stranger + ":4:";
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("blackarch-trusted lists"));
    }

    @Test
    public void refusesAnUnexpectedExtraKeyInTheKeyring() throws Exception {
        Variant v = new Variant();
        v.extraKeyInGpg = stranger;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("keys in blackarch.gpg"));
    }

    @Test
    public void refusesAChangedRevocationList() throws Exception {
        Variant v = new Variant();
        v.revokedLine = stranger;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("blackarch-revoked lists"));
    }

    @Test
    public void refusesAChangedPinnedKeysFile() throws Exception {
        Variant v = new Variant();
        v.changePinnedKeysFile = true;
        RootfsTools.Result r = verify(build(v));
        assertTrue(r.output, r.exit != 0 && r.output.contains("blackarch.gpg was changed"));
    }

    @Test
    public void refusesAnUnexpectedFileInThePackage() throws Exception {
        Variant v = new Variant();
        v.extraFile = "usr/bin/surprise";
        refused(v, "a file the keyring package must not carry");
    }

    @Test
    public void refusesAMissingPin() throws Exception {
        File dir = build(new Variant());
        File pins = new File(dir, "pins/keyring.pins");
        write(pins, RootfsTools.read(pins).replaceAll("(?m)^KEYRING_SIGNER=.*\\n", ""));
        RootfsTools.Result r = verify(dir);
        assertTrue(r.output, r.exit != 0 && r.output.contains("KEYRING_SIGNER is not pinned"));
    }
}
