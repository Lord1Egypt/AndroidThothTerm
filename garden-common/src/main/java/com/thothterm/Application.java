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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.thothterm.garden.AndroidNetworkResolver;
import com.thothterm.garden.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.utils.ThemeManager;


public class Application extends android.app.Application {
    /** This edition's package name, e.g. com.thothterm.debian. */
    public static String ID = "";
    /** This edition's versionName. */
    public static String VER = "";
    public static long VERSION_CODE;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    public static final String APP_TAG = "ThothTerm";

    public static final String NOTIFICATION_CHANNEL_SESSIONS = "garden.sessions";

    public static final String ARGUMENT_TARGET_WINDOW = "target_window";
    public static final String ARGUMENT_WINDOW_ID = "window_id";

    public static Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();

        ID = getPackageName();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(ID, 0);
            VER = info.versionName;
            VERSION_CODE = info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            VER = "?";
        }

        ThothLog.init(this);
        ThothLog.i(LogCategory.APP, "Application start version=" + VER);

        // enable Material3 dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        settings = new Settings(this, prefs);
        ThemeManager.migrateFileSelectionThemeMode(this);

        TypefaceSetting.create(getAssets());

        AndroidNetworkResolver.init(this);
        RootfsManager.init(this);
        ThothLog.i(LogCategory.RUNTIME, "Garden edition "
                + RootfsManager.get().distro().editionName()
                + " distro=" + RootfsManager.get().distro().releaseLine());
    }
}
