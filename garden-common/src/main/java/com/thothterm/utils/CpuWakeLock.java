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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.thothterm.R;

/**
 * "Keep CPU awake": a {@code PARTIAL_WAKE_LOCK} so the guest Linux processes
 * keep running while the screen is off, held according to a mode.
 * <p>
 * Without it the device suspends soon after the display sleeps and the
 * processes under PRoot only get CPU for a moment every 30-60 s, even though
 * the foreground service keeps the app alive: sound from "Linux audio" stops
 * with the screen, and a long job stalls. This is the same thing
 * {@code termux-wake-lock} does. It is deliberately not "Keep screen awake":
 * that is {@link ScreenAwake}, the window's {@code FLAG_KEEP_SCREEN_ON}, which
 * never touches the CPU; this class never touches the display, and nothing
 * here asks for a battery-optimisation exemption.
 * <ul>
 * <li>The <b>mode</b> {@link #PREF_KEY} is the record of what the user wants:
 * {@link #MODE_OFF} never holds the lock, {@link #MODE_ALWAYS} holds it as
 * long as the service runs (long jobs with the screen off), and the default
 * {@link #MODE_WHILE_AUDIO} holds it only while "Linux audio" is playing and
 * for {@link #AUDIO_GRACE_MS} after it stops, so a player's pause between
 * tracks does not let the device fall asleep. The menu dialog, the Settings
 * list and the notification button all set that one value; the notification
 * button goes between {@link #MODE_ALWAYS} and the default.</li>
 * <li>The <b>lock</b> is owned by the terminal service -- the process the
 * shells live in -- and follows {@link #shouldHold}: re-evaluated when the
 * mode changes and whenever the audio bridge starts or stops playing,
 * released when the service stops, never left behind. {@link #apply(boolean)}
 * is idempotent, so the order and number of calls do not matter.</li>
 * </ul>
 */
public final class CpuWakeLock {
    /** SharedPreferences key: one of the {@link #MODES}. */
    public static final String PREF_KEY = "keep_cpu_awake_mode";
    public static final String MODE_OFF = "off";
    public static final String MODE_WHILE_AUDIO = "while_audio";
    public static final String MODE_ALWAYS = "always";
    /** In the order the Settings list and the menu dialog show them. */
    public static final String[] MODES = {MODE_OFF, MODE_WHILE_AUDIO, MODE_ALWAYS};
    public static final String PREF_DEFAULT = MODE_WHILE_AUDIO;
    /** The tag dumpsys power shows for this lock. */
    static final String TAG = "ThothTerm:KeepAwake";
    /** How long after the last sound {@link #MODE_WHILE_AUDIO} still holds the lock. */
    public static final long AUDIO_GRACE_MS = 10000;

    /** The system lock behind an interface, so the transitions run on the JVM. */
    interface Holder {
        void acquire();

        void release();

        boolean isHeld();
    }

    @Nullable
    private final Holder holder;

    /** The real lock from {@code PowerManager}; a device without one holds nothing. */
    public CpuWakeLock(@NonNull Context context) {
        this(systemHolder(context));
    }

    CpuWakeLock(@Nullable Holder holder) {
        this.holder = holder;
    }

    @Nullable
    private static Holder systemHolder(Context context) {
        PowerManager pm = (PowerManager) context.getApplicationContext()
                .getSystemService(Context.POWER_SERVICE);
        if (pm == null) return null;
        final PowerManager.WakeLock lock;
        try {
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        } catch (Exception e) {
            return null;
        }
        if (lock == null) return null;
        // One acquire, one release, whatever the count of calls.
        lock.setReferenceCounted(false);
        return new Holder() {
            @Override
            public void acquire() {
                lock.acquire();
            }

            @Override
            public void release() {
                lock.release();
            }

            @Override
            public boolean isHeld() {
                return lock.isHeld();
            }
        };
    }

    /** Whether a lock could be created at all. */
    public boolean exists() {
        return holder != null;
    }

    public boolean isHeld() {
        return holder != null && holder.isHeld();
    }

    /** Hold the lock when {@code wanted}, drop it otherwise; idempotent. Returns whether it is held now. */
    public boolean apply(boolean wanted) {
        if (holder == null) return false;
        if (wanted) {
            if (!holder.isHeld()) holder.acquire();
        } else {
            if (holder.isHeld()) holder.release();
        }
        return holder.isHeld();
    }

    /**
     * Make the lock match the mode and the audio state (see {@link #shouldHold}).
     * Returns whether it is held now.
     */
    public boolean sync(@NonNull Context context, boolean audioActive, long audioIdleSinceMs,
                        long nowMs) {
        return apply(shouldHold(mode(context), audioActive, audioIdleSinceMs, nowMs));
    }

