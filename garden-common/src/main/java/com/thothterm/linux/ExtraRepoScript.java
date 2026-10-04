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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The guest script that turns on an optional third-party pacman repository
 * after the user agreed to it ({@link DistroInfo#hasExtraRepo()}).
 *
 * <p>The app has already downloaded the repository's keyring package and its
 * detached signature over HTTPS and refused anything but the pinned size and
 * SHA-256; it hands them to the guest base64-encoded in a staging directory.
 * This script then, failing closed at every step:
 * <ol>
 *   <li>checks both files against the pinned digests and size again, in the guest;
 *   <li>unpacks only the three keyring files into scratch space and requires
 *       the trusted and revoked lists to equal the pinned ones exactly;
 *   <li>imports them with {@code pacman-key --populate-from}, never anything
 *       else, and proves from the installation's own keyring that exactly the
 *       pinned keys are fully trusted, the revoked ones are not, and that the
 *       package's signature verifies against the pinned signer;
 *   <li>installs the package with pacman, which checks its signature once more
 *       ({@code LocalFileSigLevel = Required});
 *   <li>adds one marked repository section to {@code pacman.conf}, then proves
 *       with {@code pacman-conf} that no signature level was relaxed;
 *   <li>refreshes the package databases.
 * </ol>
 * It never runs a script from the network and never lowers a SigLevel. The
 * configuration is only touched after the keys are proven, so a failure before
 * that point leaves the guest exactly as it was.
 */
final class ExtraRepoScript {
    /** Where the app stages the downloaded files, inside the guest. */
    static final String STAGING_DIR = "/var/tmp/thothterm-extrarepo";
    static final String PACKAGE_B64 = "pkg.b64";
    static final String SIGNATURE_B64 = "sig.b64";

    /** Printed by the script when the keys were proven. */
    static final String VERIFIED_MARKER = "EXTRA_REPO_KEYS_VERIFIED";
    /** Printed by the script when the databases were refreshed. */
    static final String SYNCED_MARKER = "EXTRA_REPO_SYNCED";

    private ExtraRepoScript() {}

    /** The marker comment that begins the section the script adds to pacman.conf. */
    static String beginMarker(DistroInfo d) {
        return "# >>> ThothTerm optional repository: " + d.extraRepo();
    }

    static String endMarker(DistroInfo d) {
        return "# <<< ThothTerm optional repository: " + d.extraRepo();
    }

    /** The section the script appends: one repository, no SigLevel of its own. */
    static String repoSection(DistroInfo d) {
        return beginMarker(d) + " (" + d.extraRepoSite() + ")\n"
                + "[" + d.extraRepo() + "]\n"
                + "Server = " + d.extraRepoServer() + "\n"
                + endMarker(d) + "\n";
    }

    /** The package's file name, as the pinned URL names it (pacman reads the signature beside it). */
    static String packageFileName(DistroInfo d) {
        String url = d.extraRepoKeyringUrl();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    static String enableScript(DistroInfo d) {
        String name = d.extraRepoKeyring();
        String file = packageFileName(d);
        String trusted = String.join(" ", sorted(d.extraRepoTrusted()));
        String revoked = String.join(" ", sorted(d.extraRepoRevoked()));
        StringBuilder s = new StringBuilder();
        s.append("set -eu\n")
         .append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n")
         .append("cd /\n")
         .append("D=").append(STAGING_DIR).append("\n")
         .append("G=/etc/pacman.d/gnupg\n")
         .append("die() { echo \"extra-repo: $*\" >&2; exit 1; }\n")
         .append("trap 'rm -rf \"$D\"/x \"$D\"/*.pkg.tar.* \"$D\"/pkg \"$D\"/sig \"$D\"/*.b64 \"$D\"/*.new \"$D\"/*.conf' EXIT\n")
         // The installation's own keyring must exist before anything is imported.
         .append("test -s $G/trustdb.gpg || die 'the pacman keyring is not ready yet'\n")
         .append("base64 -d \"$D/").append(PACKAGE_B64).append("\" > \"$D/").append(file).append("\"\n")
         .append("base64 -d \"$D/").append(SIGNATURE_B64).append("\" > \"$D/").append(file).append(".sig\"\n")
         // 1. Integrity: exactly the pinned artifact, by size and SHA-256.
         .append("test \"$(stat -c %s \"$D/").append(file).append("\")\" = ").append(d.extraRepoKeyringSize())
         .append(" || die 'keyring package size differs from the pin'\n")
         .append("test \"$(stat -c %s \"$D/").append(file).append(".sig\")\" = ").append(d.extraRepoKeyringSigSize())
         .append(" || die 'keyring signature size differs from the pin'\n")
         .append("echo '").append(d.extraRepoKeyringSha256()).append("  '\"$D/").append(file)
         .append("\" | sha256sum -c - >/dev/null || die 'keyring package digest differs from the pin'\n")
         .append("echo '").append(d.extraRepoKeyringSigSha256()).append("  '\"$D/").append(file)
         .append(".sig\" | sha256sum -c - >/dev/null || die 'keyring signature digest differs from the pin'\n")
         // 2. Only the three keyring files, into scratch space.
         .append("mkdir -p \"$D/x\"\n")
         .append("bsdtar -xf \"$D/").append(file).append("\" -C \"$D/x\" usr/share/pacman/keyrings/")
         .append(name).append(".gpg usr/share/pacman/keyrings/").append(name)
         .append("-trusted usr/share/pacman/keyrings/").append(name).append("-revoked\n")
         .append("K=\"$D/x/usr/share/pacman/keyrings\"\n")
         // The trust lists are what the app pinned, nothing more: one entry per
         // line, full-trust level 4 only, the exact fingerprints.
         .append("T=\"$(grep -v '^[[:space:]]*\\(#.*\\)\\?$' \"$K/").append(name).append("-trusted\")\"\n")
         .append("printf '%s\\n' \"$T\" | grep -Evq '^[0-9A-F]{40}:4:$' && die 'unexpected trust level in the keyring'\n")
         .append("test \"$(printf '%s\\n' \"$T\" | cut -d: -f1 | sort | tr '\\n' ' ')\" = '").append(trusted).append(" ' || die 'trusted keys differ from the pin'\n")
         .append("R=\"$(grep -v '^[[:space:]]*\\(#.*\\)\\?$' \"$K/").append(name).append("-revoked\" || true)\"\n")
         .append("printf '%s\\n' \"$R\" | grep -Evq '^([0-9A-F]{40})?$' && die 'unexpected revocation entry'\n")
         .append("test \"$(printf '%s\\n' \"$R\" | sort | tr '\\n' ' ' | sed 's/^ *//')\" = '").append(revoked.isEmpty() ? "" : revoked + " ").append("' || die 'revoked keys differ from the pin'\n")
         // 3. Import into the installation's own keyring and prove the result.
         .append("pacman-key --populate-from \"$K\" --populate ").append(name).append(" >/dev/null\n")
         .append("for f in ").append(trusted).append("; do\n")
         .append("  v=\"$(gpg --homedir $G --no-permission-warning --batch --with-colons --list-keys \"$f\" | awk -F: '$1==\"pub\"{print $2; exit}')\"\n")
         .append("  case \"$v\" in f|u) ;; *) die \"key $f is not fully valid (${v:-missing})\";; esac\n")
         .append("  gpg --homedir $G --no-permission-warning --batch --export-ownertrust | grep -qx \"$f:4:\" || die \"key $f is not fully trusted\"\n")
         .append("done\n")
         .append("for f in ").append(revoked).append("; do\n")
         .append("  v=\"$(gpg --homedir $G --no-permission-warning --batch --with-colons --list-keys \"$f\" 2>/dev/null | awk -F: '$1==\"pub\"{print $2; exit}' || true)\"\n")
         .append("  case \"$v\" in f|u|m) die \"revoked key $f is still valid ($v)\";; esac\n")
         .append("  gpg --homedir $G --no-permission-warning --batch --export-ownertrust | grep -Eq \"^$f:[4-6]:\" && die \"revoked key $f is trusted\"\n")
         .append("done\n")
         // The package is signed by the pinned signer, whom the keyring now trusts.
         .append("gpg --homedir $G --no-permission-warning --batch --status-fd 1 --verify \"$D/").append(file).append(".sig\" \"$D/").append(file)
         .append("\" > \"$D/verify.status\" 2>/dev/null || die 'the keyring package signature does not verify'\n")
         .append("grep -q '^\\[GNUPG:\\] VALIDSIG ").append(d.extraRepoSigner()).append(" ' \"$D/verify.status\" || die 'the keyring package was not signed by the pinned key'\n")
         .append("grep -Eq '^\\[GNUPG:\\] TRUST_(FULLY|ULTIMATE)' \"$D/verify.status\" || die 'the signer is not trusted'\n")
         .append("rm -f \"$D/verify.status\"\n")
         .append("echo ").append(VERIFIED_MARKER).append(" trusted=").append(d.extraRepoTrusted().size())
         .append(" revoked=").append(d.extraRepoRevoked().size()).append("\n")
         // 4. pacman installs the package and checks its signature as well.
         .append("sed 's/^LocalFileSigLevel.*/LocalFileSigLevel = Required/' /etc/pacman.conf > \"$D/local.conf\"\n")
         .append("grep -qx 'LocalFileSigLevel = Required' \"$D/local.conf\" || die 'pacman.conf has no LocalFileSigLevel'\n")
         .append("pacman --config \"$D/local.conf\" -U --noconfirm --needed --noscriptlet \"$D/").append(file).append("\"\n")
         .append("pacman -Q ").append(name).append("-keyring >/dev/null || die 'the keyring package is not installed'\n")
         // 5. The repository section, once, and proof that signatures stay required.
         .append("B='").append(beginMarker(d)).append("'\n")
         .append("if ! grep -q \"^$B\" /etc/pacman.conf; then\n")
         .append("  { cat /etc/pacman.conf; printf '\\n'; printf '%s' '").append(repoSection(d).replace("'", "'\\''")).append("'; } > \"$D/pacman.conf.new\"\n")
         .append("  C=\"pacman-conf --config $D/pacman.conf.new\"\n")
         .append(CHECK_CONF.replace("@NAME@", d.extraRepo()).replace("@SERVER@", d.extraRepoServer()))
         .append("  cat \"$D/pacman.conf.new\" > /etc/pacman.conf\n")
         .append("fi\n")
         .append("C='pacman-conf'\n")
         .append(CHECK_CONF.replace("@NAME@", d.extraRepo()).replace("@SERVER@", d.extraRepoServer()))
         // 6. Fresh databases (the rootfs ships none).
         .append("pacman -Sy --noconfirm\n")
         .append("test \"$(pacman -Sl ").append(d.extraRepo()).append(" | wc -l)\" -gt 0 || die 'the repository lists no packages'\n")
         .append("echo ").append(SYNCED_MARKER).append("\n");
        return s.toString();
    }

    /** Proves the effective configuration: this repository present, no relaxed signature level anywhere. */
    private static final String CHECK_CONF =
            "  $C --repo-list | tr ' ' '\\n' | grep -qx '@NAME@' || die 'the repository is not configured'\n"
          + "  e='@SERVER@'; e=\"${e//\\$repo/@NAME@}\"; e=\"${e//\\$arch/$($C Architecture)}\"\n"
          + "  test \"$($C --repo @NAME@ Server)\" = \"$e\" || die 'the repository server differs from the pin'\n"
          + "  test -z \"$($C --repo @NAME@ SigLevel)\" || die 'the repository sets its own signature level'\n"
          + "  { $C SigLevel; for r in $($C --repo-list); do $C --repo \"$r\" SigLevel; done; } | tr ' ' '\\n' > \"$D/siglevels.conf\"\n"
          + "  grep -Eqx 'Required|PackageRequired' \"$D/siglevels.conf\" || die 'signatures are not required'\n"
          + "  if grep -Eqx 'Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll' \"$D/siglevels.conf\"; then die 'a signature level is relaxed'; fi\n";
}
