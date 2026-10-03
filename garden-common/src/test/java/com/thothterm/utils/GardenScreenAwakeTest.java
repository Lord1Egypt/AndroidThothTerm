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

package com.thothterm.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen awake is not a CPU wake lock, in every Garden edition, present and
 * future.
 * <p>
 * An edition gets "Keep screen awake" from garden-common's Term and ScreenAwake
 * and may not bring its own; it also runs garden-common's device test,
 * {@code src/editionAndroidTest/java}, as part of its instrumentation suite.
 * A new edition that skips either step fails here. See "Keep screen awake" in
 * docs/garden/ARCHITECTURE.md.
 */
public class GardenScreenAwakeTest {
    private static final File ROOT = new File("..");
    private static final String DEVICE_TEST_DIR = "garden-common/src/editionAndroidTest/java";

    private static List<File> editions() {
        File[] dirs = ROOT.listFiles(f -> f.isDirectory() && f.getName().startsWith("garden-")
                && !f.getName().equals("garden-common"));
        assertNotNull("run from the garden-common module directory", dirs);
        List<File> editions = new ArrayList<>();
        for (File dir : dirs) {
            if (new File(dir, "build.gradle").isFile()) editions.add(dir);
        }
        assertFalse("no Garden edition found next to garden-common", editions.isEmpty());
        return editions;
    }

    @Test
    public void everyEditionTakesTheImplementationFromGardenCommon() throws IOException {
        for (File edition : editions()) {
            assertTrue(edition + " must build on garden-common",
                    read(new File(edition, "build.gradle")).contains("project(':garden-common')"));
            List<File> files = new ArrayList<>();
            collect(new File(edition, "src"), files);
            for (File file : files) {
                String name = file.getName();
                assertFalse(file + " replaces garden-common's terminal or screen-awake code",
                        name.equals("Term.java") || name.equals("ScreenAwake.java")
                                || name.equals("WakeLock.java"));
                assertFalse(file + " overrides the terminal menu",
                        file.getPath().endsWith("res/menu/main.xml".replace('/', File.separatorChar)));
                if (!file.getPath().contains(File.separator + "main" + File.separator)) continue;
                String code = withoutComments(read(file));
                for (String token : ScreenAwakeTest.FORBIDDEN) {
                    assertFalse(file + " uses " + token, code.contains(token));
                }
                assertFalse(file + " relabels the screen-awake action",
                        code.contains("name=\"enable_keep_screen_on\"")
                                || code.contains("name=\"disable_keep_screen_on\""));
            }
        }
    }

    @Test
    public void everyEditionRunsTheSharedDeviceTest() throws IOException {
        assertTrue(new File(ROOT, DEVICE_TEST_DIR + "/com/thothterm/KeepScreenAwakeTest.java")
                .isFile());
        assertTrue(new File(ROOT, DEVICE_TEST_DIR + "/com/thothterm/TerminalZoomGestureTest.java")
                .isFile());
        for (File edition : editions()) {
            assertTrue(edition + "/build.gradle must add " + DEVICE_TEST_DIR
                            + " to its androidTest sources",
                    read(new File(edition, "build.gradle")).contains(DEVICE_TEST_DIR));
        }
    }

    /** Shared unit tests too (the 16 KB alignment check), never a per-edition copy. */
    @Test
    public void everyEditionRunsTheSharedUnitTests() throws IOException {
        String dir = "garden-common/src/editionTest/java";
        assertTrue(new File(ROOT, dir + "/com/thothterm/linux/PageAlignmentTest.java").isFile());
        for (File edition : editions()) {
            assertTrue(edition + "/build.gradle must add " + dir + " to its test sources",
                    read(new File(edition, "build.gradle")).contains(dir));
            List<File> files = new ArrayList<>();
            collect(new File(edition, "src"), files);
            for (File file : files) {
                assertFalse(file + " copies a shared test",
                        file.getName().equals("PageAlignmentTest.java")
                                || file.getName().equals("TerminalZoomGestureTest.java")
                                || file.getName().equals("KeepScreenAwakeTest.java"));
            }
        }
    }

    @Test
    public void ubuntuAndGardenShareOneImplementation() throws IOException {
        // term-ubuntu is not on garden-common yet; until it is, the two copies
        // are the same file, like the PRoot patches.
        String[][] pairs = {
                {"term-ubuntu/src/main/java/com/thothterm/utils/ScreenAwake.java",
                        "garden-common/src/main/java/com/thothterm/utils/ScreenAwake.java"},
                {"term-ubuntu/src/test/java/com/thothterm/utils/ScreenAwakeTest.java",
                        "garden-common/src/test/java/com/thothterm/utils/ScreenAwakeTest.java"},
                {"term-ubuntu/src/androidTest/java/com/thothterm/KeepScreenAwakeTest.java",
                        DEVICE_TEST_DIR + "/com/thothterm/KeepScreenAwakeTest.java"},
                {"term-ubuntu/src/androidTest/java/com/thothterm/UploadFsDeviceTest.java",
                        DEVICE_TEST_DIR + "/com/thothterm/UploadFsDeviceTest.java"},
                {"term-ubuntu/src/main/java/com/thothterm/lan/LanUploads.java",
                        "garden-common/src/main/java/com/thothterm/lan/LanUploads.java"},
                {"term-ubuntu/patches/0005-keep-the-kernel-working-directory-in-step-with-the-guest.patch",
                        "garden-common/patches/0005-keep-the-kernel-working-directory-in-step-with-the-guest.patch"},
        };
        List<String[]> all = new ArrayList<>(java.util.Arrays.asList(pairs));
        // The upload core too: one implementation, its tests included.
        for (String set : new String[]{"src/main/java/com/thothterm/upload", "src/test/java/com/thothterm/upload"}) {
            String[] names = new File(ROOT, "garden-common/" + set).list();
            assertNotNull(names);
            assertTrue(names.length >= 4);
            for (String name : names) {
                all.add(new String[]{"term-ubuntu/" + set + "/" + name, "garden-common/" + set + "/" + name});
            }
        }
        for (String[] pair : all) {
            assertArrayEquals(pair[0] + " and " + pair[1] + " differ",
                    Files.readAllBytes(new File(ROOT, pair[0]).toPath()),
                    Files.readAllBytes(new File(ROOT, pair[1]).toPath()));
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                collect(child, out);
            } else if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".xml")) {
                out.add(child);
            }
        }
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
