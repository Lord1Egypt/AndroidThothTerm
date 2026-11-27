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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thothterm.R;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Keep CPU awake" -- the partial wake lock with its three modes -- on the
 * JVM: the rule that decides when the lock is held, the grace after the sound
 * stops, the lock following what is wanted without ever being taken or
 * dropped twice, and the notification naming the next action and the reason.
 * The real {@code PowerManager} lock, the service and the notification are
 * the device's half.
 */
public class CpuWakeLockTest {
    private static final File MAIN = new File("src/main");

    /** Counts every call, so a double acquire or release shows up. */
    private static class FakeHolder implements CpuWakeLock.Holder {
        int acquires;
        int releases;
        boolean held;

        @Override
        public void acquire() {
            acquires++;
            held = true;
        }

        @Override
        public void release() {
            releases++;
            held = false;
        }

        @Override
        public boolean isHeld() {
            return held;
        }
    }

    @Test
    public void whileAudioByDefaultAndTheModesAreThree() {
        assertEquals("keep_cpu_awake_mode", CpuWakeLock.PREF_KEY);
        assertEquals("while_audio", CpuWakeLock.PREF_DEFAULT);
        assertArrayEquals(new String[]{"off", "while_audio", "always"}, CpuWakeLock.MODES);
        assertEquals("ThothTerm:KeepAwake", CpuWakeLock.TAG);
        assertEquals(10000, CpuWakeLock.AUDIO_GRACE_MS);
        // Unknown or missing values fall back to the default; known ones are kept.
        assertEquals("while_audio", CpuWakeLock.validMode(null));
        assertEquals("while_audio", CpuWakeLock.validMode("true"));
        assertEquals("off", CpuWakeLock.validMode("off"));
        assertEquals("always", CpuWakeLock.validMode("always"));
        assertEquals(0, CpuWakeLock.modeIndex("off"));
        assertEquals(1, CpuWakeLock.modeIndex("while_audio"));
        assertEquals(2, CpuWakeLock.modeIndex("always"));
        assertEquals(1, CpuWakeLock.modeIndex("nonsense"));
    }

    @Test
    public void theLockIsHeldAlwaysNeverOrWhileSoundPlaysPlusTheGrace() {
        for (boolean active : new boolean[]{false, true}) {
            for (long idle : new long[]{-1, 0, 5000}) {
                assertTrue(CpuWakeLock.shouldHold("always", active, idle, 5000));
                assertFalse(CpuWakeLock.shouldHold("off", active, idle, 5000));
            }
        }
        // While sound plays: held whatever happened before.
        assertTrue(CpuWakeLock.shouldHold("while_audio", true, -1, 0));
        assertTrue(CpuWakeLock.shouldHold("while_audio", true, 0, 100000));
        // Sound never played: not held.
        assertFalse(CpuWakeLock.shouldHold("while_audio", false, -1, 0));
        assertFalse(CpuWakeLock.shouldHold("while_audio", false, -1, 100000));
        // Sound stopped at 1000: held until 11000, not at or after.
        assertTrue(CpuWakeLock.shouldHold("while_audio", false, 1000, 1000));
        assertTrue(CpuWakeLock.shouldHold("while_audio", false, 1000, 10999));
        assertFalse(CpuWakeLock.shouldHold("while_audio", false, 1000, 11000));
        assertFalse(CpuWakeLock.shouldHold("while_audio", false, 1000, 60000));
        // The grace is what remains; nothing to wait for otherwise.
        assertEquals(10000, CpuWakeLock.nextCheckDelayMillis("while_audio", false, 1000, 1000));
        assertEquals(1, CpuWakeLock.nextCheckDelayMillis("while_audio", false, 1000, 10999));
        assertEquals(-1, CpuWakeLock.nextCheckDelayMillis("while_audio", false, 1000, 11000));
        assertEquals(-1, CpuWakeLock.nextCheckDelayMillis("while_audio", true, 1000, 1000));
        assertEquals(-1, CpuWakeLock.nextCheckDelayMillis("while_audio", false, -1, 1000));
        assertEquals(-1, CpuWakeLock.nextCheckDelayMillis("always", false, 1000, 1000));
        assertEquals(-1, CpuWakeLock.nextCheckDelayMillis("off", false, 1000, 1000));
    }

    @Test
    public void theNotificationButtonGoesBetweenAlwaysAndTheDefault() {
        assertEquals("always", CpuWakeLock.toggledMode("while_audio"));
        assertEquals("always", CpuWakeLock.toggledMode("off"));
        assertEquals("while_audio", CpuWakeLock.toggledMode("always"));
    }

