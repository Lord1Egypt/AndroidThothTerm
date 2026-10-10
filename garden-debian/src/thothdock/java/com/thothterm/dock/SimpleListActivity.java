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

package com.thothterm.dock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.AppCompatActivity;
import com.thothterm.debian.R;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A read-only list backed by one Engine API call, fetched when the page opens
 * or comes back to the front and never on a timer.
 */
abstract class SimpleListActivity extends AppCompatActivity {
    /** One row. */
    static final class Item {
        final String title;
        final String badge;
        final String sub;
        final String meta;

        Item(String title, String badge, String sub, String meta) {
            this.title = title;
            this.badge = badge;
            this.sub = sub;
            this.meta = meta;
        }
    }

    /** What one fetch produced. */
    static final class Result {
        final List<Item> items = new ArrayList<>();
        String summary = "";
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Item> rows = new ArrayList<>();
    private ApiClient api;
    private BaseAdapter adapter;
    private TextView summary;
    private TextView empty;

    abstract int titleRes();

    abstract int emptyRes();

    abstract Result load(ApiClient api) throws IOException;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        Toolbar toolbar = findViewById(com.thothterm.R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(titleRes());
        }
        ThothDock dock = ThothDock.getIfInitialized();
        api = new ApiClient(dock == null ? "" : dock.socketPath());
        ListView list = findViewById(R.id.simple_list);
        View header = LayoutInflater.from(this).inflate(R.layout.header_simple_list, list, false);
        summary = header.findViewById(R.id.simple_summary);
        empty = header.findViewById(R.id.simple_empty);
        list.addHeaderView(header, null, false);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int position) {
                return rows.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                View v = convert != null ? convert
                        : LayoutInflater.from(SimpleListActivity.this).inflate(R.layout.item_simple, parent, false);
                Item it = rows.get(position);
                ((TextView) v.findViewById(R.id.simple_title)).setText(it.title);
                ((TextView) v.findViewById(R.id.simple_badge)).setText(it.badge);
                ((TextView) v.findViewById(R.id.simple_sub)).setText(it.sub);
                ((TextView) v.findViewById(R.id.simple_meta)).setText(it.meta);
                return v;
            }
        };
        list.setAdapter(adapter);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            worker.execute(() -> {
                Result r = null;
                String error = null;
                try {
                    r = load(api);
                } catch (IOException e) {
                    error = e.getMessage();
                    ThothLog.w(LogCategory.RUNTIME, getClass().getSimpleName() + ": " + error);
                }
                final Result shown = r;
                ui.post(() -> show(shown));
            });
        } catch (RuntimeException rejected) {
            // The page is closing.
        }
    }

    private void show(Result r) {
        if (isFinishing() || isDestroyed()) return;
        rows.clear();
        if (r == null) {
            summary.setText("");
            empty.setText(R.string.list_engine_offline);
            empty.setVisibility(View.VISIBLE);
        } else {
            rows.addAll(r.items);
            summary.setText(r.summary + (r.items.isEmpty() ? "" : "\n" + getString(R.string.list_readonly_hint)));
            empty.setText(emptyRes());
            empty.setVisibility(r.items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
