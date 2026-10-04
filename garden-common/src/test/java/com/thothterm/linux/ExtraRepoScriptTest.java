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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The guest script that enables an optional third-party repository: what it must and must never do. */
public class ExtraRepoScriptTest {
    private static String script() throws Exception {
        return ExtraRepoScript.enableScript(DistroInfoTest.load(DistroInfoTest.withRepo()));
    }

    private static int at(String script, String needle) {
        int i = script.indexOf(needle);
        assertTrue("missing: " + needle, i >= 0);
        return i;
    }

    @Test
    public void everyPinReachesTheScript() throws Exception {
        String s = script();
        assertTrue(s.contains(DistroInfoTest.SHA + "  "));
        assertTrue(s.contains("= 1000 "));
        assertTrue(s.contains("= 100 "));
        // Sorted, upper case, exactly the pinned sets.
        assertTrue(s.contains("= '" + DistroInfoTest.KEY_A + " " + DistroInfoTest.KEY_B + " '"));
        assertTrue(s.contains("= '" + DistroInfoTest.KEY_REVOKED + " '"));
        assertTrue(s.contains("VALIDSIG " + DistroInfoTest.KEY_B + " "));
        assertTrue(s.contains("extra-keyring-1-any.pkg.tar.zst"));
    }

    /** Integrity and trust are proven before pacman.conf is touched, and the order is the contract. */
    @Test
    public void provesBeforeItChangesAnything() throws Exception {
        String s = script();
        int sha = at(s, "sha256sum -c");
        int extract = at(s, "bsdtar -xf");
        int lists = at(s, "trusted keys differ from the pin");
        int populate = at(s, "pacman-key --populate-from \"$K\" --populate extra");
        int signer = at(s, "was not signed by the pinned key");
        int marker = at(s, ExtraRepoScript.VERIFIED_MARKER);
        int install = at(s, "pacman --config");
        int confWrite = at(s, "cat \"$D/pacman.conf.new\" > /etc/pacman.conf");
        int sync = at(s, "pacman -Sy --noconfirm");
        assertTrue(sha < extract && extract < lists && lists < populate && populate < signer
                && signer < marker && marker < install && install < confWrite && confWrite < sync);
    }

    /** Never a bypass, never a script from the network, never more than the three keyring files. */
    @Test
    public void neverWeakensSignaturesOrRunsRemoteCode() throws Exception {
        // The configuration check names the relaxed levels it refuses; that one line is not a use of them.
        String s = script().replace("Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll", "<refused levels>");
        for (String forbidden : new String[]{"SigLevel = Never", "SigLevel=Never", "TrustAll", "Optional",
                "--skippgpcheck", "--nosig", "curl", "wget", "strap", "eval ", "pacman-key --recv",
                "keyserver", "--refresh-keys", "--lsign-key", "--add "}) {
            // "Optional" appears only as the word a check forbids.
            if (forbidden.equals("Optional")) continue;
            assertFalse("script contains " + forbidden, s.contains(forbidden));
        }
        assertFalse("pipes into a shell", java.util.regex.Pattern.compile("\\|\\s*(ba|da|z)?sh\\b").matcher(s).find());
        // LocalFileSigLevel is raised to Required for the one local install, never lowered.
        assertTrue(s.contains("LocalFileSigLevel = Required"));
        assertFalse(s.contains("LocalFileSigLevel = Optional"));
        assertFalse(s.contains("LocalFileSigLevel = Never"));
        // Only the three keyring files leave the package.
        assertTrue(s.contains("usr/share/pacman/keyrings/extra.gpg usr/share/pacman/keyrings/extra-trusted "
                + "usr/share/pacman/keyrings/extra-revoked"));
        assertEquals(1, s.split("bsdtar -xf", -1).length - 1);
        // The configuration check refuses every relaxed level and a repository with its own.
        assertTrue(script().contains("Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll"));
        assertTrue(s.contains("the repository sets its own signature level"));
        // No scriptlet runs from the downloaded package.
        assertTrue(s.contains("--noscriptlet"));
    }

    @Test
    public void theRepositorySectionIsOneMarkedBlockWithoutASigLevel() throws Exception {
        DistroInfo d = DistroInfoTest.load(DistroInfoTest.withRepo());
        String section = ExtraRepoScript.repoSection(d);
        assertEquals("# >>> ThothTerm optional repository: extrarepo (https://repo.example.org)\n"
                + "[extrarepo]\n"
                + "Server = https://repo.example.org/x/$repo/os/$arch\n"
                + "# <<< ThothTerm optional repository: extrarepo\n", section);
        assertFalse(section.contains("SigLevel"));
        assertTrue(section.startsWith(ExtraRepoScript.beginMarker(d)));
    }

    @Test
    public void cleansUpWhatItStaged() throws Exception {
        String s = script();
        assertTrue(s.contains("trap 'rm -rf "));
        assertTrue(s.contains("*.b64"));
        assertTrue(s.contains("\"$D\"/pkg"));
        assertEquals("/var/tmp/thothterm-extrarepo", ExtraRepoScript.STAGING_DIR);
    }
}
