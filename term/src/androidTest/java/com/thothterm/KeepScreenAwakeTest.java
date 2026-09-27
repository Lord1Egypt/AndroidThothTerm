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
import android.content.IntentFilter;
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

/**
 * "Keep screen awake" against the real window, the real menu and the real
 * power manager.
 * <p>
 * It is written against the installed app from the outside -- menu labels,
 * the activity's {@code Window}, {@code dumpsys} -- and names no app class, so
 * it runs unchanged against the minified release build. The same file runs in
 * every ThothTerm edition; only the package differs.
 */
@RunWith(AndroidJUnit4.class)
public class KeepScreenAwakeTest {
    private static final String KEEP_AWAKE = "Keep screen awake";
    private static final String ALLOW_SLEEP = "Allow screen to sleep";
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
    private Instrumentation.ActivityMonitor batterySettings;

    @Before
    public void launchTerminal() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        context = instrumentation.getTargetContext();
        pkg = context.getPackageName();
        uid = context.getApplicationInfo().uid;

        // Blocks and counts any attempt to send the user to battery settings.
        IntentFilter battery = new IntentFilter(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        battery.addAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        batterySettings = instrumentation.addMonitor(battery, null, true);

        bringToFront();
        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, false);
        choose(KEEP_AWAKE, ALLOW_SLEEP, false);
    }

    @After
    public void restore() throws Exception {
        if (terminal() == null) bringToFront();
        choose(KEEP_WIFI, ALLOW_WIFI_SLEEP, false);
        choose(KEEP_AWAKE, ALLOW_SLEEP, false);
        instrumentation.removeMonitor(batterySettings);
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

    // --- helpers -----------------------------------------------------------

    private void bringToFront() throws Exception {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        assertNotNull("no launch intent for " + pkg, intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
        assertEquals("battery settings were opened", 0, batterySettings.getHits());
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
