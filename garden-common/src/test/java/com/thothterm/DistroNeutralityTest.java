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


package com.thothterm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * garden-common is shared by every edition, so nothing it shows or writes may
 * name one: product and distribution names live in the edition modules.
 * Comments may cite where something was measured, and DEBIAN_FRONTEND is
 * dpkg's own variable, used by every dpkg-based guest.
 */
public class DistroNeutralityTest {
    private static final String[] NAMES = {"debian", "ubuntu", "canonical ltd", "trixie",
            "arch linux", "archlinux", "thothterm rolling"};

    private static boolean isComment(String line) {
        String t = line.trim();
        return t.startsWith("#") || t.startsWith("*") || t.startsWith("//")
                || t.startsWith("/*") || t.startsWith("<!--");
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out);
            else out.add(child);
        }
    }

    @Test
    public void nothingNamesAnEdition() throws Exception {
        List<File> files = new ArrayList<>();
        collect(new File("src/main"), files);
        assertTrue("run from the module directory", files.size() > 50);
        List<String> hits = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            // Generated third-party bundle and binary assets.
            if (name.equals("xterm.js") || name.endsWith(".woff2") || name.endsWith(".ttf")
                    || name.endsWith(".png") || name.endsWith(".webp")) continue;
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isComment(line)) continue;
                String lower = line.replace("DEBIAN_FRONTEND", "").toLowerCase(Locale.ROOT);
                for (String n : NAMES) {
                    if (lower.contains(n)) hits.add(file.getPath() + ":" + (i + 1) + ": " + line.trim());
                }
            }
        }
        assertTrue("garden-common names an edition:\n" + String.join("\n", hits), hits.isEmpty());
    }
}
