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

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.AppCompatActivity;
import com.thothterm.debian.R;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Containers screen. A client of the ThothDock API exactly like the Docker
 * CLI: it lists, starts, stops, restarts, deletes and reads logs through the
 * engine's socket, and keeps no container state of its own, so the CLI and
 * this screen always agree. Nothing here is measured from cgroups -- there are
 * none -- so no CPU or memory figures are shown.
 */
public class ContainersActivity extends AppCompatActivity {
    private static final long POLL_MS = 3000;

    /** What one poll saw. */
    private static final class Snapshot {
        boolean online;
        String error;
        String version = "";
        String api = "";
        String arch = "";
        int images;
        List<ContainerRow> containers = new ArrayList<>();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService poller = Executors.newSingleThreadExecutor();
    private final ExecutorService actions = Executors.newCachedThreadPool();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Set<String> busy = new HashSet<>();
    private final List<ContainerRow> rows = new ArrayList<>();

    private ApiClient api;
    private boolean started;
    private RowAdapter adapter;
    private View header;
    private TextView engineState;
    private TextView engineDetail;
    private TextView statRunning;
    private TextView statStopped;
    private TextView statImages;
    private TextView offlineHint;
    private Button startEngine;
    private TextView emptyView;

    private final Runnable pollLoop = new Runnable() {
        @Override
        public void run() {
            poll();
            if (started) ui.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_containers);
        Toolbar toolbar = findViewById(com.thothterm.R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(com.thothterm.R.string.containers);
        }
        ThothDock dock = ThothDock.getIfInitialized();
        api = new ApiClient(dock == null ? "" : dock.socketPath());

        header = LayoutInflater.from(this).inflate(R.layout.header_containers, null, false);
        engineState = header.findViewById(R.id.engine_state);
        engineDetail = header.findViewById(R.id.engine_detail);
        statRunning = header.findViewById(R.id.stat_running);
        statStopped = header.findViewById(R.id.stat_stopped);
        statImages = header.findViewById(R.id.stat_images);
        offlineHint = header.findViewById(R.id.offline_hint);
        startEngine = header.findViewById(R.id.start_engine);
        startEngine.setOnClickListener(v -> {
            ThothDock d = ThothDock.getIfInitialized();
            if (d != null) d.ensureRunning();
            Toast.makeText(this, R.string.containers_starting_engine, Toast.LENGTH_SHORT).show();
        });
        ListView list = findViewById(R.id.containers_list);
        list.addHeaderView(header, null, false);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        emptyView = header.findViewById(R.id.containers_empty);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        ui.post(pollLoop);
    }

