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
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The pacman keyring script of the editions: Rolling's is exactly what it was
 * before the multiple-keyring change, and an edition with two signing
 * identities (BlackArch) gets a script that proves both.
 */
public class PacmanKeyringScriptTest {
    private static final String ALARM_KEY = "68B3537F39A313B3E574D06777193F152BDBE6A6";
    private static final String BLACKARCH_KEY = "F9A6E68A711354D84A9B91637533BAFE69A25079";

    /**
     * Rolling's keyring script as RootfsManager produced it at 3b944ca, the
     * revision this change started from: the text of that revision's string
     * concatenation, evaluated, not retyped.
     */
    private static final String ROLLING_GOLDEN =
              "set -e\n"
            + "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n"
            + "export LANG=C.UTF-8\n"
            + "cd /\n"
            + "K=/etc/pacman.d/gnupg\n"
            + "rm -rf \"$K\"\n"
            + "pacman-key --init\n"
            + "pacman-key --populate archlinuxarm\n"
            + "G=\"gpg --homedir $K --no-permission-warning --batch\"\n"
            + "$G --with-colons --list-keys 68B3537F39A313B3E574D06777193F152BDBE6A6 | grep -q '^pub:[fu]:' || { echo 'signing key is not fully valid' >&2; exit 1; }\n"
            + "set -- /usr/share/thothterm/signature-check/*.sig\n"
            + "[ -f \"$1\" ] || { echo 'no signature-check package' >&2; exit 1; }\n"
            + "$G --status-fd 1 --verify \"$1\" \"${1%.sig}\" > /tmp/.thothterm-verify 2>/dev/null || true\n"
            + "grep -q '^\\[GNUPG:\\] VALIDSIG 68B3537F39A313B3E574D06777193F152BDBE6A6 ' /tmp/.thothterm-verify && grep -qE '^\\[GNUPG:\\] TRUST_(FULLY|ULTIMATE)' /tmp/.thothterm-verify || { cat /tmp/.thothterm-verify >&2; rm -f /tmp/.thothterm-verify; exit 1; }\n"
            + "rm -f /tmp/.thothterm-verify\n"
            + "echo keyring-verified\n";

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "garden-arch").isDirectory()) return here;
        return here.getParentFile();
    }

    private static DistroInfo edition(String module) throws Exception {
        try (InputStream in = new FileInputStream(new File(repo(),
                module + "/src/main/assets/garden/distro.properties"))) {
            return DistroInfo.load(in);
        }
    }

    @Test
    public void rollingKeepsItsKeyringPathUnchanged() throws Exception {
        DistroInfo rolling = edition("garden-arch");
        assertEquals(Arrays.asList("archlinuxarm"), rolling.pacmanKeyrings());
        assertEquals(Arrays.asList(ALARM_KEY), rolling.packageSigningKeys());
        assertEquals(ROLLING_GOLDEN, RootfsManager.pacmanKeyringScript(rolling));
    }

    @Test
    public void blackArchDeclaresBothKeyringsAndBothSigningKeys() throws Exception {
        DistroInfo blackArch = edition("garden-blackarch");
        assertEquals(Arrays.asList("archlinuxarm", "blackarch"), blackArch.pacmanKeyrings());
        assertEquals(Arrays.asList(ALARM_KEY, BLACKARCH_KEY), blackArch.packageSigningKeys());
        String script = RootfsManager.pacmanKeyringScript(blackArch);
        assertTrue(script, script.contains("\npacman-key --populate archlinuxarm blackarch\n"));
        for (String key : blackArch.packageSigningKeys()) {
            assertTrue(script.contains("$G --with-colons --list-keys " + key + " | grep -q '^pub:[fu]:'"));
            assertTrue(script.contains("signed by " + key));
        }
        assertTrue(script.endsWith("rm -f /tmp/.thothterm-verify\necho keyring-verified\n"));
        // The signature level is not this script's business, and never weakened here.
        assertTrue(!script.contains("SigLevel") && !script.contains("TrustAll"));
    }

    // ---- the script's logic, run with stand-ins for pacman-key and gpg ----

    private static final class Run {
        final int exit;
        final String output;

        Run(int exit, String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    private static String status(String signer, boolean trusted) {
        return "[GNUPG:] NEWSIG\n[GNUPG:] GOODSIG 7533BAFE69A25079 someone\n"
                + "[GNUPG:] VALIDSIG " + signer + " 2026-10-03 1 0 4 0 1 10 00 " + signer + "\n"
                + (trusted ? "[GNUPG:] TRUST_ULTIMATE 0 pgp\n" : "[GNUPG:] TRUST_UNDEFINED 0 pgp\n");
    }

    /**
     * Runs {@code script} in a scratch directory. Paths in the script are
     * redirected there and pacman-key and gpg are shell stand-ins: gpg
     * reports the keys in {@code invalidKeys} as not fully valid and answers
     * a verification of NAME.sig with the status text in the file NAME.status.
     */
    private static Run run(String script, java.util.Map<String, String> sigStatus,
            java.util.List<String> invalidKeys) throws Exception {
        assumeTrue("sh is required", new File("/bin/sh").canExecute());
        File t = Files.createTempDirectory("keyring-script").toFile();
        try {
            File bin = new File(t, "bin");
            File sc = new File(t, "sc");
            assertTrue(bin.mkdirs() && sc.mkdirs());
            write(new File(bin, "pacman-key"), "#!/bin/sh\necho \"pacman-key $*\" >> \"" + t + "/calls\"\n");
            write(new File(bin, "gpg"), "#!/bin/sh\n"
                    + "for a in \"$@\"; do last=$a; case $a in *.sig) sig=$a;; esac; done\n"
                    + "case \" $* \" in\n"
                    + "*\" --list-keys \"*) if grep -qx \"$last\" \"" + t + "/invalid\"; then echo 'pub:e:4096:1:X:1:::'; "
                    + "else echo 'pub:f:4096:1:X:1:::'; fi ;;\n"
                    + "*\" --verify \"*) cat \"" + t + "/$(basename \"$sig\").status\" ;;\n"
                    + "esac\n");
            assertTrue(new File(bin, "pacman-key").setExecutable(true));
            assertTrue(new File(bin, "gpg").setExecutable(true));
            write(new File(t, "invalid"), String.join("\n", invalidKeys) + "\n");
            for (java.util.Map.Entry<String, String> e : sigStatus.entrySet()) {
                write(new File(sc, e.getKey()), "signature");
                write(new File(sc, e.getKey().replace(".sig", "")), "package");
                write(new File(t, e.getKey() + ".status"), e.getValue());
            }
            String adapted = script
                    .replace("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                            "export PATH=\"" + bin + ":/usr/bin:/bin\"")
                    .replace("\ncd /\n", "\ncd \"" + t + "\"\n")
                    .replace("/etc/pacman.d/gnupg", t + "/gnupg")
                    .replace("/usr/share/thothterm/signature-check/", sc + "/")
                    .replace("/tmp/.thothterm-verify", t + "/verify");
            File file = new File(t, "script.sh");
            write(file, adapted);
            ProcessBuilder pb = new ProcessBuilder("sh", file.getPath());
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            Process p = pb.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            return new Run(p.waitFor(), new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            delete(t);
        }
    }

    private static void write(File f, String text) throws Exception {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        f.delete();
    }

    private static java.util.Map<String, String> sigs(String... pairs) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static final java.util.List<String> NONE = java.util.Collections.emptyList();

    @Test
    public void rollingsScriptPassesOnlyWithItsOwnSignature() throws Exception {
        String script = RootfsManager.pacmanKeyringScript(edition("garden-arch"));
        Run ok = run(script, sigs("pkg-a.sig", status(ALARM_KEY, true)), NONE);
        assertEquals(ok.output, 0, ok.exit);
        assertTrue(ok.output.endsWith("keyring-verified\n"));
        assertTrue(run(script, sigs("pkg-a.sig", status(BLACKARCH_KEY, true)), NONE).exit != 0);
        assertTrue(run(script, sigs("pkg-a.sig", status(ALARM_KEY, false)), NONE).exit != 0);
        assertTrue(run(script, sigs(), NONE).exit != 0);
        assertTrue(run(script, sigs("pkg-a.sig", status(ALARM_KEY, true)), Arrays.asList(ALARM_KEY)).exit != 0);
    }

    @Test
    public void blackArchsScriptNeedsBothIdentitiesProven() throws Exception {
        String script = RootfsManager.pacmanKeyringScript(edition("garden-blackarch"));
        String alarm = status(ALARM_KEY, true);
        String black = status(BLACKARCH_KEY, true);

        Run ok = run(script, sigs("alarm.sig", alarm, "blackarch.sig", black), NONE);
        assertEquals(ok.output, 0, ok.exit);
        assertTrue(ok.output.endsWith("keyring-verified\n"));

        // One identity unproven is a failure, whichever it is.
        assertTrue(run(script, sigs("alarm.sig", alarm), NONE).exit != 0);
        assertTrue(run(script, sigs("blackarch.sig", black), NONE).exit != 0);
        // Two files by the same key do not prove the other.
        assertTrue(run(script, sigs("a.sig", alarm, "b.sig", alarm), NONE).exit != 0);
        // A file signed by a key the edition does not declare fails even beside good ones.
        String stranger = status("0123456789ABCDEF0123456789ABCDEF01234567", true);
        assertTrue(run(script, sigs("alarm.sig", alarm, "blackarch.sig", black, "other.sig", stranger), NONE).exit != 0);
        // Trust must be full or ultimate on every file.
        assertTrue(run(script, sigs("alarm.sig", alarm, "blackarch.sig", status(BLACKARCH_KEY, false)), NONE).exit != 0);
        // Every key must be fully valid in the keyring.
        assertTrue(run(script, sigs("alarm.sig", alarm, "blackarch.sig", black), Arrays.asList(BLACKARCH_KEY)).exit != 0);
        assertTrue(run(script, sigs("alarm.sig", alarm, "blackarch.sig", black), Arrays.asList(ALARM_KEY)).exit != 0);
        // Nothing to check against is a failure, not a pass.
        assertTrue(run(script, sigs(), NONE).exit != 0);
        // A status line without a signer fails closed.
        assertTrue(run(script, sigs("alarm.sig", alarm, "blackarch.sig", "[GNUPG:] TRUST_ULTIMATE 0 pgp\n"), NONE).exit != 0);
    }
}
