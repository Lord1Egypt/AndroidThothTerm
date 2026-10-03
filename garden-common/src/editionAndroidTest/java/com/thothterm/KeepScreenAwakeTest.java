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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Keep screen awake" -- by hand and while charging -- against the real
 * window, the real menu and the real power manager.
 * <p>
 * Power is simulated through {@code dumpsys battery} (unplugged unless a test
 * connects AC), and the real state is restored afterwards.
 * <p>
 * It reads menu labels, the activity's {@code Window} and {@code dumpsys}, and
 * names no app class, so the same file runs in every ThothTerm edition. It
 * runs against the debug build: the test runner needs AndroidX classes that
 * R8 removes from the release build. The minified release is checked on the
 * device from outside, through the same window flag and display hold in
 * {@code dumpsys}.
 */
@RunWith(AndroidJUnit4.class)
public class KeepScreenAwakeTest {
    private static final String KEEP_AWAKE = "Keep screen awake";
    private static final String ALLOW_SLEEP = "Allow screen to sleep";
    private static final String WHILE_CHARGING = "Screen awake while charging";
    private static final String CHARGING_SETTING = "keep_screen_on_while_charging";
    private static final String KEEP_WIFI = "Keep Wi-Fi on";
    private static final String ALLOW_WIFI_SLEEP = "Allow Wi-Fi to sleep";
    private static final long LAUNCH_TIMEOUT_MS = 40_000L;
    private static final long UI_TIMEOUT_MS = 5_000L;
    private static final long SYSTEM_TIMEOUT_MS = 10_000L;

    private Instrumentation instrumentation;
    private UiDevice device;
    private Context context;
    private String pkg;
    private int uid;
    private final AtomicInteger batterySettingsStarts = new AtomicInteger();
    private Instrumentation.ActivityMonitor batterySettings;

    @Before
    public void launchTerminal() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        context = instrumentation.getTargetContext();
        pkg = context.getPackageName();
        uid = context.getApplicationInfo().uid;

