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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thothterm.R;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Keep screen awake" is the window's FLAG_KEEP_SCREEN_ON and nothing else.
 * <p>
 * The device half -- the real flag, recreation, background, the display hold
 * in dumpsys power -- is {@code KeepScreenAwakeTest} under androidTest. This
 * half pins what must never come back: up to terminal-v1.3.0 the menu item
 * took a partial wake lock, which keeps the CPU awake and lets the display
 * sleep, and then sent the user to battery settings.
 */
public class ScreenAwakeTest {
    private static final File MAIN = new File("src/main");

    static final String[] FORBIDDEN = {
            "PARTIAL_WAKE_LOCK",
            "newWakeLock",
            "isIgnoringBatteryOptimizations",
            "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "ignore_battery_optimizations",
    };

    @Test
    public void theMenuNamesTheNextAction() {
        assertEquals(R.string.enable_keep_screen_on, ScreenAwake.menuTitle(false));
        assertEquals(R.string.disable_keep_screen_on, ScreenAwake.menuTitle(true));
    }

    @Test
    public void theLabelsSayWhatHappensToTheScreen() throws IOException {
        String strings = read(new File(MAIN, "res/values/strings.xml"));
        assertEquals("Keep screen awake", string(strings, "enable_keep_screen_on"));
        assertEquals("Allow screen to sleep", string(strings, "disable_keep_screen_on"));
    }

    @Test
    public void nothingKeepsTheCpuAwakeOrAsksForABatteryExemption() throws IOException {
        List<File> files = new ArrayList<>();
        collect(MAIN, files);
        assertTrue("no sources found under " + MAIN.getAbsolutePath(), files.size() > 10);
        for (File file : files) {
            String code = withoutComments(read(file));
            for (String token : FORBIDDEN) {
                assertFalse(file + " uses " + token, code.contains(token));
            }
        }
    }

    @Test
    public void theTerminalTogglesTheWindowFlag() throws IOException {
        String term = read(new File(MAIN, "java/jackpal/androidterm/Term.java"));
        assertTrue(term.contains("ScreenAwake.restore(getWindow(), icicle)"));
        assertTrue(term.contains("ScreenAwake.save(getWindow(), outState)"));
        assertTrue(term.contains("ScreenAwake.toggle(getWindow())"));
        assertTrue(term.contains("ScreenAwake.menuTitle(ScreenAwake.isOn(getWindow()))"));
        assertFalse(new File(MAIN, "java/com/thothterm/utils/WakeLock.java").exists());
    }

    @Test
    public void keepWifiOnKeepsItsLockAndPermission() throws IOException {
        // WifiManager.WifiLock.acquire() needs WAKE_LOCK; it is not a leftover.
        assertTrue(read(new File(MAIN, "AndroidManifest.xml"))
                .contains("android.permission.WAKE_LOCK"));
        String term = read(new File(MAIN, "java/jackpal/androidterm/Term.java"));
        assertTrue(term.contains("WifiLock.toggle(this)"));
        assertTrue(term.contains("R.id.menu_toggle_wifilock"));
        String strings = read(new File(MAIN, "res/values/strings.xml"));
        assertEquals("Keep Wi-Fi on", string(strings, "enable_wifilock"));
        assertEquals("Allow Wi-Fi to sleep", string(strings, "disable_wifilock"));
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

    private static String string(String xml, String name) {
        Matcher m = Pattern.compile("<string name=\"" + name + "\">([^<]*)</string>")
                .matcher(xml);
        assertTrue("no string " + name, m.find());
        return m.group(1);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
