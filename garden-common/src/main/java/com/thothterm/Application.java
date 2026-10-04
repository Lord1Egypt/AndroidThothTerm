/*
 * Copyright (C) 2018-2026 Roumen Petrov.  All rights reserved.
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

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.thothterm.linux.AndroidNetworkResolver;
import com.thothterm.lan.LanController;
import com.thothterm.linux.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.utils.ThemeManager;


public class Application extends android.app.Application {
    /*
     * Set from the running package, not BuildConfig: this class lives in the
     * garden-common library, whose BuildConfig describes the library rather
     * than the edition that ships it.
     */
    /** This edition's package name, e.g. com.thothterm.<edition>. */
    public static String ID = "";
    /** This edition's versionName. */
    public static String VER = "";
    public static long VERSION_CODE;
    /** "embedded" when the APK carries the rootfs, "download" when it fetches it. */
    public static String ROOTFS_SOURCE = "";
    public static boolean DEBUGGABLE;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    public static final String APP_TAG = "ThothTerm";

    /** Per-app channel id; the same "<package>.sessions" form every edition uses. */
    public static String NOTIFICATION_CHANNEL_SESSIONS = "";

    public static final String ARGUMENT_TARGET_WINDOW = "target_window";
    public static final String ARGUMENT_WINDOW_ID = "window_id";

    public static Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();

        ID = getPackageName();
        NOTIFICATION_CHANNEL_SESSIONS = ID + ".sessions";
        DEBUGGABLE = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(ID, 0);
            VER = info.versionName;
            VERSION_CODE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            VER = "?";
        }

        ThothLog.init(this);
        AndroidNetworkResolver.init(this);
        RootfsManager.init(this);
        ROOTFS_SOURCE = RootfsManager.get().isRootfsEmbedded() ? "embedded" : "download";
        ThothLog.i(LogCategory.APP, "Application start version=" + VER
                + " rootfs=" + ROOTFS_SOURCE + " debuggable=" + DEBUGGABLE);
        LanController.init(this);
        com.thothterm.dock.ThothDock.init(this);

        // enable Material3 dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this);

        setupPreferences();
        ThemeManager.migrateFileSelectionThemeMode(this);

        TypefaceSetting.create(getAssets());
    }

    private void setupPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        settings = new Settings(this, prefs);
    }
}
