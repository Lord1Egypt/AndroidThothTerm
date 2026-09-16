/*
 * Copyright (C) 2018-2024 Roumen Petrov.  All rights reserved.
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
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.core.app.NavUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thothterm.utils.ConsoleStartupScript;
import com.thothterm.utils.ThemeManager;

import jackpal.androidterm.util.TermSettings;


public class TermPreferencesActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        {
            ActionBar action_bar = getSupportActionBar();
            if (action_bar != null) {
                action_bar.setDisplayHomeAsUpEnabled(true);
            }
        }

        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        loadPreferences();
    }

    @Override
    protected void onDestroy() {
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) // Respond to the action bar's Up/Home button
            NavUtils.navigateUpFromSameTask(this);
        else
            return super.onOptionsItemSelected(item);
        return true;
    }

    private void loadPreferences() {
        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new TermPreferencesFragment())
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (ThemeManager.PREF_THEME_MODE.equals(key)) {
            // Do no not inform user!
            restart(0);
        }
    }

    public static class TermPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            RecyclerView list = getListView();
            int left = list.getPaddingLeft();
            int top = list.getPaddingTop();
            int right = list.getPaddingRight();
            int bottom = list.getPaddingBottom();
            int spacing = Math.round(12 * getResources().getDisplayMetrics().density);
            list.setClipToPadding(false);
            ViewCompat.setOnApplyWindowInsetsListener(list, (target, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                target.setPadding(left, top + spacing + bars.top,
                        right, bottom + spacing + bars.bottom);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(list);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            // Load the preferences from an XML resource
            setPreferencesFromResource(R.xml.preferences, rootKey);

            {
                Preference pref = findPreference(getString(R.string.key_fontsource_preference));
                if (pref != null)
                    pref.setOnPreferenceClickListener(
                            preference -> TypefaceSetting.chose(getActivity()));
            }

            Context context = getContext();
            if (context != null) {
                TermSettings settings = new TermSettings(context);
                String homedir = settings.getHomePath();

                String pref_shellrc = getString(R.string.key_shellrc_preference);
                EditTextPreference pref = findPreference(pref_shellrc);
                if (pref != null)
                    pref.setText(ConsoleStartupScript.read(homedir));
            }
        }
    }
}
