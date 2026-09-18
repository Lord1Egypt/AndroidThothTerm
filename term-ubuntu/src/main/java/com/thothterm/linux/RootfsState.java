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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Completion manifest for an extracted rootfs. The presence of the rootfs
 * directory alone is never treated as proof of a valid installation.
 */
public final class RootfsState {
    public static final int SCHEMA_VERSION = 1;

    public String imageId = "";
    public String ubuntuVersion = "";
    public String architecture = "";
    public String imageSha256 = "";
    public int schemaVersion = SCHEMA_VERSION;
    public long installedAt;
    public boolean complete;

    public static RootfsState read(File file) {
        RootfsState state = new RootfsState();
        if (file == null || !file.isFile()) return state;

        Properties properties = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            properties.load(in);
        } catch (Exception e) {
            return state;
        } finally {
            closeQuietly(in);
        }

        state.imageId = properties.getProperty("imageId", "");
        state.ubuntuVersion = properties.getProperty("ubuntuVersion", "");
        state.architecture = properties.getProperty("architecture", "");
        state.imageSha256 = properties.getProperty("imageSha256", "");
        state.schemaVersion = parse(properties.getProperty("schemaVersion"), SCHEMA_VERSION);
        state.installedAt = parseLong(properties.getProperty("installedAt"), 0L);
        state.complete = Boolean.parseBoolean(properties.getProperty("complete", "false"));
        return state;
    }

    public void write(File file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("imageId", imageId);
        properties.setProperty("ubuntuVersion", ubuntuVersion);
        properties.setProperty("architecture", architecture);
        properties.setProperty("imageSha256", imageSha256);
        properties.setProperty("schemaVersion", Integer.toString(schemaVersion));
        properties.setProperty("installedAt", Long.toString(installedAt));
        properties.setProperty("complete", Boolean.toString(complete));

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create state directory: " + parent);
        }

        OutputStream out = new FileOutputStream(file);
        try {
            properties.store(out, "ThothTerm Ubuntu rootfs state");
        } finally {
            out.close();
        }
    }

    public boolean matches(ImageInfo image) {
        return complete
                && schemaVersion == image.schemaVersion()
                && imageId.equals(image.imageId())
                && imageSha256.equals(image.upstreamSha256());
    }

    private static int parse(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(String value, long def) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
        }
    }
}
