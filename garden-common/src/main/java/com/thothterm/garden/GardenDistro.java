/*
 * Copyright (C) 2026 ThothTerm.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Everything distro-specific about a Garden edition, read from the edition's
 * {@code assets/garden/distro.properties}. garden-common itself names no
 * distribution; it asks this class.
 *
 * <p>Parsing is free of Android APIs so it is unit-testable. A missing or
 * malformed required value is an error at load time rather than a surprise
 * during extraction.
 */
public final class GardenDistro {
    public static final String ASSET = "garden/distro.properties";

    /** The guest account every Garden edition creates. */
    public static final String USER = "thoth";
    public static final int UID = 1000;
    public static final int GID = 1000;
    public static final String USER_HOME = "/home/thoth";

    private final Properties properties;

    private GardenDistro(Properties properties) {
        this.properties = properties;
    }

    public static GardenDistro load(InputStream in) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(in);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        GardenDistro distro = new GardenDistro(properties);
        distro.validate();
        return distro;
    }

    private void validate() throws IOException {
        String[] required = {
                "distro.id", "distro.displayName", "distro.version", "distro.architecture",
                "edition.name", "rootfs.id", "rootfs.sha256", "rootfs.compressedSize",
                "rootfs.uncompressedSize", "guest.shell", "guest.su", "guest.sudo",
                "guest.sudoGroup", "guest.sudoGroupGid", "guest.hostname",
        };
        for (String key : required) {
            if (get(key).isEmpty()) throw new IOException("distro.properties lacks " + key);
        }
        if (!rootfsSha256().matches("[0-9a-f]{64}")) {
            throw new IOException("distro.properties has a malformed rootfs.sha256");
        }
        if (compressedSize() <= 0 || uncompressedSize() <= 0 || sudoGroupGid() <= 0) {
            throw new IOException("distro.properties has a malformed size or gid");
        }
        if (rootfsAsset().isEmpty() && rootfsUrl().isEmpty()) {
            throw new IOException("distro.properties names neither rootfs.asset nor rootfs.url");
        }
        if (!rootfsUrl().isEmpty() && !rootfsUrl().startsWith("https://")) {
            throw new IOException("rootfs.url must be https");
        }
    }

    private String get(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public String id() {
        return get("distro.id");
    }

    /** e.g. "Debian GNU/Linux". */
    public String displayName() {
        return get("distro.displayName");
    }

    /** e.g. "13.7". */
    public String version() {
        return get("distro.version");
    }

    /** e.g. "trixie"; may be empty for distros without codenames. */
    public String codename() {
        return get("distro.codename");
    }

    public String architecture() {
        return get("distro.architecture");
    }

    /** e.g. "ThothTerm Debian". */
    public String editionName() {
        return get("edition.name");
    }

    /** e.g. "Debian GNU/Linux 13.7 (trixie) ARM64". */
    public String releaseLine() {
        StringBuilder line = new StringBuilder(displayName()).append(' ').append(version());
        if (!codename().isEmpty()) line.append(" (").append(codename()).append(')');
        return line.append(' ').append(architecture().equals("arm64") ? "ARM64"
                : architecture()).toString();
    }

    public String rootfsId() {
        return get("rootfs.id");
    }

    /** Asset path of the embedded archive, or empty when this build embeds none. */
    public String rootfsAsset() {
        return get("rootfs.asset");
    }

    /** Pinned download location for builds without an embedded archive. */
    public String rootfsUrl() {
        return get("rootfs.url");
    }

    public String rootfsSha256() {
        return get("rootfs.sha256").toLowerCase(java.util.Locale.ROOT);
    }

    public long compressedSize() {
        return getLong("rootfs.compressedSize");
    }

    public long uncompressedSize() {
        return getLong("rootfs.uncompressedSize");
    }

    /** Bumped by the edition when managed guest configuration must be re-applied. */
    public int schemaVersion() {
        long value = getLong("rootfs.schema");
        return value > 0 ? (int) value : 1;
    }

    public String hostname() {
        return get("guest.hostname");
    }

    public String shell() {
        return get("guest.shell");
    }

    public String suPath() {
        return get("guest.su");
    }

    public String sudoPath() {
        return get("guest.sudo");
    }

    public String sudoGroup() {
        return get("guest.sudoGroup");
    }

    public int sudoGroupGid() {
        return (int) getLong("guest.sudoGroupGid");
    }
}