    @Test
    public void theLockFollowsWhatIsWantedAndIsNeverTakenOrDroppedTwice() {
        FakeHolder holder = new FakeHolder();
        CpuWakeLock lock = new CpuWakeLock(holder);
        assertTrue(lock.exists());
        assertFalse(lock.isHeld());

        // Off stays off: nothing to release.
        assertFalse(lock.apply(false));
        assertEquals(0, holder.releases);

        assertTrue(lock.apply(true));
        assertTrue(lock.isHeld());
        assertEquals(1, holder.acquires);
        // Asking again -- the listener and the toggle both call apply -- is a no-op.
        assertTrue(lock.apply(true));
        assertEquals(1, holder.acquires);

        assertFalse(lock.apply(false));
        assertFalse(lock.isHeld());
        assertEquals(1, holder.releases);
        assertFalse(lock.apply(false));
        assertEquals(1, holder.releases);

        // Round trips keep acquires and releases paired.
        for (int i = 0; i < 3; i++) {
            lock.apply(true);
            lock.apply(false);
        }
        assertEquals(holder.acquires, holder.releases);
        assertFalse(holder.held);
    }

    @Test
    public void releaseDropsTheLockWhateverWasWantedAndIsSafeToRepeat() {
        FakeHolder holder = new FakeHolder();
        CpuWakeLock lock = new CpuWakeLock(holder);
        lock.apply(true);
        lock.release();
        lock.release();
        assertFalse(lock.isHeld());
        assertEquals(1, holder.acquires);
        assertEquals(1, holder.releases);
    }

    @Test
    public void aDeviceWithoutAPowerManagerHoldsNothingAndNeverFails() {
        CpuWakeLock lock = new CpuWakeLock((CpuWakeLock.Holder) null);
        assertFalse(lock.exists());
        assertFalse(lock.apply(true));
        assertFalse(lock.isHeld());
        lock.release();
        assertFalse(lock.isHeld());
    }

    @Test
    public void theNotificationOffersTheOtherStateAndSaysWhichOneHoldsAndWhy() {
        assertEquals(R.string.keep_cpu_awake_action_on, CpuWakeLock.notificationAction("off"));
        assertEquals(R.string.keep_cpu_awake_action_on, CpuWakeLock.notificationAction("while_audio"));
        assertEquals(R.string.keep_cpu_awake_action_off, CpuWakeLock.notificationAction("always"));
        for (String mode : CpuWakeLock.MODES) {
            assertEquals(R.string.service_notify_text, CpuWakeLock.notificationText(false, mode));
        }
        assertEquals(R.string.service_notify_text_keep_awake, CpuWakeLock.notificationText(true, "always"));
        assertEquals(R.string.service_notify_text_keep_awake_audio, CpuWakeLock.notificationText(true, "while_audio"));
        assertEquals(R.string.keep_cpu_awake_mode_off, CpuWakeLock.modeLabel("off"));
        assertEquals(R.string.keep_cpu_awake_mode_while_audio, CpuWakeLock.modeLabel("while_audio"));
        assertEquals(R.string.keep_cpu_awake_mode_always, CpuWakeLock.modeLabel("always"));
        assertEquals(R.string.keep_cpu_awake_toast_off, CpuWakeLock.modeToast("off"));
        assertEquals(R.string.keep_cpu_awake_toast_audio, CpuWakeLock.modeToast("while_audio"));
        assertEquals(R.string.keep_cpu_awake_toast_on, CpuWakeLock.modeToast("always"));
    }

