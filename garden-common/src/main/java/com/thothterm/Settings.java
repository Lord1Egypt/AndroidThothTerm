/*
 * Copyright (C) 2018-2025 Roumen Petrov.  All rights reserved.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jackpal.androidterm.emulatorview.ColorScheme;


public class Settings {
    // foreground and background as ARGB color pair
    /* Note keep synchronized with names in @array.entries_color_preference
    and index in @array.entryvalues_color_preference. */
    public static final ColorScheme[] color_schemes = {
            new ColorScheme(0xFF000000, 0xFFFFFFFF) /*black on white*/,
            new ColorScheme(0xFFFFFFFF, 0xFF000000) /*white on black*/,
            new ColorScheme(0xFFFFFFFF, 0xFF344EBD) /*white on blue*/,
            new ColorScheme(0xFF00FF00, 0xFF000000) /*green on black*/,
            new ColorScheme(0xFFFFB651, 0xFF000000) /*amber on black*/,
            new ColorScheme(0xFFFF0113, 0xFF000000) /*red on black*/,
            new ColorScheme(0xFF33B5E5, 0xFF000000) /*holo-blue on black*/,
            new ColorScheme(0xFF657B83, 0xFFFDF6E3) /*solarized light*/,
            new ColorScheme(0xFF839496, 0xFF002B36) /*solarized dark*/,
            new ColorScheme(0xFFAAAAAA, 0xFF000000) /*linux console*/,
            new ColorScheme(0xFFDCDCCC, 0xFF2C2C2C) /*dark pastels*/
    };

    @FontSource
    private int font_source;
    @Orientation
    private int orientation;
    private String initial_command;
    private boolean source_sys_shrc;


    public Settings(Context context) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()));
    }

    public Settings(Context context, SharedPreferences preferences) {
        this(context.getResources(), preferences);
    }

    public Settings(Resources r, SharedPreferences preferences) {
        font_source = parseInteger(preferences,
                r.getString(R.string.key_fontsource_preference),
                FontSource.EMBED);
        orientation = parseInteger(preferences,
                r.getString(R.string.key_orientation_preference),
                r.getInteger(R.integer.pref_orientation_default));
        initial_command = parseString(preferences,
                r.getString(R.string.key_initialcommand_preference),
                r.getString(R.string.pref_initialcommand_default));
        source_sys_shrc = parseBoolean(preferences,
                r.getString(R.string.key_source_sys_shrc_preference),
                r.getBoolean(R.bool.pref_source_sys_shrc_default));
    }

    @NonNull
    public static String prepareInitialCommand(Context context, String extraCommand) {
        Settings settings = new Settings(context);
        String cmd = settings.initial_command;
        if (cmd == null /*just in case*/) cmd = "";
        if (!TextUtils.isEmpty(extraCommand)) {
            cmd = TextUtils.isEmpty(cmd) ? extraCommand : cmd + "\r" + extraCommand;
        }
        return cmd;
    }

    public void parsePreference(Context context, SharedPreferences preferences, String key) {
        if (TextUtils.isEmpty(key)) return;

        if (parseFontSource(context, preferences, key)) return;
        if (parseOrientation(context, preferences, key)) return;
        if (parseInitialCommand(context, preferences, key)) return;
        parseSourceSysRC(context, preferences, key);
    }

    @FontSource
    public int getFontSource() {
        return font_source;
    }

    @Orientation
    public int getOrientation() {
        return orientation;
    }

    public boolean sourceSystemShellStartupFile() {
        return source_sys_shrc;
    }

    private boolean parseBoolean(SharedPreferences preferences, String key, boolean def) {
        try {
            return preferences.getBoolean(key, def);
        } catch (Exception ignored) {
        }
        return def;
    }

    private int parseInteger(SharedPreferences preferences, String key, int def) {
        try {
            String value = preferences.getString(key, null);
            if (value == null) return def;
            return Integer.decode(value);
        } catch (Exception ignored) {
        }
        return def;
    }

    private String parseString(SharedPreferences preferences, String key, String def) {
        try {
            return preferences.getString(key, def);
        } catch (Exception ignored) {
        }
        return def;
    }

    private boolean parseFontSource(Context context, SharedPreferences preferences, String key) {
        String pref = context.getString(R.string.key_fontsource_preference);
        if (!key.equals(pref)) return false;

        int value = parseInteger(preferences, key, font_source);
        font_source = (value == FontSource.EMBED)
                ? FontSource.EMBED
                : FontSource.SYSTEM;
        return true;
    }

    private boolean parseOrientation(Context context, SharedPreferences preferences, String key) {
        String pref = context.getString(R.string.key_orientation_preference);
        if (!key.equals(pref)) return false;

        int value = parseInteger(preferences, key, orientation);
        switch (value) {
            case Orientation.AUTOMATIC:
            case Orientation.LANDSCAPE:
            case Orientation.PORTRAIT:
            case Orientation.SYSTEM:
                orientation = value;
                break;
            default:
                Resources r = context.getResources();
                orientation = r.getInteger(R.integer.pref_orientation_default);
        }
        return true;
    }

    private boolean parseInitialCommand(Context context, SharedPreferences preferences, String key) {
        String pref = context.getString(R.string.key_initialcommand_preference);
        if (!key.equals(pref)) return false;

        initial_command = parseString(preferences, key, initial_command);
        return true;
    }

    private void parseSourceSysRC(Context context, SharedPreferences preferences, String key) {
        String pref = context.getString(R.string.key_source_sys_shrc_preference);
        if (!key.equals(pref)) return;

        boolean value = parseBoolean(preferences, key, source_sys_shrc);
        if (value != source_sys_shrc) {
            source_sys_shrc = value;
            Installer.installAppScriptFile();
        }
    }

    @IntDef({
            FontSource.SYSTEM,
            FontSource.EMBED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FontSource {
        int SYSTEM = 1; // default
        int EMBED = 2;
    }

    @IntDef({
            Orientation.AUTOMATIC,
            Orientation.LANDSCAPE,
            Orientation.PORTRAIT,
            Orientation.SYSTEM
    })
    @Retention(RetentionPolicy.SOURCE)
    // items match resource @array/entryvalues_orientation_preference
    public @interface Orientation {
        int AUTOMATIC = 0; // match resource @integer/pref_orientation_default
        int LANDSCAPE = 1;
        int PORTRAIT = 2;
        int SYSTEM = 3;
    }
}
