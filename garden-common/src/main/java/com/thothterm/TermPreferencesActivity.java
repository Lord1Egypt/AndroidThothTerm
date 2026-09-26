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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NavUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.LogExporter;
import com.thothterm.logging.ThothLog;
import com.thothterm.utils.ThemeManager;



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
            ThothLog.i(LogCategory.UI, "Theme mode changed; restarting activity");
            // Do no not inform user!
            restart(0);
        }
        ThothLog.onPreferenceChanged(key);
    }

    public static class TermPreferencesFragment extends PreferenceFragmentCompat {
        private ActivityResultLauncher<String> export_launcher;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                export_launcher = registerForActivityResult(
                        new ActivityResultContracts.CreateDocument("text/plain"),
                        uri -> {
                            if (uri != null) doExportLogs(uri);
                        });
            }
        }

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

            {
                Preference pref = findPreference("diagnostics_view_logs");
                if (pref != null)
                    pref.setOnPreferenceClickListener(p -> {
                        Context prefContext = getContext();
                        if (prefContext != null)
                            startActivity(new Intent(prefContext, LogsActivity.class));
                        return true;
                    });
            }

            {
                Preference pref = findPreference("diagnostics_clear");
                if (pref != null)
                    pref.setOnPreferenceClickListener(p -> {
                        Context prefContext = getContext();
                        if (prefContext != null) confirmClearLogs(prefContext);
                        return true;
                    });
            }

            {
                Preference pref = findPreference("diagnostics_export");
                if (pref != null)
                    pref.setOnPreferenceClickListener(p -> {
                        if (export_launcher != null) {
                            export_launcher.launch(ThothLog.suggestedExportName());
                        } else {
                            Context prefContext = getContext();
                            if (prefContext != null) {
                                Toast.makeText(prefContext,
                                        R.string.diagnostics_export_unavailable,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                        return true;
                    });
            }
        }

        private void confirmClearLogs(Context context) {
            new AlertDialog.Builder(context)
                    .setMessage(R.string.diagnostics_clear_confirm)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> ThothLog.clear())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void doExportLogs(Uri uri) {
            Context context = getContext();
            if (context == null) return;
            LogExporter.exportAsync(context, uri, success -> {
                if (!isAdded()) return;
                Toast.makeText(context,
                        success ? R.string.diagnostics_export_success
                                : R.string.diagnostics_export_failure,
                        Toast.LENGTH_SHORT).show();
            });
        }
    }
}
