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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * What a Garden edition installs: its identity and the pinned rootfs archive.
 * Read from the edition's {@code assets/garden/distro.properties}, which the
 * edition's build checks against the archive it stages or publishes, so the
 * embedded and the downloaded userland are the same bytes.
 *
 * <p>Loading fails rather than defaulting: a missing digest or size would turn
 * the download check into no check at all.
 */
public final class DistroInfo {
    public static final String ASSET = "garden/distro.properties";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
    private static final Pattern GROUP = Pattern.compile("[a-z_][a-z0-9_-]{0,31}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9A-F]{40}");

    /** Debian-family guests: dpkg's status database, apt, debconf. */
    public static final String DPKG = "dpkg";
    /** Arch-family guests: pacman's local database and its own keyring. */
    public static final String PACMAN = "pacman";

    /** A trust anchor list longer than this is a configuration mistake. */
    private static final int MAX_TRUST_ANCHORS = 8;
    /** The optional repository's keyring package and signature are small; more is a mistake. */
    private static final long MAX_EXTRA_FILE = 1024 * 1024;

    private final Properties properties;

    private DistroInfo(Properties properties) {
        this.properties = properties;
    }

    public static DistroInfo load(InputStream in) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new java.io.InputStreamReader(in, "UTF-8"));
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        DistroInfo info = new DistroInfo(properties);
        info.validate();
        return info;
    }

    private void validate() throws IOException {
        require("editionName", printable(editionName()));
        require("distroName", printable(distroName()));
        require("distroVersion", printable(distroVersion()));
        require("distroDir", NAME.matcher(distroDir()).matches());
        require("imageId", NAME.matcher(imageId()).matches());
        require("assetName", NAME.matcher(assetName()).matches());
        require("architecture", !architecture().isEmpty());
        require("sourceUrl", sourceUrl().startsWith("https://"));
        require("sha256", SHA256.matcher(sha256()).matches());
        require("compressedSize", compressedSize() > 0);
        require("uncompressedSize", uncompressedSize() > 0);
        require("lanPort", lanPort() >= 1024 && lanPort() <= 65535);
        require("sudoBinary", sudoBinary().startsWith("usr/")
                && !sudoBinary().contains(".."));
        require("packageManager", packageManager().equals(DPKG) || packageManager().equals(PACMAN));
        require("adminGroup", GROUP.matcher(adminGroup()).matches());
        if (packageManager().equals(PACMAN)) {
            // Without these the first run could not prove that the keyring it
            // creates trusts the distribution's packages.
            require("pacmanKeyring", NAME.matcher(pacmanKeyring()).matches());
            require("packageSigningKey", FINGERPRINT.matcher(packageSigningKey()).matches());
        }
        if (hasExtraRepo()) {
            require("packageManager", packageManager().equals(PACMAN));
            require("extraRepo", NAME.matcher(extraRepo()).matches());
            require("extraRepoLabel", printable(extraRepoLabel()));
            require("extraRepoSite", extraRepoSite().startsWith("https://"));
            require("extraRepoServer", extraRepoServer().startsWith("https://"));
            require("extraRepoKeyring", NAME.matcher(extraRepoKeyring()).matches());
            for (String key : new String[] {"extraRepoKeyringUrl", "extraRepoKeyringSigUrl"}) {
                require(key, get(key).startsWith("https://"));
            }
            require("extraRepoKeyringSha256", SHA256.matcher(extraRepoKeyringSha256()).matches());
            require("extraRepoKeyringSigSha256", SHA256.matcher(extraRepoKeyringSigSha256()).matches());
            require("extraRepoKeyringSize", extraRepoKeyringSize() > 0 && extraRepoKeyringSize() <= MAX_EXTRA_FILE);
            require("extraRepoKeyringSigSize", extraRepoKeyringSigSize() > 0 && extraRepoKeyringSigSize() <= MAX_EXTRA_FILE);
            require("extraRepoTrusted", validList(extraRepoTrusted()));
            require("extraRepoRevoked", extraRepoRevoked().isEmpty() || validList(extraRepoRevoked()));
            require("extraRepoSigner", extraRepoTrusted().contains(extraRepoSigner()));
            for (String key : extraRepoRevoked()) require("extraRepoRevoked", !extraRepoTrusted().contains(key));
            // One origin: the pinned files and the repository come from the same site.
            String host = host(extraRepoSite());
            for (String url : new String[] {extraRepoServer(), extraRepoKeyringUrl(), extraRepoKeyringSigUrl()}) {
                require("extraRepo host", host.equals(host(url)));
            }
        }
    }

    private static String host(String url) {
        try {
            String h = new java.net.URI(url.replace("$", "")).getHost();
            return h == null ? "" : h.toLowerCase(java.util.Locale.ROOT);
        } catch (java.net.URISyntaxException e) {
            return "";
        }
    }

    /** At most {@link #MAX_TRUST_ANCHORS} distinct fingerprints. */
    private static boolean validList(java.util.List<String> values) {
        if (values.isEmpty() || values.size() > MAX_TRUST_ANCHORS) return false;
        if (new java.util.HashSet<>(values).size() != values.size()) return false;
        for (String value : values) {
            if (!FINGERPRINT.matcher(value).matches()) return false;
        }
        return true;
    }

    /** Shown in the UI and written into the guest as one line of text. */
    private static boolean printable(String value) {
        if (value.isEmpty() || value.length() > 80) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return false;
        }
        return true;
    }

    private static void require(String key, boolean valid) throws IOException {
        if (!valid) throw new IOException("Invalid or missing " + key + " in " + ASSET);
    }

    private String get(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** The product name shown to the user, e.g. in the notification. */
    public String editionName() {
        return get("editionName");
    }

    public String distroName() {
        return get("distroName");
    }

    public String distroVersion() {
        return get("distroVersion");
    }

    /** Directory under {@code files/linux} that holds this edition's rootfs. */
    public String distroDir() {
        return get("distroDir");
    }

    public String imageId() {
        return get("imageId");
    }

    public String architecture() {
        return get("architecture");
    }

    /** Where a build without an embedded rootfs downloads it from. */
    public String sourceUrl() {
        return get("sourceUrl");
    }

    /** File name of the archive, embedded under {@code assets/garden/rootfs}. */
    public String assetName() {
        return get("assetName");
    }

    public String sha256() {
        return get("sha256").toLowerCase(java.util.Locale.ROOT);
    }

    public long compressedSize() {
        return getLong("compressedSize");
    }

    public long uncompressedSize() {
        return getLong("uncompressedSize");
    }

    public int schemaVersion() {
        long value = getLong("schemaVersion");
        return value > 0 ? (int) value : 1;
    }

    /** First port LAN Mode tries; editions differ so they can run side by side. */
    public int lanPort() {
        return (int) getLong("lanPort");
    }

    /** The real sudo binary, relative to the rootfs, that needs its setuid bit. */
    public String sudoBinary() {
        return get("sudoBinary");
    }

    /** {@link #DPKG} (the default) or {@link #PACMAN}. */
    public String packageManager() {
        String value = get("packageManager");
        return value.isEmpty() ? DPKG : value;
    }

    /** The group administrators belong to: "sudo" by default, "wheel" on Arch. */
    public String adminGroup() {
        String value = get("adminGroup");
        return value.isEmpty() ? "sudo" : value;
    }

    /** pacman only: the keyring {@code pacman-key --populate} loads, e.g. "archlinux". */
    public String pacmanKeyring() {
        return get("pacmanKeyring");
    }

    /** pacman only: fingerprint of the key the distribution signs its packages with. */
    public String packageSigningKey() {
        return get("packageSigningKey").toUpperCase(java.util.Locale.ROOT);
    }

    // ---- optional third-party repository --------------------------------------
    // A pacman repository the user may enable after explicit consent. Nothing of
    // it ships in the rootfs or the APK: the app downloads its keyring package
    // from the repository's own site, over HTTPS, and accepts exactly the file
    // pinned here (size and SHA-256, plus its detached signature's), then proves
    // the keys it imports. All values are data for one app release.

    /** True when this edition offers the optional repository. */
    public boolean hasExtraRepo() {
        return !get("extraRepo").isEmpty();
    }

    /** The pacman section name. */
    public String extraRepo() {
        return get("extraRepo");
    }

    /** Factual name shown on the opt-in entry and the consent screen. */
    public String extraRepoLabel() {
        return get("extraRepoLabel");
    }

    /** The repository project's own site, https. */
    public String extraRepoSite() {
        return get("extraRepoSite");
    }

    /** The pacman {@code Server} line, with {@code $repo} and {@code $arch}. */
    public String extraRepoServer() {
        return get("extraRepoServer");
    }

    /** The keyring name {@code pacman-key --populate} takes; the package is {@code <name>-keyring}. */
    public String extraRepoKeyring() {
        return get("extraRepoKeyring");
    }

    public String extraRepoKeyringUrl() {
        return get("extraRepoKeyringUrl");
    }

    public String extraRepoKeyringSha256() {
        return get("extraRepoKeyringSha256").toLowerCase(java.util.Locale.ROOT);
    }

    public long extraRepoKeyringSize() {
        return getLong("extraRepoKeyringSize");
    }

    public String extraRepoKeyringSigUrl() {
        return get("extraRepoKeyringSigUrl");
    }

    public String extraRepoKeyringSigSha256() {
        return get("extraRepoKeyringSigSha256").toLowerCase(java.util.Locale.ROOT);
    }

    public long extraRepoKeyringSigSize() {
        return getLong("extraRepoKeyringSigSize");
    }

    /** The only keys the repository's keyring may leave fully trusted, upper case. */
    public java.util.List<String> extraRepoTrusted() {
        return upperList("extraRepoTrusted");
    }

    /** Keys the keyring must leave revoked and untrusted, upper case. */
    public java.util.List<String> extraRepoRevoked() {
        return upperList("extraRepoRevoked");
    }

    /** The trusted key that must have signed the keyring package. */
    public String extraRepoSigner() {
        return get("extraRepoSigner").toUpperCase(java.util.Locale.ROOT);
    }

    private java.util.List<String> upperList(String key) {
        String value = get(key);
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (!value.isEmpty()) {
            for (String k : value.split("\\s+")) keys.add(k.toUpperCase(java.util.Locale.ROOT));
        }
        return java.util.Collections.unmodifiableList(keys);
    }
}
