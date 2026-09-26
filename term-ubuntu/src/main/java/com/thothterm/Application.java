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
import android.content.res.AssetManager;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.thothterm.linux.AndroidNetworkResolver;
import com.thothterm.lan.LanController;
import com.thothterm.linux.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.utils.ThemeManager;

import java.io.File;


public class Application extends android.app.Application {
    public static final String ID = BuildConfig.APPLICATION_ID;
    public static final String VER = BuildConfig.VERSION_NAME;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    public static final String APP_TAG = "ThothTerm";

    public static final String NOTIFICATION_CHANNEL_SESSIONS = BuildConfig.APPLICATION_ID + ".sessions";

    public static final String ACTION_OPEN_NEW_WINDOW = BuildConfig.APPLICATION_ID + ".OPEN_NEW_WINDOW";
    public static final String ACTION_RUN_SHORTCUT = BuildConfig.APPLICATION_ID + ".RUN_SHORTCUT";
    public static final String ACTION_RUN_SCRIPT = BuildConfig.APPLICATION_ID + ".RUN_SCRIPT";

    public static final String ARGUMENT_TARGET_WINDOW = "target_window";
    public static final String ARGUMENT_WINDOW_ID = "window_id";
    /* arguments for use by external applications */
    public static final String ARGUMENT_SHELL_COMMAND = "com.thothterm.Command";
    public static final String ARGUMENT_WINDOW_HANDLE = "com.thothterm.WindowHandle";

    public static Settings settings;

    private static File xbindir;

    private static File rootdir;
    private static File etcdir;
    private static File libdir;
    private static File cachedir;


    public static File getTmpDir() {
        return cachedir;
    }

    public static String getTmpPath() {
        return getTmpDir().getAbsolutePath();
    }

    public static File getScriptFile() {
        return new File(etcdir, "mkshrc");
    }

    public static String getScriptFilePath() {
        return getScriptFile().getPath();
    }

    public static String buildPATH() {
        String s;
        String path = Application.xbindir.getPath();

        s = System.getenv("PATH");
        if (!TextUtils.isEmpty(s))
            path += File.pathSeparator + s;

        return path;
    }

    public static String buildLoaderLibraryPath(String extra) {
        String path = Application.libdir.getPath();

        if (!TextUtils.isEmpty(extra))
            path += File.pathSeparator + extra;

        String orig = System.getenv("LD_LIBRARY_PATH");
        if (!TextUtils.isEmpty(orig))
            path += File.pathSeparator + orig;

        return path;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ThothLog.init(this);
        ThothLog.i(LogCategory.APP, "Application start version=" + VER
                + " flavor=" + BuildConfig.FLAVOR + "-" + BuildConfig.BUILD_TYPE);
        AndroidNetworkResolver.init(this);
        RootfsManager.init(this);
        LanController.init(this);

        // enable Material3 dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this);

        rootdir = getFilesDir().getParentFile();
        etcdir = new File(rootdir, "etc");
        libdir = new File(getApplicationInfo().nativeLibraryDir);
        xbindir = libdir;
        cachedir = getCacheDir();

        ThothLog.i(LogCategory.STORAGE, "Initializing application private directories");

        setupPreferences();
        ThemeManager.migrateFileSelectionThemeMode(this);

        TypefaceSetting.create(getAssets());

        ThothLog.i(LogCategory.INSTALLER, "Bootstrap start");
        Installer.install_directory(etcdir, false);
        install_skeleton();

        // Note at this point xbindir == libdir
        File exe = new File(xbindir, Installer.APPINFO_COMMAND);
        if (!exe.canExecute()) {
            // Old Android (API Level < 17) - libraries are without executable bit set
            xbindir = new File(rootdir, ".x");
            Installer.install_directory(xbindir, false);

            Installer.copy_executable(exe, xbindir);
        }

        Installer.installAppScriptFile();
        ThothLog.i(LogCategory.INSTALLER, "Bootstrap complete");
        ThothLog.i(LogCategory.STORAGE, "Application private directories ready");
    }

    private void setupPreferences() {
        boolean updated = false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();

        String pref_home_path = getString(R.string.key_home_path_preference);
        if (!prefs.contains(pref_home_path)) {
            String path = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
            editor.putString(pref_home_path, path);
            updated = true;
        }

        // Ubuntu used to inherit the regular terminal's visible "cd ~" PTY
        // startup command. The runtime already starts in /home/thoth.
        String initialCommand = getString(R.string.key_initialcommand_preference);
        if ("cd ~".equals(prefs.getString(initialCommand, null))) {
            editor.remove(initialCommand);
            updated = true;
        }

        // clean-up obsolete preferences:
        // "allow_prepend_path" was removed in 3.1.0
        if (prefs.contains("allow_prepend_path")) {
            // Note depends from do_path_extensions
            editor.remove("allow_prepend_path");
            updated = true;
        }
        // "do_path_extensions" was removed in 3.1.0
        if (prefs.contains("do_path_extensions")) {
            editor.remove("do_path_extensions");
            updated = true;
        }

        if (updated) editor.apply();

        settings = new Settings(this, prefs);
    }

    private boolean install_skeleton() {
        String asset_path = "skel";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String homedir = prefs.getString(getString(R.string.key_home_path_preference), "");

        AssetManager am = getAssets();
        try {
            String[] list = am.list(asset_path);
            if (list == null) return true;
            for (String item : list)
                if (!install_skeleton(homedir, am, asset_path, item))
                    return false;
        } catch (Exception ignore) {
        }
        return true;
    }

    protected final boolean install_skeleton(String homedir, AssetManager am, String asset_path, String item) {
        File target = new File(homedir, "." + item);

        if (target.exists()) return true;

        return Installer.install_asset(am, asset_path + "/" + item, target);
    }
}