    @Test
    public void theLabelsNameTheCpuNotTheScreen() throws IOException {
        String strings = read(new File(MAIN, "res/values/strings.xml"));
        assertEquals("Keep CPU awake", string(strings, "keep_cpu_awake"));
        assertEquals("Keep awake", string(strings, "keep_cpu_awake_action_on"));
        assertEquals("Let sleep", string(strings, "keep_cpu_awake_action_off"));
        assertEquals("Keep CPU awake", string(strings, "title_keep_cpu_awake_preference"));
        assertEquals("Off", string(strings, "keep_cpu_awake_mode_off"));
        assertEquals("While sound plays", string(strings, "keep_cpu_awake_mode_while_audio"));
        assertEquals("Always", string(strings, "keep_cpu_awake_mode_always"));
        assertEquals("ThothTerm Garden is running, CPU kept awake",
                string(strings, "service_notify_text_keep_awake"));
        assertEquals("ThothTerm Garden is running, CPU awake for Linux audio",
                string(strings, "service_notify_text_keep_awake_audio"));
        // Distinct from the screen-awake labels the device test looks for.
        assertFalse(string(strings, "keep_cpu_awake").equals(string(strings, "enable_keep_screen_on")));

        // The Settings entry is a list of the three modes in MODES order, defaulting to while_audio.
        String prefs = read(new File(MAIN, "res/xml/preferences.xml"));
        Matcher m = Pattern.compile("<ListPreference\\s+android:defaultValue=\"(\\w+)\"\\s+android:dialogTitle=\"@string/keep_cpu_awake\""
                + "\\s+android:entries=\"@array/entries_keep_cpu_awake_mode_preference\""
                + "\\s+android:entryValues=\"@array/entryvalues_keep_cpu_awake_mode_preference\""
                + "\\s+android:key=\"" + CpuWakeLock.PREF_KEY + "\"").matcher(prefs);
        assertTrue("no list for " + CpuWakeLock.PREF_KEY, m.find());
        assertEquals(CpuWakeLock.PREF_DEFAULT, m.group(1));
        assertFalse("a boolean switch would not carry the modes", prefs.contains("android:key=\"keep_cpu_awake\""));
        String values = read(new File(MAIN, "res/values/arraysNoLocalize.xml"));
        assertTrue(values.contains("<string-array name=\"entryvalues_keep_cpu_awake_mode_preference\">\n"
                + "        <item>off</item>\n        <item>while_audio</item>\n        <item>always</item>\n"));
        String entries = read(new File(MAIN, "res/values/arrays.xml"));
        assertTrue(entries.contains("<string-array name=\"entries_keep_cpu_awake_mode_preference\">\n"
                + "        <item>@string/keep_cpu_awake_mode_off</item>\n"
                + "        <item>@string/keep_cpu_awake_mode_while_audio</item>\n"
                + "        <item>@string/keep_cpu_awake_mode_always</item>\n"));
    }

    @Test
    public void theServiceOwnsTheLockAndTheTerminalOnlySetsTheMode() throws IOException {
        String service = read(new File(MAIN, "java/jackpal/androidterm/TermService.java"));
        assertTrue(service.contains("wakeLock = new CpuWakeLock(this)"));
        assertTrue(service.contains("wakeLock.sync(this, "));
        assertTrue(service.contains("ACTION_TOGGLE_KEEP_AWAKE.equals(intent.getAction())"));
        assertTrue(service.contains("CpuWakeLock.toggleAlways(this)"));
        // The bridge's state reaches the lock: the listener re-evaluates it, and again after the grace.
        assertTrue(service.contains("audioBridge.setListener("));
        assertTrue(service.contains("CpuWakeLock.nextCheckDelayMillis("));
        // Released on every way out: destroy, timeout and Exit.
        int onDestroy = service.indexOf("public void onDestroy()");
        int onTimeout = service.indexOf("public void onTimeout(");
        int shutdownAll = service.indexOf("public void shutdownAll()");
        for (int start : new int[]{onDestroy, onTimeout, shutdownAll}) {
            assertTrue(start >= 0);
            int end = service.indexOf("\n    }\n", start);
            assertTrue(service.substring(start, end).contains("releaseKeepAwake()"));
        }
        // The restart policy is untouched.
        assertTrue(service.contains("return START_NOT_STICKY;"));

        String term = read(new File(MAIN, "java/jackpal/androidterm/Term.java"));
        assertTrue(term.contains("CpuWakeLock.setMode(this, "));
        assertTrue(term.contains("CpuWakeLock.modeIndex(CpuWakeLock.mode(this))"));
        assertTrue(term.contains("setSingleChoiceItems(R.array.entries_keep_cpu_awake_mode_preference"));
        assertTrue(term.contains("R.id.menu_toggle_keep_cpu_awake"));
        assertFalse(term.contains("new CpuWakeLock("));
        assertFalse(term.contains("CpuWakeLock.toggle"));

        String menu = read(new File(MAIN, "res/menu/main.xml"));
        Matcher m = Pattern.compile("<item android:id=\"@\\+id/menu_toggle_keep_cpu_awake\"\\s+android:title=")
                .matcher(menu);
        assertTrue("the menu item opens a dialog, it is not a check box", m.find());

        // WAKE_LOCK, which Keep Wi-Fi on already needs, is all the lock takes;
        // no battery-optimisation exemption is requested anywhere.
        String manifest = read(new File(MAIN, "AndroidManifest.xml"));
        assertTrue(manifest.contains("android.permission.WAKE_LOCK"));
        assertFalse(manifest.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"));
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
