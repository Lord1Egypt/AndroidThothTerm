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

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.LogEntry;
import com.thothterm.logging.LogExporter;
import com.thothterm.logging.LogLevel;
import com.thothterm.logging.ThothLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live in-app diagnostics log viewer. Reads the bounded in-memory buffer that
 * {@link ThothLog} maintains; it never touches the renderer or PTY.
 */
public class LogsActivity extends AppCompatActivity {
    private static final long REFRESH_INTERVAL_MS = 750;
    private static final int MAX_ROWS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LogsAdapter adapter;
    private ListView listView;
    private TextView emptyView;
    private EditText search;

    private int minRank = LogLevel.VERBOSE.rank;
    private String categoryFilter;
    private boolean paused;
    private boolean autoScroll = true;
    private long lastChange = -1;

    private MenuItem pauseItem;
    private MenuItem autoScrollItem;
    private ActivityResultLauncher<String> exportLauncher;

    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            if (!paused) {
                long change = ThothLog.getChangeCount();
                if (change != lastChange) {
                    lastChange = change;
                    applyFilter();
                }
            }
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_logs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            exportLauncher = registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/plain"),
                    uri -> {
                        if (uri != null) doExport(uri);
                    });
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        adapter = new LogsAdapter(this);
        listView = findViewById(R.id.logs_list);
        listView.setAdapter(adapter);
        emptyView = findViewById(R.id.logs_empty);
        search = findViewById(R.id.logs_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        setupLevelFilter();
        setupCategoryFilter();
        applyFilter();
    }

    private void setupLevelFilter() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.diagnostics_filter_all_levels));
        for (String name : LogLevel.names()) labels.add(name);

        Spinner spinner = findViewById(R.id.logs_level_filter);
        spinner.setAdapter(spinnerAdapter(labels));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                minRank = (position == 0) ? LogLevel.VERBOSE.rank : (position - 1);
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupCategoryFilter() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.diagnostics_filter_all_categories));
        for (String category : LogCategory.all()) labels.add(category);

        Spinner spinner = findViewById(R.id.logs_category_filter);
        spinner.setAdapter(spinnerAdapter(labels));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                categoryFilter = (position == 0) ? null : labels.get(position);
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void applyFilter() {
        if (adapter == null) return;

        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        List<LogEntry> all = ThothLog.snapshot();

        List<LogEntry> filtered = new ArrayList<>();
        int start = Math.max(0, all.size() - MAX_ROWS);
        for (int i = start; i < all.size(); i++) {
            LogEntry entry = all.get(i);
            if (entry.level.rank > minRank) continue;
            if (categoryFilter != null && !categoryFilter.equals(entry.category)) continue;
            if (query.length() > 0 && !matches(entry, query)) continue;
            filtered.add(entry);
        }

        adapter.setEntries(filtered);
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        if (autoScroll && adapter.getCount() > 0) {
            listView.setSelection(adapter.getCount() - 1);
        }
    }

    private boolean matches(LogEntry entry, String query) {
        return entry.message.toLowerCase(Locale.US).contains(query)
                || entry.category.toLowerCase(Locale.US).contains(query)
                || entry.level.label.toLowerCase(Locale.US).contains(query);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastChange = -1;
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logs, menu);
        pauseItem = menu.findItem(R.id.logs_pause);
        autoScrollItem = menu.findItem(R.id.logs_auto_scroll);
        updateMenuState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.logs_pause) {
            paused = !paused;
            updateMenuState();
        } else if (id == R.id.logs_auto_scroll) {
            autoScroll = !autoScroll;
            item.setChecked(autoScroll);
            if (autoScroll) applyFilter();
        } else if (id == R.id.logs_clear) {
            confirmClear();
        } else if (id == R.id.logs_export) {
            if (exportLauncher != null) {
                exportLauncher.launch(ThothLog.suggestedExportName());
            } else {
                Toast.makeText(this, R.string.diagnostics_export_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void updateMenuState() {
        if (pauseItem != null) {
            pauseItem.setTitle(paused
                    ? R.string.diagnostics_resume
                    : R.string.diagnostics_pause);
        }
        if (autoScrollItem != null) {
            autoScrollItem.setChecked(autoScroll);
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.diagnostics_clear_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ThothLog.clear();
                    lastChange = ThothLog.getChangeCount();
                    applyFilter();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doExport(Uri uri) {
        LogExporter.exportAsync(this, uri, success -> Toast.makeText(
                LogsActivity.this,
                success ? R.string.diagnostics_export_success : R.string.diagnostics_export_failure,
                Toast.LENGTH_SHORT).show());
    }
}
