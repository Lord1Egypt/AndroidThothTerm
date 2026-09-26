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

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Runtime consent for the ongoing terminal-service notification.
 *
 * <p>From Android 13 (API 33) {@code POST_NOTIFICATIONS} is a runtime
 * permission. Declaring it is not enough: until the user grants it the platform
 * accepts the foreground-service notification but never presents it, so the
 * service runs with no visible representation. ThothTerm therefore asks once,
 * and simply does without the notification if the answer is no — the service
 * itself is unaffected either way.
 */
public final class NotificationPermission {
    /** Guarded by {@code allowBackup="false"}, so this never survives a reinstall. */
    private static final String PREF_ASKED = "notification_permission_asked";

    private NotificationPermission() {
    }

    /** True once the platform stopped gating the service notification behind consent. */
    public static boolean isRuntimeGated(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU /*API Level 33*/;
    }

    /**
     * Pure decision so the rule stays testable off-device. Ask only where the
     * platform gates the notification, only while consent is missing, and only
     * the first time — a repeat prompt is spam the system would suppress anyway.
     */
    public static boolean shouldRequest(int sdkInt, boolean granted, boolean alreadyAsked) {
        if (!isRuntimeGated(sdkInt)) return false;
        if (granted) return false;
        return !alreadyAsked;
    }

    public static boolean isGranted(Context context) {
        if (!isRuntimeGated(Build.VERSION.SDK_INT)) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean wasAsked(Context context) {
        return prefs(context).getBoolean(PREF_ASKED, false);
    }

    /**
     * Records the ask <em>before</em> the dialog is shown: the result callback
     * does not fire when the activity is recreated underneath it, and a lost
     * record would re-prompt on the next launch.
     */
    static void recordAsked(Context context) {
        prefs(context).edit().putBoolean(PREF_ASKED, true).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences("thothterm_permissions", Context.MODE_PRIVATE);
    }
}
