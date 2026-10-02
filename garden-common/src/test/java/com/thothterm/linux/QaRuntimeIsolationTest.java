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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QA VARIANT RUNTIME ISOLATION: the application id of a QA build is the id
 * its native runtime is built for, and nothing in it points at a production
 * package's private storage.
 *
 * <ul>
 *   <li>Each edition has one application id (applicationId.gradle), used by
 *   every variant, by the PRoot runtime build and by term-ubuntu's CMake
 *   code; no build type or flavour changes it.</li>
 *   <li>build-proot.sh compiles /data/data/&lt;that id&gt;/files/linux/runtime
 *   in, never defaults the id, and verifies every runtime file with
 *   check-runtime-ids.sh (the device gate checks the APK the same way).</li>
 *   <li>The Java runtime derives every path from the app's own Context: no
 *   production id or /data/data path is written into Java or assets.</li>
 * </ul>
 */
public class QaRuntimeIsolationTest {
    private static final String[][] EDITIONS = {
            {"garden-arch", "com.thothterm.arch"},
            {"garden-debian", "com.thothterm.debian"},
            {"term-ubuntu", "com.thothterm.ubuntu"},
    };
    private static final String[] BUILD_PROOT = {
            "garden-common/tools/build-proot.sh",
            "term-ubuntu/tools/build-proot.sh",
    };
    private static final Pattern PROTECTED_LITERAL = Pattern.compile(
            "\"(com\\.thothterm(\\.(arch|debian|ubuntu|devel))?)\"");
    private static final Pattern STORAGE_PATH = Pattern.compile(
            "/data/(data|user/\\d+)/com\\.thothterm");

    @Test
    public void everyVariantAndTheRuntimeShareOneApplicationId() throws Exception {
        String ids = read("applicationId.gradle");
        assertTrue(ids.contains("findProperty('thothtermQaApplicationIdSuffix')"));
        assertTrue("the suffix is validated", ids.contains("/\\.qa\\.[a-z][a-z0-9]*/"));
        // An ext property named like the -P property would shadow it: findProperty
        // would return the closure and every build would fail.
        assertFalse(ids.contains("ext.thothtermQaApplicationIdSuffix"));
        assertTrue(read("build.gradle").contains("apply from: 'applicationId.gradle'"));
        for (String[] edition : EDITIONS) {
            String path = edition[0] + "/build.gradle";
            String gradle = withoutComments(read(path));
            assertTrue(path, gradle.contains("thothtermApplicationId('" + edition[1] + "')"));
            assertFalse(path + " sets a literal application id",
                    Pattern.compile("(?m)^\\s*applicationId\\s+\"").matcher(gradle).find());
            assertFalse(path + " lets a variant change the id the runtime is built for",
                    Pattern.compile("(?m)^\\s*applicationIdSuffix\\b").matcher(gradle).find());
            assertTrue(path + " builds PRoot for its application id", gradle.contains(
                    "environment 'THOTHTERM_APPLICATION_ID', android.defaultConfig.applicationId"));
            assertTrue(path + " rebuilds PRoot when the id changes", gradle.contains(
                    "inputs.property 'applicationId', android.defaultConfig.applicationId"));
            assertTrue(path, gradle.contains(
                    "inputs.file rootProject.file('garden-common/tools/check-runtime-ids.sh')"));
        }
        String ubuntu = read("term-ubuntu/build.gradle");
        assertTrue(ubuntu.contains(
                "def ubuntuApplicationId = rootProject.ext.thothtermApplicationId('com.thothterm.ubuntu')"));
        assertTrue(ubuntu.contains("applicationId ubuntuApplicationId"));
        assertTrue("CMake's PACKAGE_NAME is the same id",
                ubuntu.contains("\"-DAPPLICATION_ID:STRING=${ubuntuApplicationId}\""));
    }