        // Blocks and counts any attempt to send the user to battery settings.
        // Matched by action here, not by an IntentFilter: a filter with
        // actions also matches every intent without one, which would swallow
        // the app's own explicit activity starts.
        batterySettings = new Instrumentation.ActivityMonitor() {
            @Override
            public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                String action = intent.getAction();
                if (Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS.equals(action)
                        || Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.equals(action)) {
                    batterySettingsStarts.incrementAndGet();
                    return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
                }
                return null;
            }
        };
        instrumentation.addMonitor(batterySettings);

        shell("dumpsys battery unplug");
        preferences().edit().remove(CHARGING_SETTING).commit();
        bringToFront();
        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, false);
        choose(KEEP_AWAKE, ALLOW_SLEEP, false);
    }

    @After
    public void restore() throws Exception {
        shell("dumpsys battery unplug");
        preferences().edit().remove(CHARGING_SETTING).commit();
        if (terminal() == null) bringToFront();
        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, false);
        choose(KEEP_AWAKE, ALLOW_SLEEP, false);
        instrumentation.removeMonitor(batterySettings);
        shell("dumpsys battery reset");
    }

    @Test
    public void offByDefaultAndTheMenuOffersToKeepTheScreenAwake() throws Exception {
        assertFalse(keepScreenOn(terminal()));
        openMenu();
        assertNotNull(device.findObject(By.text(KEEP_AWAKE)));
        assertNull(device.findObject(By.text(ALLOW_SLEEP)));
        device.pressBack();
    }

    @Test
    public void enablingSetsTheWindowFlagAndTheDisplayHold() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);

        assertTrue("FLAG_KEEP_SCREEN_ON missing", keepScreenOn(terminal()));
        assertTrue("WindowManager does not hold the screen for " + pkg,
                waitFor(this::displayHeldForUs, true));
        openMenu();
        assertNotNull(device.findObject(By.text(ALLOW_SLEEP)));
        assertNull(device.findObject(By.text(KEEP_AWAKE)));
        device.pressBack();

        assertNoCpuWakeLock();
        assertNoBatteryDetour();
    }

    @Test
    public void disablingClearsTheWindowFlagAndTheDisplayHold() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        assertTrue(waitFor(this::displayHeldForUs, true));

        choose(KEEP_AWAKE, ALLOW_SLEEP, false);

        assertFalse("FLAG_KEEP_SCREEN_ON still set", keepScreenOn(terminal()));
        assertTrue("WindowManager still holds the screen for " + pkg,
                waitFor(this::displayHeldForUs, false));
        assertNoCpuWakeLock();
    }

    @Test
    public void recreatingTheActivityKeepsTheChoice() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        Activity before = terminal();

        instrumentation.runOnMainSync(before::recreate);
        Activity after = waitForNewTerminal(before);

        assertNotSame(before, after);
        assertTrue("choice lost on recreation", keepScreenOn(after));
        assertTrue(waitFor(this::displayHeldForUs, true));
    }

    @Test
    public void inTheBackgroundNothingHoldsTheScreenOrTheCpu() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        assertTrue(waitFor(this::displayHeldForUs, true));

        device.pressHome();

        assertTrue("screen still held with the terminal in the background",
                waitFor(this::displayHeldForUs, false));
        assertNoCpuWakeLock();

        // Coming back to the same activity reapplies nothing by hand: the
        // window still carries the flag and Android honours it again.
        bringToFront();
        assertTrue(keepScreenOn(terminal()));
        assertTrue(waitFor(this::displayHeldForUs, true));
    }

    @Test
    public void noBatteryOptimizationPermissionIsRequested() throws Exception {
        PackageInfo info = context.getPackageManager()
                .getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
        String[] requested = info.requestedPermissions != null
                ? info.requestedPermissions : new String[0];
        assertFalse(Arrays.asList(requested).contains(
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"));
    }

    @Test
    public void keepWifiOnIsIndependent() throws Exception {
        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, true);
        assertFalse("Wi-Fi lock turned the screen flag on", keepScreenOn(terminal()));

        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        openMenu();
        assertNotNull("screen toggle released the Wi-Fi lock",
                device.findObject(By.text(ALLOW_WIFI_SLEEP)));
        device.pressBack();

        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, false);
        assertTrue("Wi-Fi toggle cleared the screen flag", keepScreenOn(terminal()));
    }

    // --- while charging ------------------------------------------------------

    @Test
    public void chargingKeepsTheScreenOnByDefault() throws Exception {
        assertFalse(keepScreenOn(terminal()));
        connectPower();

        assertTrue("FLAG_KEEP_SCREEN_ON missing while charging", waitForFlag(true));
        assertTrue(waitFor(this::displayHeldForUs, true));
        assertMenuShows(WHILE_CHARGING);
        assertNoCpuWakeLock();
        assertNoBatteryDetour();
    }

    @Test
    public void unpluggingDropsTheChargingReasonAtOnce() throws Exception {
        connectPower();
        assertTrue(waitForFlag(true));

        shell("dumpsys battery unplug");

        assertTrue("flag still set after unplugging", waitForFlag(false));
        assertTrue(waitFor(this::displayHeldForUs, false));
        assertMenuShows(KEEP_AWAKE);
    }

    @Test
    public void theSettingOffIgnoresCharging() throws Exception {
        preferences().edit().putBoolean(CHARGING_SETTING, false).commit();
        connectPower();
        SystemClock.sleep(1500);

        assertFalse("charging kept the screen on with the setting off", keepScreenOn(terminal()));
        assertMenuShows(KEEP_AWAKE);

        preferences().edit().putBoolean(CHARGING_SETTING, true).commit();
        assertTrue("turning the setting on did not apply at once", waitForFlag(true));
    }

    @Test
    public void manualWorksUnpluggedAndCoexistsWithCharging() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        assertTrue(keepScreenOn(terminal()));

        connectPower();
        SystemClock.sleep(1000);
        assertTrue(keepScreenOn(terminal()));
        assertMenuShows(ALLOW_SLEEP);

        shell("dumpsys battery unplug");
        SystemClock.sleep(1000);
        assertTrue("unplugging cleared the manual choice", keepScreenOn(terminal()));
    }

    @Test
    public void turningManualOffWhileChargingKeepsTheScreenOn() throws Exception {
        choose(KEEP_AWAKE, ALLOW_SLEEP, true);
        connectPower();
        SystemClock.sleep(1000);

        choose(KEEP_AWAKE, ALLOW_SLEEP, false);

        assertTrue("manual off defeated the charging reason", keepScreenOn(terminal()));
        assertMenuShows(WHILE_CHARGING);
    }

    @Test
    public void theChargingItemLeadsToTheSetting() throws Exception {
        connectPower();
        assertTrue(waitForFlag(true));
        openMenu();
        device.findObject(By.text(WHILE_CHARGING)).click();
        assertTrue("the setting did not open where it can be changed",
                device.wait(Until.hasObject(By.text("Keep screen awake while charging")), UI_TIMEOUT_MS));
        device.pressBack();
        device.waitForIdle();
        bringToFront();
        assertTrue(keepScreenOn(terminal()));
    }

    @Test
    public void chargingBehindAnotherAppHoldsNothingAndResumes() throws Exception {
        connectPower();
        assertTrue(waitFor(this::displayHeldForUs, true));

        device.pressHome();

        assertTrue("screen held by a terminal in the background",
                waitFor(this::displayHeldForUs, false));
        assertNoCpuWakeLock();

        bringToFront();
        assertTrue("charging did not apply again on return", waitForFlag(true));
        assertTrue(waitFor(this::displayHeldForUs, true));
    }

    @Test
    public void recreationWhileChargingReadsPowerAgain() throws Exception {
        connectPower();
        assertTrue(waitForFlag(true));
        Activity before = terminal();

        instrumentation.runOnMainSync(before::recreate);
        Activity after = waitForNewTerminal(before);

        assertTrue(keepScreenOn(after));
        shell("dumpsys battery unplug");
        assertTrue("recreated activity does not follow power", waitForFlag(false));
    }

    // --- helpers -----------------------------------------------------------

    private void connectPower() throws IOException {
        shell("dumpsys battery set ac 1");
    }

    /** The app's default preferences, the same instance the terminal listens to. */
    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences(pkg + "_preferences", Context.MODE_PRIVATE);
    }

    private boolean waitForFlag(boolean expected) throws IOException {
        return waitFor(() -> keepScreenOn(terminal()), expected);
    }

    /** The screen item says {@code label}, and only that. */
    private void assertMenuShows(String label) {
        openMenu();
        for (String other : new String[]{KEEP_AWAKE, ALLOW_SLEEP, WHILE_CHARGING}) {
            if (other.equals(label)) {
                assertNotNull("menu lacks " + other, device.findObject(By.text(other)));
            } else {
                assertNull("menu shows " + other, device.findObject(By.text(other)));
            }
        }
        device.pressBack();
    }

    private void bringToFront() throws Exception {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        assertNotNull("no launch intent for " + pkg, intent);
        // Through the shell: Android blocks an activity start from an app in
        // the background, which this app is whenever another one is in front.
        shell("am start -n " + intent.getComponent().flattenToShortString());
        long deadline = SystemClock.uptimeMillis() + LAUNCH_TIMEOUT_MS;
        while (terminal() == null || !device.hasObject(By.res(pkg, "view_flipper"))) {
            assertTrue("terminal did not come to the front",
                    SystemClock.uptimeMillis() < deadline);
            SystemClock.sleep(200);
        }
        device.waitForIdle();
    }

    /** The resumed activity of this app, or null. */
    private Activity terminal() {
        Activity[] found = new Activity[1];
        instrumentation.runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            if (!resumed.isEmpty()) found[0] = resumed.iterator().next();
        });
        return found[0];
    }

    private Activity waitForNewTerminal(Activity old) {
        long deadline = SystemClock.uptimeMillis() + LAUNCH_TIMEOUT_MS;
        Activity now;
        while ((now = terminal()) == null || now == old) {
            assertTrue("activity was not recreated", SystemClock.uptimeMillis() < deadline);
            SystemClock.sleep(100);
        }
        device.waitForIdle();
        return now;
    }

    private boolean keepScreenOn(Activity activity) {
        assertNotNull("no resumed terminal", activity);
        boolean[] on = new boolean[1];
        instrumentation.runOnMainSync(() -> on[0] = (activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        return on[0];
    }

    /** Opens the overflow menu. The first MENU may only reveal a hidden toolbar. */
    private void openMenu() {
        for (int attempt = 0; attempt < 3; attempt++) {
            device.pressMenu();
            if (device.wait(Until.hasObject(By.text(KEEP_WIFI).pkg(pkg)), UI_TIMEOUT_MS)
                    || device.hasObject(By.text(ALLOW_WIFI_SLEEP).pkg(pkg))) {
                return;
            }
        }
        throw new AssertionError("overflow menu did not open");
    }

    /** Brings a two-label toggle to the wanted state through the menu. */
    private void choose(String enable, String disable, boolean on) {
        openMenu();
        UiObject2 item = device.findObject(By.text(on ? enable : disable));
        if (item != null) {
            item.click();
            device.wait(Until.gone(By.text(on ? enable : disable)), UI_TIMEOUT_MS);
        } else {
            device.pressBack();
        }
        device.waitForIdle();
    }

    private void assertNoBatteryDetour() {
        assertEquals("battery settings were opened", 0, batterySettingsStarts.get());
        assertFalse("a battery dialog appeared",
                device.hasObject(By.textContains("battery")));
        assertEquals(pkg, device.getCurrentPackageName());
    }

    /** No partial wake lock -- the CPU kind the old implementation took -- is ours. */
    private void assertNoCpuWakeLock() throws IOException {
        for (String line : currentWakeLocks().split("\n")) {
            if (line.contains("PARTIAL_WAKE_LOCK")) {
                assertFalse("CPU wake lock held: " + line.trim(),
                        line.contains("(uid=" + uid + " "));
            }
        }
    }

    /** WindowManager's screen wake lock, attributed to this app's window. */
    private boolean displayHeldForUs() throws IOException {
        for (String line : currentWakeLocks().split("\n")) {
            if (line.contains("'WindowManager") && line.contains("WorkSource{" + uid + " ")) {
                return true;
            }
        }
        return false;
    }

    /** The "Wake Locks:" section of dumpsys power, without its history. */
    private String currentWakeLocks() throws IOException {
        String power = shell("dumpsys power");
        int start = power.indexOf("\nWake Locks:");
        assertTrue("dumpsys power has no Wake Locks section", start >= 0);
        int end = power.indexOf("\nSuspend Blockers:", start);
        return power.substring(start, end < 0 ? power.length() : end);
    }

    private interface Check {
        boolean get() throws IOException;
    }

    private static boolean waitFor(Check check, boolean expected) throws IOException {
        long deadline = SystemClock.uptimeMillis() + SYSTEM_TIMEOUT_MS;
        while (check.get() != expected) {
            if (SystemClock.uptimeMillis() > deadline) return false;
            SystemClock.sleep(250);
        }
        return true;
    }

    private String shell(String command) throws IOException {
        ParcelFileDescriptor pfd = instrumentation.getUiAutomation().executeShellCommand(command);
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            pfd.close();
        }
    }
}