    @Override
    protected void onStop() {
        started = false;
        ui.removeCallbacks(pollLoop);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        poller.shutdownNow();
        actions.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_containers, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.menu_refresh) {
            poll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- polling ---------------------------------------------------------------

    private void poll() {
        if (!polling.compareAndSet(false, true)) return;
        try {
            poller.execute(() -> {
                Snapshot s = fetch();
                ui.post(() -> {
                    polling.set(false);
                    if (!isFinishing() && !isDestroyed()) render(s);
                });
            });
        } catch (RuntimeException rejected) {
            polling.set(false); // the activity is going away
        }
    }

    private Snapshot fetch() {
        Snapshot s = new Snapshot();
        try {
            JSONObject v = api.getObject("/version");
            s.version = v.optString("Version", "");
            s.api = v.optString("ApiVersion", "");
            s.arch = v.optString("Arch", "");
            s.containers = ContainerRow.parse(api.getArray("/containers/json?all=1"));
            JSONArray images = api.getArray("/images/json");
            s.images = images.length();
            s.online = true;
        } catch (IOException e) {
            s.online = false;
            s.error = e.getMessage();
        }
        return s;
    }

    private void render(Snapshot s) {
        if (s.online) {
            int running = 0;
            for (ContainerRow r : s.containers) if (r.isRunning()) running++;
            engineState.setText(R.string.containers_engine_running);
            engineState.setTextColor(getColor(R.color.thothdock_success));
            engineDetail.setText(getString(R.string.containers_engine_detail, s.version, s.api, s.arch));
            statRunning.setText(String.valueOf(running));
            statStopped.setText(String.valueOf(s.containers.size() - running));
            statImages.setText(String.valueOf(s.images));
            offlineHint.setVisibility(View.GONE);
            startEngine.setVisibility(View.GONE);
            emptyView.setVisibility(s.containers.isEmpty() ? View.VISIBLE : View.GONE);
            rows.clear();
            rows.addAll(s.containers);
        } else {
            engineState.setText(R.string.containers_engine_offline);
            engineState.setTextColor(getColor(R.color.thothdock_error));
            engineDetail.setText("");
            statRunning.setText("–");
            statStopped.setText("–");
            statImages.setText("–");
            offlineHint.setVisibility(View.VISIBLE);
            startEngine.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            rows.clear();
        }
        adapter.notifyDataSetChanged();
    }

    // ---- actions ---------------------------------------------------------------

    private void act(ContainerRow row, String method, String path, int timeoutMs, int errorRes) {
        if (!busy.add(row.id)) return;
        adapter.notifyDataSetChanged();
        try {
            actions.execute(() -> {
                String failure = null;
                try {
                    api.act(method, path, timeoutMs);
                } catch (IOException e) {
                    failure = e.getMessage();
                    ThothLog.w(LogCategory.RUNTIME, "Containers action " + method + " " + path + " failed: " + failure);
                }
                final String message = failure;
                ui.post(() -> {
                    busy.remove(row.id);
                    if (message != null && !isDestroyed()) {
                        Toast.makeText(this, getString(errorRes, message), Toast.LENGTH_LONG).show();
                    }
                    poll();
                });
            });
        } catch (RuntimeException rejected) {
            busy.remove(row.id);
        }
    }

    private void confirmDelete(ContainerRow row) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.containers_delete_title, row.name))
                .setMessage(row.isRunning()
                        ? R.string.containers_delete_running_message : R.string.containers_delete_message)
                .setPositiveButton(R.string.containers_delete, (d, w) ->
                        act(row, "DELETE", "/containers/" + row.id + "?force=1", 30000, R.string.containers_error_delete))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openShell(ContainerRow row) {
        if (!row.hasSafeName()) {
            Toast.makeText(this, R.string.containers_error_name, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent().setClassName(getPackageName(), "com.thothterm.TermActivity")
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(com.thothterm.dock.ThothDock.EXTRA_TERMINAL_INPUT, "docker exec -it " + row.name + " sh\n");
        startActivity(intent);
    }

    private void openLogs(ContainerRow row) {
        startActivity(new Intent(this, ContainerLogsActivity.class)
                .putExtra(ContainerLogsActivity.EXTRA_ID, row.id)
                .putExtra(ContainerLogsActivity.EXTRA_NAME, row.name));
    }

    // ---- list ------------------------------------------------------------------

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public ContainerRow getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return rows.get(position).id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(ContainersActivity.this).inflate(R.layout.item_container, parent, false);
            ContainerRow r = rows.get(position);
            ((TextView) v.findViewById(R.id.item_name)).setText(r.name);
            ((TextView) v.findViewById(R.id.item_image)).setText(r.image);
            ((TextView) v.findViewById(R.id.item_status)).setText(r.status);
            TextView state = v.findViewById(R.id.item_state);
            state.setText(r.state);
            int color = stateColor(r.state);
            state.setTextColor(color);
            View dot = v.findViewById(R.id.item_dot);
            Drawable bg = dot.getBackground().mutate();
            bg.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            dot.setBackground(bg);
            TextView ports = v.findViewById(R.id.item_ports);
            if (r.ports.isEmpty()) {
                ports.setVisibility(View.GONE);
            } else {
                ports.setVisibility(View.VISIBLE);
                ports.setText(android.text.TextUtils.join("   ", r.ports));
            }
            boolean working = busy.contains(r.id);
            Button toggle = v.findViewById(R.id.btn_toggle);
            toggle.setText(r.isRunning() ? R.string.containers_stop : R.string.containers_start);
            toggle.setEnabled(!working);
            toggle.setOnClickListener(x -> {
                if (r.isRunning()) {
                    act(r, "POST", "/containers/" + r.id + "/stop?t=10", 30000, R.string.containers_error_stop);
                } else {
                    act(r, "POST", "/containers/" + r.id + "/start", 30000, R.string.containers_error_start);
                }
            });
            Button restart = v.findViewById(R.id.btn_restart);
            restart.setEnabled(!working);
            restart.setOnClickListener(x ->
                    act(r, "POST", "/containers/" + r.id + "/restart?t=10", 40000, R.string.containers_error_restart));
            v.findViewById(R.id.btn_logs).setOnClickListener(x -> openLogs(r));
            Button shell = v.findViewById(R.id.btn_shell);
            shell.setVisibility(r.isRunning() ? View.VISIBLE : View.GONE);
            shell.setOnClickListener(x -> openShell(r));
            Button delete = v.findViewById(R.id.btn_delete);
            delete.setEnabled(!working);
            delete.setOnClickListener(x -> confirmDelete(r));
            return v;
        }
    }

    private int stateColor(String state) {
        switch (state) {
            case "running":
                return getColor(R.color.thothdock_state_running);
            case "created":
                return getColor(R.color.thothdock_state_stopped);
            case "exited":
                return getColor(R.color.thothdock_state_exited);
            default:
                return getColor(R.color.thothdock_state_error);
        }
    }
}
