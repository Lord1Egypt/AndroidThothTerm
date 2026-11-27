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

/**
 * Pure, idempotent text transforms for the guest account and managed
 * configuration. No Android APIs so every rule is unit-testable.
 *
 * <p>All transforms append or edit only the lines they own. Unrelated entries
 * in {@code /etc/passwd}, {@code /etc/group}, {@code /etc/shadow} and the
 * debconf databases are never rewritten, so an already-populated rootfs can be
 * upgraded in place. Running the transforms twice produces the same text.</p>
 */
final class GuestConfig {
    /** The guest account's name unless the user chose another in Settings. */
    static final String DEFAULT_USER = "thoth";
    /** The guest's host name unless the user chose another in Settings. */
    static final String DEFAULT_HOSTNAME = "thothterm";
    static final int UID = 1000;
    static final int GID = 1000;
    static final String USER_SHELL = "/bin/bash";

    /** Bumped when managed guest configuration changes so it can be re-applied. */
    static final int RUNTIME_CONFIG_VERSION = 4;

    /** A portable login name: lower case, a letter or underscore first, 32 characters at most. */
    private static final java.util.regex.Pattern USER_NAME =
            java.util.regex.Pattern.compile("[a-z_][a-z0-9_-]{0,31}");
    /** One RFC 1123 label of at most 63 characters, which PRoot's --hostname accepts too. */
    private static final java.util.regex.Pattern HOST_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,62}");

    /** Exact text of the {@code auth sufficient pam_permit.so} line ThothTerm
     *  added to {@code /etc/pam.d/su} in runtime config v2. */
    private static final String MANAGED_PAM_PERMIT = "auth sufficient pam_permit.so";

    /** First line of the ThothTerm-managed {@code /usr/local/bin/sudo} helper. */
    private static final String MANAGED_SUDO_MARKER = "# Managed by ThothTerm";

    private GuestConfig() {
    }

    /** True for a name the guest account may have; "root" is never one. */
    static boolean isValidUserName(String name) {
        return name != null && USER_NAME.matcher(name).matches() && !name.equals("root");
    }

    /** True for a name the guest may carry as its host name. */
    static boolean isValidHostName(String name) {
        return name != null && HOST_NAME.matcher(name).matches();
    }

    /** The home directory of the guest account {@code user}. */
    static String homeOf(String user) {
        return "/home/" + user;
    }

    /** True when the passwd, group or shadow text has an entry of that name. */
    static boolean hasAccount(String text, String name) {
        return hasNamedLine(text, name);
    }

    static String ensurePasswd(String text, String user) {
        if (hasNamedLine(text, user)) return text;
        return appendStanza(text, user + ":x:" + UID + ":" + GID + ":Thoth User:"
                + homeOf(user) + ":" + USER_SHELL);
    }

    /**
     * Adds the account's own group and makes the account a member of
     * {@code adminGroup} ("sudo" on Debian, "wheel" on Arch). Elevation itself
     * comes from the sudoers entry; the membership only matches what the
     * distribution's own tools expect of an administrator. Only a missing
     * "sudo" group is created (as Debian's gid 27); another missing group is
     * left alone rather than guessing a gid the distribution may use.
     */
    static String ensureGroup(String text, String adminGroup, String user) {
        String result = text;
        if (!hasNamedLine(result, user)) {
            result = appendStanza(result, user + ":x:" + GID + ":");
        }
        if (!hasNamedLine(result, adminGroup) && !adminGroup.equals("sudo")) return result;
        return ensureMember(result, adminGroup, user);
    }

    static String ensureShadow(String text, String user) {
        if (hasNamedLine(text, user)) return text;
        return appendStanza(text, user + ":!:19000:0:99999:7:::");
    }

    /**
     * Renames the account in {@code /etc/passwd}: the name field of its entry,
     * and a home directory that followed the old name. The uid, gid, shell and
     * every other entry stay as they are. Without an entry of the old name the
     * text is returned unchanged, so a rename interrupted half-way can simply
     * be run again.
     */
    static String renamePasswd(String text, String oldName, String newName) {
        return renameEntries(text, oldName, newName, 5);
    }

    /**
     * Renames the account in {@code /etc/group}: its own group's name field and
     * its membership of every other group (sudo, wheel, ...).
     */
    static String renameGroup(String text, String oldName, String newName) {
        return renameEntries(text, oldName, newName, -1, 3);
    }

    /** Renames the account's {@code /etc/shadow} entry. */
    static String renameShadow(String text, String oldName, String newName) {
        return renameEntries(text, oldName, newName, -1);
    }

    /**
     * Renames the account's group in {@code /etc/gshadow}, and the account in
     * every group's administrator and member lists.
     */
    static String renameGshadow(String text, String oldName, String newName) {
        return renameEntries(text, oldName, newName, -1, 2, 3);
    }

    private static String renameEntries(String text, String oldName, String newName,
                                        int homeField, int... memberFields) {
        if (text == null || oldName.equals(newName)) return text;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String[] fields = lines[i].split(":", -1);
            if (fields.length < 2) continue;
            if (fields[0].equals(oldName)) {
                fields[0] = newName;
                if (homeField >= 0 && fields.length > homeField
                        && fields[homeField].equals(homeOf(oldName))) {
                    fields[homeField] = homeOf(newName);
                }
            }
            for (int field : memberFields) {
                if (fields.length > field) {
                    fields[field] = renameMember(fields[field], oldName, newName);
                }
            }
            lines[i] = String.join(":", fields);
        }
        return String.join("\n", lines);
    }

    private static String renameMember(String members, String oldName, String newName) {
        if (members.isEmpty()) return members;
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String member : members.split(",", -1)) {
            String renamed = member.equals(oldName) ? newName : member;
            if (!result.contains(renamed)) result.add(renamed);
        }
        return String.join(",", result);
    }

    private static final int AID_USER_OFFSET = 100000;
    private static final int AID_APP_START = 10000;
    private static final int AID_CACHE_START = 20000;
    private static final int AID_SHARED_START = 50000;

    /** Where the guest's sudo package stands, from its package database. */
    enum PackageState {
        /** Fully installed and configured. */
        INSTALLED,
        /**
         * Present but not finished -- unpacked, half-configured or awaiting
         * triggers. {@code dpkg --configure -a} completes it in place;
         * reinstalling a bundled copy would downgrade a newer version.
         */
        UNFINISHED,
        /** Never installed, removed, or purged. */
        ABSENT,
    }

    /** The state of {@code packageName} in the text of /var/lib/dpkg/status. */
    static PackageState packageState(String dpkgStatus, String packageName) {
        for (String stanza : dpkgStatus.split("\n\n")) {
            String status = null;
            boolean match = false;
            for (String line : stanza.split("\n")) {
                if (line.equals("Package: " + packageName)) match = true;
                else if (line.startsWith("Status: ")) status = line.substring("Status: ".length());
            }
            if (!match || status == null) continue;
            String[] words = status.trim().split("\\s+");
            String current = words[words.length - 1];
            switch (current) {
                case "installed":
                    return PackageState.INSTALLED;
                case "unpacked":
                case "half-configured":
                case "triggers-awaited":
                case "triggers-pending":
                case "half-installed":
                    return PackageState.UNFINISHED;
                default:
                    return PackageState.ABSENT;
            }
        }
        return PackageState.ABSENT;
    }

    /**
     * The state of {@code packageName} from the entry names in pacman's local
     * database ({@code /var/lib/pacman/local}), which are
     * {@code <name>-<pkgver>-<pkgrel>}. pacman writes an entry only once the
     * package's files are in place and has no half-configured state, so an
     * entry means installed. An interrupted transaction shows as a leftover
     * {@code db.lck} instead.
     */
    static PackageState pacmanPackageState(String[] localEntries, String packageName) {
        if (localEntries == null) return PackageState.ABSENT;
        for (String entry : localEntries) {
            int rel = entry.lastIndexOf('-');
            int ver = rel > 0 ? entry.lastIndexOf('-', rel - 1) : -1;
            if (ver > 0 && entry.substring(0, ver).equals(packageName)) {
                return PackageState.INSTALLED;
            }
        }
        return PackageState.ABSENT;
    }

    /**
     * The guest user's sudo rule. sudo resets the environment, so the one
     * variable package hooks need to see the truth about the environment
     * ({@code SYSTEMD_IN_CHROOT}, see the runtime) is kept explicitly.
     */
    static String sudoersEntry(String user) {
        return "Defaults:" + user + " env_keep += \"SYSTEMD_IN_CHROOT\"\n"
                + user + " ALL=(ALL:ALL) NOPASSWD: ALL\n";
    }

    /**
     * Ensures the loopback entries sudo and local tools require in
     * {@code /etc/hosts}. A minimal base image can ship it empty, and sudo fails with
     * "unable to resolve host" when its own host name is missing. Only missing
     * managed lines are appended; user entries are preserved and rerunning the
     * transform returns the same text.
     *
     * <p>The guest's own name is the {@code 127.0.1.1} line, where Debian's
     * installer keeps it. That one line is the app's: it follows the host name
     * the user chose, and no other line is touched.</p>
     */
    static String ensureHosts(String text, String hostName) {
        StringBuilder result = new StringBuilder(text == null ? "" : text);
        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
            result.append('\n');
        }
        ensureHostEntry(result, "127.0.0.1", "localhost");
        ensureHostEntry(result, "::1", "localhost ip6-localhost ip6-loopback");
        return setOwnHostEntry(result.toString(), hostName);
    }

    private static String setOwnHostEntry(String text, String hostName) {
        String own = "127.0.1.1 " + hostName;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String[] fields = lines[i].trim().split("\\s+");
            if (!fields[0].equals("127.0.1.1")) continue;
            if (fields.length == 2 && fields[1].equals(hostName)) return text;
            lines[i] = own;
            return String.join("\n", lines);
        }
        return text + own + "\n";
    }

    /**
     * Ensures the guest can name the Android supplementary groups its processes
     * already carry. An app's threads run with the fixed AIDs {@code inet} and
     * {@code everybody} plus two derived from its own uid, and a base image has
     * no entry for any of them, so {@code id}, {@code ls -l} and {@code ps}
     * print "cannot find name for group ID" instead of a name.
     *
     * <p>This only adds name-to-GID mappings inside the guest. It grants
     * nothing: the process already holds these groups, membership lists are
     * left empty, and no Android credential or permission is altered. Only
     * missing managed lines are appended, user entries are preserved, and
     * rerunning the transform returns the same text.</p>
     *
     * @param androidUid the app's Android uid, e.g. {@code Process.myUid()}
     */
    static String ensureGroups(String text, int androidUid) {
        StringBuilder result = new StringBuilder(text == null ? "" : text);
        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
            result.append('\n');
        }
        ensureGroupEntry(result, "aid_inet", 3003);
        ensureGroupEntry(result, "aid_everybody", 9997);
        int appId = androidUid % AID_USER_OFFSET - AID_APP_START;
        if (appId >= 0) {
            ensureGroupEntry(result, "aid_cache", AID_CACHE_START + appId);
            ensureGroupEntry(result, "aid_all", AID_SHARED_START + appId);
        }
        return result.toString();
    }

    private static void ensureGroupEntry(StringBuilder text, String name, int gid) {
        for (String line : text.toString().split("\n", -1)) {
            String[] fields = line.split(":", -1);
            if (fields.length < 3) continue;
            if (fields[0].equals(name) || fields[2].trim().equals(Integer.toString(gid))) return;
        }
        text.append(name).append(":x:").append(gid).append(":\n");
    }

    private static void ensureHostEntry(StringBuilder text, String address, String names) {
        String first = names.split(" ")[0];
        for (String line : text.toString().split("\n", -1)) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 2 || !fields[0].equals(address)) continue;
            for (int i = 1; i < fields.length; i++) {
                if (fields[i].equals(first)) return;
            }
        }
        text.append(address).append(' ').append(names).append('\n');
    }

    /**
     * Reverts the runtime-config-v2 {@code su} PAM customization. The real
     * {@code sudo} is now the only elevation path, so {@code su} must go back to
     * its stock policy ({@code auth sufficient pam_rootok.so}) and must not offer
     * a passwordless route to root. Only the exact managed line is removed.
     */
    static String removePasswordlessSu(String pamSu) {
        String[] lines = pamSu.split("\n", -1);
        java.util.List<String> kept = new java.util.ArrayList<>(lines.length);
        for (String line : lines) {
            if (line.trim().equals(MANAGED_PAM_PERMIT)) continue;
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    static String ensureDebconfFrontendConfig(String text) {
        String stanza = "Name: debconf/frontend\n"
                + "Template: debconf/frontend\n"
                + "Value: Teletype\n"
                + "Owners: debconf\n"
                + "Flags: seen";
        return replaceStanzaField(text, "debconf/frontend", "Value", "Teletype", stanza);
    }

    static String ensureDebconfFrontendTemplate(String text) {
        if (hasStanzaNamed(text, "debconf/frontend")) return text;
        String stanza = "Name: debconf/frontend\n"
                + "Type: select\n"
                + "Choices: Dialog, Readline, Gnome, Kde, Editor, Noninteractive, Teletype\n"
                + "Description: Interface to use for configuring packages";
        return appendStanza(text, stanza);
    }

    /**
     * True when {@code /usr/local/bin/sudo} is the ThothTerm-managed su-backed
     * helper from runtime config v2 (or the earlier "fallback sudo" variant).
     * A file without this marker is treated as user-owned and is never deleted.
     */
    static boolean isManagedSudoHelper(String content) {
        return content != null && content.contains(MANAGED_SUDO_MARKER);
    }

    /**
     * Replaces the distro locale fixup (an {@code eval} of {@code locale-check}
     * output) with a direct setting that only applies when the locale actually
     * exists. This removes the fragile eval at its source and never evaluates a
     * path as a command.
     */
    static String localeFixScript() {
        return "# Managed by ThothTerm. C.UTF-8 is built into glibc on this image.\n"
                + "if [ -d /usr/lib/locale/C.utf8 ]; then\n"
                + "  export LANG=C.UTF-8\n"
                + "  export LC_ALL=C.UTF-8\n"
                + "fi\n";
    }

    /** True for debconf-style stanzas whose first field is {@code Name: <name>}. */
    static boolean hasStanzaNamed(String text, String name) {
        for (String line : text.split("\n", -1)) {
            if (line.equals("Name: " + name)) return true;
        }
        return false;
    }

    private static boolean hasNamedLine(String text, String name) {
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(name + ":")) return true;
        }
        return false;
    }

    private static String ensureMember(String text, String group, String member) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(group + ":")) continue;

            String[] fields = line.split(":", -1);
            if (fields.length < 4) {
                String[] padded = new String[4];
                for (int f = 0; f < 4; f++) {
                    padded[f] = f < fields.length ? fields[f] : "";
                }
                padded[1] = padded[1].isEmpty() ? "x" : padded[1];
                fields = padded;
            }
            for (String existing : fields[3].split(",")) {
                if (existing.equals(member)) return text;
            }
            fields[3] = fields[3].isEmpty() ? member : fields[3] + "," + member;
            lines[i] = String.join(":", fields);
            return String.join("\n", lines);
        }
        return appendStanza(text, group + ":x:27:" + member);
    }

    /** Adds or updates a field inside an existing stanza, else appends the whole stanza. */
    private static String replaceStanzaField(String text, String name,
                                             String field, String value, String fallbackStanza) {
        java.util.List<String> lines = new java.util.ArrayList<>(
                java.util.Arrays.asList(text.split("\n", -1)));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("Name: " + name)) {
                start = i;
                break;
            }
        }
        if (start < 0) return appendStanza(text, fallbackStanza);

        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).isEmpty()) {
                end = i;
                break;
            }
        }
        int insertAt = end;
        for (int i = start + 1; i < end; i++) {
            String line = lines.get(i);
            if (line.startsWith(field + ":")) {
                if (line.equals(field + ": " + value)) return text;
                lines.set(i, field + ": " + value);
                return String.join("\n", lines);
            }
            if (line.startsWith("Template:")) insertAt = i + 1;
        }
        lines.add(insertAt, field + ": " + value);
        return String.join("\n", lines);
    }

    private static String appendStanza(String text, String stanza) {
        StringBuilder builder = new StringBuilder();
        if (text.isEmpty()) {
            builder.append(stanza).append('\n');
            return builder.toString();
        }
        builder.append(text);
        if (!builder.toString().endsWith("\n")) builder.append('\n');
        builder.append('\n').append(stanza).append('\n');
        return builder.toString();
    }
}
