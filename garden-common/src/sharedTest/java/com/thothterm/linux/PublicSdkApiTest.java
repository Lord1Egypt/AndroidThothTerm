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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production code may only call public SDK members of android.system.Os and
 * OsConstants. Host compilation against Robolectric's android-all jar also
 * sees hidden platform methods, which is how a call to the hidden
 * Os.unlink(String) compiled here and failed the real SDK 36 build. The public
 * lists are in tests/garden-common/api/ with their source.
 */
public class PublicSdkApiTest {
    private static final String[] SOURCE_ROOTS = {
            "garden-common/src/main/java",
            "garden-common/src/shared/java",
            "garden-common/src/sharedAndroidTest/java",
            "garden-common/src/editionAndroidTest/java",
            "term-ubuntu/src/main/java",
            "term-ubuntu/src/androidTest/java",
            "garden-arch/src/main/java",
            "garden-debian/src/main/java",
            "libtermexec/src/main/java",
            "emulatorview/src/main/java",
    };

    @Test
    public void onlyPublicOsMethodsAreCalled() throws Exception {
        check("Os", Pattern.compile("\\bOs\\.([a-zA-Z_0-9]+)\\s*\\("));
    }

    @Test
    public void onlyPublicOsConstantsAreUsed() throws Exception {
        check("OsConstants", Pattern.compile("\\bOsConstants\\.([A-Za-z_0-9]+)"));
    }

    @Test
    public void theHiddenUnlinkIsNotPublic() throws Exception {
        // The list itself must keep catching the regression it was made for.
        Set<String> os = publicMembers("Os");
        assertTrue(os.contains("remove") && os.contains("lstat") && os.contains("open"));
        assertTrue("Os.unlink is hidden in the public SDK", !os.contains("unlink"));
    }

    private static void check(String cls, Pattern use) throws IOException {
        Set<String> allowed = publicMembers(cls);
        Set<String> violations = new TreeSet<>();
        int scanned = 0;
        for (String root : SOURCE_ROOTS) {
            File dir = new File(repo(), root);
            if (!dir.isDirectory()) continue;
            for (File file : javaFiles(dir)) {
                scanned++;
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                Matcher m = use.matcher(text);
                while (m.find()) {
                    if (!allowed.contains(m.group(1))) {
                        violations.add(cls + "." + m.group(1) + " in " + root + "/"
                                + file.getPath().substring(dir.getPath().length() + 1));
                    }
                }
            }
        }
        assertTrue("no sources scanned", scanned > 0);
        if (!violations.isEmpty()) fail("not public SDK API: " + violations);
    }

    private static Set<String> publicMembers(String cls) throws IOException {
        File list = new File(repo(), "tests/garden-common/api/android.system." + cls + ".txt");
        Set<String> names = new HashSet<>();
        for (String line : new String(Files.readAllBytes(list.toPath()), StandardCharsets.UTF_8)
                .split("\\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) names.add(line);
        }
        return names;
    }

    private static java.util.List<File> javaFiles(File dir) {
        java.util.List<File> out = new java.util.ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File child : children) {
            if (child.isDirectory()) out.addAll(javaFiles(child));
            else if (child.getName().endsWith(".java")) out.add(child);
        }
        return out;
    }

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }
}
