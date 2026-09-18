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
 * Pure, idempotent text transforms for the guest Ubuntu account and managed
 * configuration. No Android APIs so every rule is unit-testable.
 *
 * <p>All transforms append or edit only the lines they own. Unrelated entries
 * in {@code /etc/passwd}, {@code /etc/group}, {@code /etc/shadow} and the
 * debconf databases are never rewritten, so an already-populated rootfs can be
 * upgraded in place. Running the transforms twice produces the same text.</p>
 */
final class GuestConfig {
    static final String USER = "thoth";
    static final int UID = 1000;
    static final int GID = 1000;
    static final String USER_HOME = "/home/thoth";
    static final String USER_SHELL = "/bin/bash";

    /** Bumped when managed guest configuration changes so it can be re-applied. */
    static final int RUNTIME_CONFIG_VERSION = 3;

    /** Exact text of the {@code auth sufficient pam_permit.so} line ThothTerm
     *  added to {@code /etc/pam.d/su} in runtime config v2. */
    private static final String MANAGED_PAM_PERMIT = "auth sufficient pam_permit.so";

    /** First line of the ThothTerm-managed {@code /usr/local/bin/sudo} helper. */
    private static final String MANAGED_SUDO_MARKER = "# Managed by ThothTerm";

    private GuestConfig() {
    }

    static String ensurePasswd(String text) {
        if (hasNamedLine(text, USER)) return text;
        return appendStanza(text, USER + ":x:" + UID + ":" + GID + ":Thoth User:"
                + USER_HOME + ":" + USER_SHELL);
    }

    static String ensureGroup(String text) {
        String result = text;
        if (!hasNamedLine(result, USER)) {
            result = appendStanza(result, USER + ":x:" + GID + ":");
        }
        return ensureMember(result, "sudo", USER);
    }

    static String ensureShadow(String text) {
        if (hasNamedLine(text, USER)) return text;
        return appendStanza(text, USER + ":!:19000:0:99999:7:::");
    }

    static String sudoersEntry() {
        return USER + " ALL=(ALL:ALL) NOPASSWD: ALL\n";
    }

    /**
     * Reverts the runtime-config-v2 {@code su} PAM customization. Real Ubuntu
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
