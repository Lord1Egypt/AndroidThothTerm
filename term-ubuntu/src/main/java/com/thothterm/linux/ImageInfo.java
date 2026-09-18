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

/**
 * Provenance and identity of the embedded Ubuntu image. Sourced from the
 * generated {@code assets/ubuntu/image.properties}, which is derived from the
 * pinned {@code tools/prepare-assets.sh} constants.
 */
public final class ImageInfo {
    private final Properties properties;

    private ImageInfo(Properties properties) {
        this.properties = properties;
    }

    public static ImageInfo load(InputStream in) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(in);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        return new ImageInfo(properties);
    }

    private String get(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    public String imageId() {
        return get("imageId");
    }

    public String ubuntuVersion() {
        return get("ubuntuVersion");
    }

    public String architecture() {
        return get("architecture");
    }

    public String sourceUrl() {
        return get("sourceUrl");
    }

    public String filename() {
        return get("filename");
    }

    public String assetName() {
        String name = get("assetName");
        return name.isEmpty() ? get("filename") : name;
    }

    public String upstreamSha256() {
        return get("upstreamSha256");
    }

    public long compressedSize() {
        try {
            return Long.parseLong(get("compressedSize"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public int schemaVersion() {
        try {
            return Integer.parseInt(get("schemaVersion"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public String prootVersion() {
        return get("prootVersion");
    }
}
