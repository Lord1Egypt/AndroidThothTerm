/*
 * Copyright (C) 2022-2024 Roumen Petrov.  All rights reserved.
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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;


public class TypefaceSetting {
    private static Typeface typeface = Typeface.MONOSPACE;

    private final TextView license;
    @Settings.FontSource
    private int source;

    private TypefaceSetting(Activity activity) {
        source = Application.settings.getFontSource();

        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_font_source, null);

        license = view.findViewById(R.id.buildin_license);
        try {
            AssetManager am = activity.getAssets();
            InputStream in = am.open("font/DejaVu.lic");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            StringBuilder buffer = new StringBuilder();
            String line;
            while (true) {
                line = reader.readLine();
                if (line == null) break;
                buffer.append(line).append("\n");
            }
            license.setText(buffer);
        } catch (Exception ignore) {
        }
        if (source != Settings.FontSource.EMBED)
            license.setVisibility(View.GONE);

        {
            RadioButton radio = view.findViewById(R.id.system_font);
            radio.setOnClickListener(this::onFontSourceButtonClicked);
            if (source == Settings.FontSource.SYSTEM)
                radio.setChecked(true);
        }
        {
            RadioButton radio = view.findViewById(R.id.buildin_font);
            radio.setOnClickListener(this::onFontSourceButtonClicked);
            if (source == Settings.FontSource.EMBED)
                radio.setChecked(true);
        }

        final AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> saveFontSource(activity))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void create(AssetManager am) {
        typeface = Typeface.createFromAsset(am, "font/DejaVuSansMono.ttf");
    }

    public static boolean chose(Activity activity) {
        if (activity == null) return false;

        new TypefaceSetting(activity);
        return true;
    }

    public static Typeface getTypeface() {
        Settings settings = Application.settings;
        if (settings == null) return Typeface.MONOSPACE;
        if (settings.getFontSource() == Settings.FontSource.EMBED)
            return typeface;
        return Typeface.MONOSPACE;
    }

    private void onFontSourceButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        if (!checked) return;

        int id = view.getId();
        if (id == R.id.system_font)
            source = Settings.FontSource.SYSTEM;
        else if (id == R.id.buildin_font)
            source = Settings.FontSource.EMBED;
        else
            return;

        if (source != Settings.FontSource.EMBED)
            license.setVisibility(View.GONE);
        else
            license.setVisibility(View.VISIBLE);
    }

    private void saveFontSource(Context context) {
        String key = context.getString(R.string.key_fontsource_preference);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, Integer.toString(source));
        editor.apply();
        Application.settings.parsePreference(context, preferences, key);
    }
}
