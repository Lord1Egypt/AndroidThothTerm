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
}
