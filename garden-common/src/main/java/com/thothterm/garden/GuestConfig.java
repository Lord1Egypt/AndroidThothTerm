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

package com.thothterm.garden;

/**
 * Pure, idempotent text transforms for the Garden guest account and managed
 * configuration. No Android APIs so every rule is unit-testable.
 *
 * <p>All transforms append or edit only the lines they own. Unrelated entries
 * in {@code /etc/passwd}, {@code /etc/group}, {@code /etc/shadow} and the
 * debconf databases are never rewritten, so an already-populated rootfs can be
 * upgraded in place. Running the transforms twice produces the same text.</p>
 */
final class GuestConfig {
    static final String USER = GardenDistro.USER;
    static final int UID = GardenDistro.UID;
    static final int GID = GardenDistro.GID;
    static final String USER_HOME = GardenDistro.USER_HOME;

    /** Bumped when Garden's managed guest configuration changes so it is re-applied. */
    static final int RUNTIME_CONFIG_VERSION = 1;

    private GuestConfig() {
    }

    static String ensurePasswd(String text, String shell) {
        if (hasNamedLine(text, USER)) return text;
        return appendStanza(text, USER + ":x:" + UID + ":" + GID + ":Thoth User:"
                + USER_HOME + ":" + shell);
    }

    static String ensureGroup(String text, String sudoGroup, int sudoGid) {
        String result = text;
        if (!hasNamedLine(result, USER)) {
            result = appendStanza(result, USER + ":x:" + GID + ":");
        }
        return ensureMember(result, sudoGroup, sudoGid, USER);
    }

    static String ensureShadow(String text) {
        if (hasNamedLine(text, USER)) return text;
        return appendStanza(text, USER + ":!:19000:0:99999:7:::");
    }

    private static final int AID_USER_OFFSET = 100000;
    private static final int AID_APP_START = 10000;
    private static final int AID_CACHE_START = 20000;
    private static final int AID_SHARED_START = 50000;

    static String sudoersEntry() {
        return USER + " ALL=(ALL:ALL) NOPASSWD: ALL\n";
    }

    /**
     * Ensures the loopback entries sudo and local tools require in
     * {@code /etc/hosts}, plus the Garden host name. Minimal images can ship the
     * file empty (Ubuntu Base does), and sudo fails with
     * "unable to resolve host" when its own host name is missing. Only missing
     * managed lines are appended; user entries are preserved and rerunning the
     * transform returns the same text.
     */
    static String ensureHosts(String text, String hostname) {
        StringBuilder result = new StringBuilder(text == null ? "" : text);
        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
            result.append('\n');
        }
        ensureHostEntry(result, "127.0.0.1", "localhost");
        ensureHostEntry(result, "::1", "localhost ip6-localhost ip6-loopback");
        ensureHostEntry(result, "127.0.1.1", hostname);
        return result.toString();
    }

    /**
     * Ensures the guest can name the Android supplementary groups its processes
     * already carry. An app's threads run with the fixed AIDs {@code inet} and
     * {@code everybody} plus two derived from its own uid, and a stock distro has
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

    private static String ensureMember(String text, String group, int gid, String member) {
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
        return appendStanza(text, group + ":x:" + gid + ":" + member);
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