    /** Drop the lock whatever the mode says: the service is going away. */
    public void release() {
        apply(false);
    }

    /* ---- the rule: pure functions of the state ---- */

    /**
     * Whether the lock is to be held: always in {@link #MODE_ALWAYS}, never in
     * {@link #MODE_OFF}, and in {@link #MODE_WHILE_AUDIO} while sound plays
     * ({@code audioActive}) or within {@link #AUDIO_GRACE_MS} of it stopping
     * ({@code audioIdleSinceMs}, the clock reading when it stopped, negative
     * when it never played).
     */
    public static boolean shouldHold(String mode, boolean audioActive, long audioIdleSinceMs,
                                     long nowMs) {
        if (MODE_ALWAYS.equals(mode)) return true;
        if (!MODE_WHILE_AUDIO.equals(mode)) return false;
        if (audioActive) return true;
        return audioIdleSinceMs >= 0 && nowMs - audioIdleSinceMs < AUDIO_GRACE_MS;
    }

    /**
     * When {@link #MODE_WHILE_AUDIO} has to be looked at again after the
     * sound stopped at {@code audioIdleSinceMs}: the rest of the grace, at
     * least 0; or -1 when nothing will change by itself.
     */
    public static long nextCheckDelayMillis(String mode, boolean audioActive, long audioIdleSinceMs,
                                            long nowMs) {
        if (!MODE_WHILE_AUDIO.equals(mode) || audioActive || audioIdleSinceMs < 0) return -1;
        long elapsed = nowMs - audioIdleSinceMs;
        return elapsed >= AUDIO_GRACE_MS ? -1 : AUDIO_GRACE_MS - elapsed;
    }

    /** A known mode, or the default for anything else (an unknown or null value). */
    public static String validMode(@Nullable String mode) {
        for (String known : MODES) {
            if (known.equals(mode)) return known;
        }
        return PREF_DEFAULT;
    }

    /** The position of {@code mode} in {@link #MODES}, for a single-choice list. */
    public static int modeIndex(@Nullable String mode) {
        String valid = validMode(mode);
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(valid)) return i;
        }
        return 1; // unreachable: the default is in MODES
    }

    /** The notification button offers the other of always and the default. */
    public static String toggledMode(String mode) {
        return MODE_ALWAYS.equals(mode) ? PREF_DEFAULT : MODE_ALWAYS;
    }

    /* ---- the preference ---- */

    /** The mode the user wants; the default when unset or not a known mode. */
    public static String mode(@NonNull Context context) {
        return validMode(prefs(context).getString(PREF_KEY, PREF_DEFAULT));
    }

    public static void setMode(@NonNull Context context, String mode) {
        prefs(context).edit().putString(PREF_KEY, validMode(mode)).apply();
    }

    /** The notification button: between always and the default; returns the new mode. */
    public static String toggleAlways(@NonNull Context context) {
        String next = toggledMode(mode(context));
        setMode(context, next);
        return next;
    }

    /* ---- what the UI shows: pure functions of the state ---- */

    /** The notification button offers the other state: "Let sleep" in always, "Keep awake" otherwise. */
    @StringRes
    public static int notificationAction(String mode) {
        return MODE_ALWAYS.equals(mode) ? R.string.keep_cpu_awake_action_off
                : R.string.keep_cpu_awake_action_on;
    }

    /**
     * The notification's line says whether the CPU is being kept awake and
     * why: by choice (always) or for the sound that is playing.
     */
    @StringRes
    public static int notificationText(boolean held, String mode) {
        if (!held) return R.string.service_notify_text;
        return MODE_ALWAYS.equals(mode) ? R.string.service_notify_text_keep_awake
                : R.string.service_notify_text_keep_awake_audio;
    }

    /** The short name of a mode, for the menu item and the toast. */
    @StringRes
    public static int modeLabel(String mode) {
        switch (validMode(mode)) {
            case MODE_OFF:
                return R.string.keep_cpu_awake_mode_off;
            case MODE_ALWAYS:
                return R.string.keep_cpu_awake_mode_always;
            default:
                return R.string.keep_cpu_awake_mode_while_audio;
        }
    }

    /** What a choice means, told once when it is made. */
    @StringRes
    public static int modeToast(String mode) {
        switch (validMode(mode)) {
            case MODE_OFF:
                return R.string.keep_cpu_awake_toast_off;
            case MODE_ALWAYS:
                return R.string.keep_cpu_awake_toast_on;
            default:
                return R.string.keep_cpu_awake_toast_audio;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }
}
