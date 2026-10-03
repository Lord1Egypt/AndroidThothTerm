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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.thothterm.R;

/**
 * Keeping the display on while the terminal is in front: the window's
 * {@code FLAG_KEEP_SCREEN_ON}, for one of two reasons.
 * <ul>
 * <li><b>manual</b> -- the "Keep screen awake" menu action. It lasts as long as
 * the terminal activity: saved across recreation, off after a new launch or
 * Exit.</li>
 * <li><b>charging</b> -- the "Keep screen awake while charging" setting (on by
 * default) while the device is connected to power: AC, USB, wireless or dock.</li>
 * </ul>
 * The flag is {@code manual || (setting && pluggedIn)}, computed from those
 * three values and applied to the window every time one changes; the window is
 * never the record. Power is watched only while the activity is started, by a
 * receiver registered at run time and never exported; on start the current
 * state is read from the sticky battery broadcast. Android honours the flag only
 * while the window is visible, and the charging reason is dropped when the
 * activity stops, so a terminal behind another app never keeps the display on.
 * <p>
 * No permission, no {@code PowerManager} wake lock and no battery-optimization
 * exemption are involved. A {@code PARTIAL_WAKE_LOCK} keeps the CPU awake, not
 * the display -- the bug this class replaced up to terminal-v1.3.0.
 */
public final class ScreenAwake {
    static final String STATE_KEY = "com.thothterm.keep_screen_on";
    /** SharedPreferences key of the charging setting. */
    public static final String PREF_WHILE_CHARGING = "keep_screen_on_while_charging";
    public static final boolean PREF_WHILE_CHARGING_DEFAULT = true;

    /** What the menu item offers. */
    public enum Menu {
        /** Nothing keeps the screen on: offer to. */
        KEEP_AWAKE,
        /** The manual choice keeps it on: offer to undo it. */
        ALLOW_SLEEP,
        /** Only charging keeps it on: say so, and lead to the setting. */
        AWAKE_WHILE_CHARGING,
    }

    private final Activity activity;
    private boolean manual;
    private boolean setting = PREF_WHILE_CHARGING_DEFAULT;
    private boolean pluggedIn;
    private BroadcastReceiver receiver;

    /** Restores the manual choice from {@code savedState}; the charging reason starts unknown. */
    public ScreenAwake(@NonNull Activity activity, @Nullable Bundle savedState) {
        this.activity = activity;
        this.manual = savedState != null && savedState.getBoolean(STATE_KEY);
        apply();
    }

    /** The effective state: the only rule there is. */
    public static boolean keepOn(boolean manual, boolean setting, boolean pluggedIn) {
        return manual || (setting && pluggedIn);
    }

    public static Menu menu(boolean manual, boolean setting, boolean pluggedIn) {
        if (manual) return Menu.ALLOW_SLEEP;
        if (setting && pluggedIn) return Menu.AWAKE_WHILE_CHARGING;
        return Menu.KEEP_AWAKE;
    }

    /** The menu names what the user can do, and never claims the screen may sleep while it cannot. */
    @StringRes
    public static int menuTitle(Menu menu) {
        switch (menu) {
            case ALLOW_SLEEP:
                return R.string.disable_keep_screen_on;
            case AWAKE_WHILE_CHARGING:
                return R.string.keep_screen_on_while_charging_active;
            default:
                return R.string.enable_keep_screen_on;
        }
    }

    /** Whether {@code plugged}, an {@code EXTRA_PLUGGED} value, means connected to power. */
    public static boolean isPluggedIn(int plugged) {
        return plugged != 0 && plugged != -1;
    }

    /** From onStart: read the setting and the power state now, then follow power changes. */
    public void start() {
        readSetting();
        if (receiver == null) {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onPower(intent);
                }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent sticky;
            if (Build.VERSION.SDK_INT >= 33) {
                sticky = activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                sticky = activity.registerReceiver(receiver, filter);
            }
            if (sticky != null) onPower(sticky);
        }
        apply();
    }

    /** From onStop: stop watching power; charging no longer counts until the next start. */
    public void stop() {
        if (receiver != null) {
            activity.unregisterReceiver(receiver);
            receiver = null;
        }
        pluggedIn = false;
        apply();
    }

    /** The setting may have changed. */
    public void onPreferencesChanged() {
        readSetting();
        apply();
    }

    public void save(@NonNull Bundle outState) {
        outState.putBoolean(STATE_KEY, manual);
    }

    /** The manual choice flips; the charging reason is untouched. */
    public void toggleManual() {
        manual = !manual;
        apply();
    }

    public boolean isManual() {
        return manual;
    }

    public boolean isPluggedIn() {
        return pluggedIn;
    }

    public Menu menu() {
        return menu(manual, setting, pluggedIn);
    }

    /** Whether the window flag is set, for tests. */
    public static boolean isOn(@NonNull Window window) {
        return (window.getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    private void onPower(Intent intent) {
        pluggedIn = isPluggedIn(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
        apply();
    }

    private void readSetting() {
        setting = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(PREF_WHILE_CHARGING, PREF_WHILE_CHARGING_DEFAULT);
    }

    private void apply() {
        Window window = activity.getWindow();
        if (keepOn(manual, setting, pluggedIn)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
