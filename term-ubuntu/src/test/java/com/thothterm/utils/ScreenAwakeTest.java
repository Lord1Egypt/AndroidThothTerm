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
 * "Keep screen awake" -- by hand, or while charging -- is the window's
 * FLAG_KEEP_SCREEN_ON and nothing else.
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
            "SCREEN_OFF_TIMEOUT",
            "screen_off_timeout",
            "FULL_WAKE_LOCK",
            "SCREEN_BRIGHT_WAKE_LOCK",
            "STAY_ON_WHILE_PLUGGED_IN",
    };

    @Test
    public void theScreenStaysOnForTheManualChoiceOrForChargingWithTheSettingOn() {
        // manual, setting, pluggedIn -> keep on
        assertFalse(ScreenAwake.keepOn(false, false, false));
        assertFalse(ScreenAwake.keepOn(false, false, true));
        assertFalse(ScreenAwake.keepOn(false, true, false));
        assertTrue(ScreenAwake.keepOn(false, true, true));
        assertTrue(ScreenAwake.keepOn(true, false, false));
        assertTrue(ScreenAwake.keepOn(true, false, true));
        assertTrue(ScreenAwake.keepOn(true, true, false));
        assertTrue(ScreenAwake.keepOn(true, true, true));
    }

    @Test
    public void theMenuNeverClaimsTheScreenMaySleepWhileItCannot() {
        for (boolean manual : new boolean[]{false, true}) {
            for (boolean setting : new boolean[]{false, true}) {
                for (boolean plugged : new boolean[]{false, true}) {
                    ScreenAwake.Menu menu = ScreenAwake.menu(manual, setting, plugged);
                    boolean on = ScreenAwake.keepOn(manual, setting, plugged);
                    // "Keep screen awake" only while nothing keeps it on.
                    assertEquals(!on, menu == ScreenAwake.Menu.KEEP_AWAKE);
                    if (manual) assertEquals(ScreenAwake.Menu.ALLOW_SLEEP, menu);
                }
            }
        }
        // Turning the manual choice off while charging leaves the charging reason on.
        assertEquals(ScreenAwake.Menu.AWAKE_WHILE_CHARGING, ScreenAwake.menu(false, true, true));
        assertTrue(ScreenAwake.keepOn(false, true, true));
    }

    @Test
    public void theMenuNamesTheNextAction() {
        assertEquals(R.string.enable_keep_screen_on, ScreenAwake.menuTitle(ScreenAwake.Menu.KEEP_AWAKE));
        assertEquals(R.string.disable_keep_screen_on, ScreenAwake.menuTitle(ScreenAwake.Menu.ALLOW_SLEEP));
        assertEquals(R.string.keep_screen_on_while_charging_active,
                ScreenAwake.menuTitle(ScreenAwake.Menu.AWAKE_WHILE_CHARGING));
    }

    @Test
    public void everyKindOfPowerCounts() {
        assertFalse(ScreenAwake.isPluggedIn(0));
        assertFalse(ScreenAwake.isPluggedIn(-1));
        assertTrue(ScreenAwake.isPluggedIn(1)); // AC
        assertTrue(ScreenAwake.isPluggedIn(2)); // USB
        assertTrue(ScreenAwake.isPluggedIn(4)); // wireless
        assertTrue(ScreenAwake.isPluggedIn(8)); // dock
    }

    @Test
    public void theChargingSettingIsOnByDefault() throws IOException {
        assertTrue(ScreenAwake.PREF_WHILE_CHARGING_DEFAULT);
        String prefs = read(new File(MAIN, "res/xml/preferences.xml"));
        Matcher m = Pattern.compile("<androidx.preference.SwitchPreferenceCompat\\s+android:defaultValue=\"(\\w+)\"\\s+android:key=\""
                + ScreenAwake.PREF_WHILE_CHARGING + "\"").matcher(prefs);
        assertTrue("no switch for " + ScreenAwake.PREF_WHILE_CHARGING, m.find());
        assertEquals("true", m.group(1));
    }

    @Test
    public void theLabelsSayWhatHappensToTheScreen() throws IOException {
        String strings = read(new File(MAIN, "res/values/strings.xml"));
        assertEquals("Keep screen awake", string(strings, "enable_keep_screen_on"));
        assertEquals("Allow screen to sleep", string(strings, "disable_keep_screen_on"));
        assertEquals("Screen awake while charging", string(strings, "keep_screen_on_while_charging_active"));
        assertEquals("Keep screen awake while charging",
                string(strings, "title_keep_screen_on_while_charging_preference"));
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
    public void powerIsWatchedOnlyWhileTheTerminalIsStartedAndNeverThroughAnExportedReceiver()
            throws IOException {
        String awake = read(new File(MAIN, "java/com/thothterm/utils/ScreenAwake.java"));
        assertTrue(awake.contains("Intent.ACTION_BATTERY_CHANGED"));
        assertTrue(awake.contains("Context.RECEIVER_NOT_EXPORTED"));
        assertTrue(awake.contains("activity.unregisterReceiver(receiver)"));
        String manifest = read(new File(MAIN, "AndroidManifest.xml"));
        assertFalse(manifest.contains("BATTERY_CHANGED"));
        assertFalse(manifest.contains("ACTION_POWER_CONNECTED"));
    }

    @Test
    public void theTerminalFollowsItsLifecycle() throws IOException {
        String term = read(new File(MAIN, "java/jackpal/androidterm/Term.java"));
        assertTrue(term.contains("mScreenAwake = new ScreenAwake(this, icicle)"));
        assertTrue(term.contains("mScreenAwake.save(outState)"));
        assertTrue(term.contains("mScreenAwake.start()"));
        assertTrue(term.contains("mScreenAwake.stop()"));
        assertTrue(term.contains("mScreenAwake.toggleManual()"));
        assertTrue(term.contains("mScreenAwake.onPreferencesChanged()"));
        assertTrue(term.contains("ScreenAwake.menuTitle(mScreenAwake.menu())"));
        int onStart = term.indexOf("protected void onStart()");
        int onStop = term.indexOf("protected void onStop()");
        assertTrue(term.indexOf("mScreenAwake.start()") > onStart);
        assertTrue(term.indexOf("mScreenAwake.stop()") > onStop);
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
