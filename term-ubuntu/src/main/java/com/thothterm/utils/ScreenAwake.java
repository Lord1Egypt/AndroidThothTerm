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

import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.thothterm.R;

/**
 * "Keep screen awake": {@code FLAG_KEEP_SCREEN_ON} on the terminal's window.
 * <p>
 * The window flag is the only state, so the menu can never disagree with it.
 * Android honours the flag only while the window is visible: a terminal in the
 * background never holds the display on, and none of this needs a permission,
 * a CPU wake lock or a battery-optimization exemption. It must not become a
 * {@code PowerManager} wake lock again -- a {@code PARTIAL_WAKE_LOCK} keeps the
 * CPU awake, not the display, which is the bug this class replaced.
 * <p>
 * The choice lasts as long as the terminal activity: it travels through the
 * saved instance state when the activity is recreated, and a new launch,
 * including the one after Exit, starts with it off.
 */
public final class ScreenAwake {
    static final String STATE_KEY = "com.thothterm.keep_screen_on";

    private ScreenAwake() {
    }

    public static boolean isOn(@NonNull Window window) {
        return (window.getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    public static void set(@NonNull Window window, boolean on) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public static void toggle(@NonNull Window window) {
        set(window, !isOn(window));
    }

    public static void save(@NonNull Window window, @NonNull Bundle outState) {
        outState.putBoolean(STATE_KEY, isOn(window));
    }

    public static void restore(@NonNull Window window, @Nullable Bundle savedState) {
        set(window, savedState != null && savedState.getBoolean(STATE_KEY));
    }

    /** The menu names the action the user can take, not the current state. */
    @StringRes
    public static int menuTitle(boolean on) {
        return on ? R.string.disable_keep_screen_on : R.string.enable_keep_screen_on;
    }
}
