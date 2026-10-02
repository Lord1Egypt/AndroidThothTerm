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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * What an extracted tree actually is, one line per path, in the format of
 * {@code tests/garden-common/extractor/manifest.py}:
 * {@code path, d|f|l, mode (octal, 0777), size, sha256 or link target}.
 * Observed with java.nio and never through the operations under test; works
 * on the JVM and on Android (API 26+).
 */
public final class TreeManifest {
    private TreeManifest() {
    }

    public static List<String> of(File root) throws IOException {
        List<String> lines = new ArrayList<>();
        walk(root, "", lines);
        java.util.Collections.sort(lines, (a, b) -> key(a).compareTo(key(b)));
        return lines;
    }

    /** Lines of {@code expected} that differ from {@code actual}, both directions. */
    public static List<String> diff(List<String> expected, List<String> actual, int limit) {
        java.util.Set<String> e = new java.util.HashSet<>(expected);
        java.util.Set<String> a = new java.util.HashSet<>(actual);
        List<String> out = new ArrayList<>();
        for (String line : expected) {
            if (!a.contains(line) && out.size() < limit) out.add("missing/different: " + line);
        }
        for (String line : actual) {
            if (!e.contains(line) && out.size() < limit) out.add("unexpected/actual:  " + line);
        }
        return out;
    }

    private static String key(String line) {
        return line.substring(0, line.indexOf('\t'));
    }

    private static void walk(File dir, String rel, List<String> out) throws IOException {
        String[] names = dir.list();
        if (names == null) return;
        Arrays.sort(names);
        for (String name : names) {
            File file = new File(dir, name);
            Path path = file.toPath();
            String child = rel.isEmpty() ? name : rel + "/" + name;
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink()) {
                out.add(child + "\tl\t0\t0\t" + Files.readSymbolicLink(path));
            } else if (attrs.isDirectory()) {
                out.add(child + "\td\t" + Integer.toOctalString(mode(path)) + "\t0\t-");
                walk(file, child, out);
            } else if (attrs.isRegularFile()) {
                out.add(child + "\tf\t" + Integer.toOctalString(mode(path)) + "\t" + attrs.size()
                        + "\t" + sha256(path));
            } else {
                out.add(child + "\tOTHER\t0\t0\t-");
            }
        }
    }

    private static int mode(Path path) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        int mode = 0;
        for (PosixFilePermission p : perms) mode |= 0400 >> p.ordinal();
        return mode;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            } finally {
                in.close();
            }
            return RootfsArchive.toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static List<String> read(File manifest) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : new String(Files.readAllBytes(manifest.toPath()),
                Charset.forName("UTF-8")).split("\n")) {
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }
}