    @Test
    public void theRuntimeIsBuiltForThatIdAndVerified() throws Exception {
        for (String path : BUILD_PROOT) {
            String script = read(path);
            assertTrue(path, script.contains("APP_ID=\"${THOTHTERM_APPLICATION_ID:?"));
            assertFalse(path + " defaults the application id",
                    script.contains("THOTHTERM_APPLICATION_ID:-"));
            assertTrue(path, script.contains("RUNTIME_DIR=\"/data/data/${APP_ID}/files/linux/runtime\""));
            // What is compiled in is exactly what is then required.
            assertTrue(path, script.contains("-D_PATH_TMP=\"\\\"$RUNTIME_DIR/tmp/\\\"\""));
            assertTrue(path, script.contains("PROOT_UNBUNDLE_LOADER=\"$RUNTIME_DIR/loader\""));
            assertTrue(path, script.contains(
                    "sh \"$CHECK_IDS\" \"$APP_ID\" \"$PROOT_BIN\" \"$RUNTIME_DIR/loader\""));
            assertTrue(path, script.contains(
                    "sh \"$CHECK_IDS\" \"$APP_ID\" \"$BUILD_DIR/lib/libandroid-shmem.so\" \"$RUNTIME_DIR/tmp/\""));
            assertTrue(path, script.contains("sh \"$CHECK_IDS\" \"$APP_ID\" \"$LOADER_BIN\""));
            assertTrue(path, script.contains(
                    "sh \"$CHECK_IDS\" \"$APP_ID\" \"$BUILD_DIR/lib/libtalloc.so.2\""));
            assertFalse(path + " names a production package",
                    Pattern.compile("com\\.thothterm\\.(arch|debian|ubuntu)").matcher(script).find());
        }
        String gate = read("tests/garden-common/extractor/device-gate.sh");
        assertTrue(gate.contains("gate_check_runtime_ids \"$PKG\" \"$APK\""));
    }

    @Test
    public void everyGuardProtectsTheSamePackages() throws Exception {
        TreeSet<String> gradle = ids(read("applicationId.gradle"), "thothtermProtectedApplicationIds = \\[([^\\]]*)\\]");
        TreeSet<String> checker = ids(read("garden-common/tools/check-runtime-ids.sh"), "PROTECTED=\"([^\"]*)\"");
        TreeSet<String> gate = ids(read("tests/garden-common/extractor/apk-identity.sh"),
                "GATE_PROTECTED_PACKAGES=\"([^\"]*)\"");
        assertEquals(5, gradle.size());
        assertEquals(gradle, checker);
        assertEquals(gradle, gate);
    }

    @Test
    public void theJavaRuntimeNamesNoProductionPackageOrPath() throws Exception {
        List<File> roots = new ArrayList<>();
        for (String root : new String[]{"garden-common/src/main", "garden-common/src/shared",
                "garden-arch/src/main", "garden-debian/src/main", "term-ubuntu/src/main"}) {
            roots.add(new File(repo(), root));
        }
        List<String> found = new ArrayList<>();
        for (File root : roots) scan(root, found);
        assertEquals("production ids or storage paths in app code: " + found, 0, found.size());
        // The runtime is pointed at the app's own files on every launch.
        for (String runtime : new String[]{"garden-common/src/main/java/com/thothterm/linux/GardenRuntime.java",
                "term-ubuntu/src/main/java/com/thothterm/linux/UbuntuRuntime.java"}) {
            String source = read(runtime);
            assertTrue(runtime, source.contains("env.put(\"PROOT_LOADER\", loaderPath);"));
            assertTrue(runtime, source.contains("env.put(\"PROOT_TMP_DIR\", prootTmpDir);"));
        }
    }

    @Test
    public void theRuntimeIdCheckerRefusesAProductionRuntimeInAQaBuild() throws Exception {
        assumeTrue("sh is required", new File("/bin/sh").canExecute());
        File script = new File(repo(), "tests/garden-common/qa/runtime-ids-selftest.sh");
        ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        assertEquals(new String(out.toByteArray(), StandardCharsets.UTF_8), 0, p.waitFor());
    }

    private static void scan(File file, List<String> found) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) scan(child, found);
            return;
        }
        String name = file.getName();
        boolean java = name.endsWith(".java");
        if (!java && !(name.endsWith(".sh") || name.endsWith(".properties") || name.endsWith(".xml")
                || name.endsWith(".conf") || name.indexOf('.') < 0)) {
            return;
        }
        if (file.length() > 1_000_000) return;
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String code = java ? withoutComments(text) : text;
        if (STORAGE_PATH.matcher(code).find()) found.add(file + ": /data/.../com.thothterm path");
        if (java) {
            Matcher m = PROTECTED_LITERAL.matcher(code);
            while (m.find()) found.add(file + ": \"" + m.group(1) + "\"");
        }
    }

    private static TreeSet<String> ids(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        assertTrue(regex, m.find());
        TreeSet<String> ids = new TreeSet<>();
        for (String token : m.group(1).split("[\\s,']+")) {
            if (!token.isEmpty()) ids.add(token);
        }
        return ids;
    }

    /** Drops // and block comments (good enough for these files: no "//" in their strings). */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(repo(), path).toPath()), StandardCharsets.UTF_8);
    }

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }
}
