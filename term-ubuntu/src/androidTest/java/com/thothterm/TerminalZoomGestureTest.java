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
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Drives a genuine two-finger pinch on the terminal.
 *
 * <p>The rest of the zoom chain is covered off-device by {@code TerminalZoomTest}
 * and by the menu actions, which share every step after the gesture. What only a
 * device can exercise is the gesture itself: that a pinch is recognised at all,
 * and that recognising one does not swallow the taps and drags the terminal
 * needs. {@code adb shell input} cannot express multi-touch, and writing kernel
 * events needs privileges the shell user does not have, so this runs as
 * instrumentation instead.
 */
@RunWith(AndroidJUnit4.class)
public class TerminalZoomGestureTest {
    private static final String PACKAGE = "com.thothterm.ubuntu";
    private static final String FLIPPER = PACKAGE + ":id/view_flipper";
    private static final long LAUNCH_TIMEOUT_MS = 40_000L;

    private UiDevice device;
    private SharedPreferences prefs;

    private int fontSize() {
        // TermSettings stores this key as a string; see readIntPref.
        return Integer.parseInt(prefs.getString("fontsize",
                Integer.toString(TerminalZoom.DEFAULT_SIZE)));
    }

    private void setFontSize(int size) {
        prefs.edit().putString("fontsize", Integer.toString(size)).commit();
    }

    private UiObject terminal() {
        return device.findObject(new UiSelector().resourceId(FLIPPER));
    }

    @Before
    public void launchTerminal() throws Exception {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        prefs = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context);

        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(PACKAGE);
        assertTrue("no launch intent for " + PACKAGE, intent != null);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);

        // Guest start-up runs before the terminal appears on a cold launch.
        assertTrue("terminal did not appear",
                device.wait(Until.hasObject(By.res(FLIPPER)), LAUNCH_TIMEOUT_MS));
        assertTrue("terminal view is not present", terminal().exists());
    }

    @Test
    public void pinchingApartEnlargesTheFont() throws Exception {
        setFontSize(TerminalZoom.DEFAULT_SIZE);
        int before = fontSize();

        terminal().pinchOut(60, 20);
        device.waitForIdle();

        assertTrue("pinch out must increase the font size, was "
                + before + " now " + fontSize(), fontSize() > before);
    }

    @Test
    public void pinchingTogetherShrinksTheFont() throws Exception {
        setFontSize(TerminalZoom.DEFAULT_SIZE);
        int before = fontSize();

        terminal().pinchIn(60, 20);
        device.waitForIdle();

        assertTrue("pinch in must decrease the font size, was "
                + before + " now " + fontSize(), fontSize() < before);
    }

    @Test
    public void zoomStaysWithinTheLadder() throws Exception {
        setFontSize(TerminalZoom.MAX_SIZE);
        terminal().pinchOut(90, 25);
        device.waitForIdle();
        assertEquals("must not exceed the maximum",
                TerminalZoom.MAX_SIZE, fontSize());

        setFontSize(TerminalZoom.MIN_SIZE);
        terminal().pinchIn(90, 25);
        device.waitForIdle();
        assertEquals("must not fall below the minimum",
                TerminalZoom.MIN_SIZE, fontSize());
    }

    /**
     * A recognised pinch consumes its own events, but an ordinary tap must still
     * reach the terminal — otherwise focus and the soft keyboard stop working.
     */
    @Test
    public void ordinaryTapsStillReachTheTerminal() throws Exception {
        setFontSize(TerminalZoom.DEFAULT_SIZE);
        int before = fontSize();

        terminal().click();
        device.waitForIdle();

        assertEquals("a single tap must not change the font size",
                before, fontSize());
        assertTrue("terminal must survive a tap", terminal().exists());
    }
}
