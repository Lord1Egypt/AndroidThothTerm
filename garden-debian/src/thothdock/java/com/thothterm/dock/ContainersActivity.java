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
 * The ThothDock management screen: Containers, Images, Volumes and Web Panel
 * as in-place tabs under one header (branding, engine status, summary tiles).
 * A client of the ThothDock API exactly like the Docker CLI: it lists, starts,
 * stops, restarts, deletes and reads logs through the engine's socket, and
 * keeps no container state of its own, so the CLI and this screen always
 * agree. Nothing here is measured from cgroups -- there are none -- so no CPU
 * or memory figures are shown.
 *
 * <p>Switching tabs swaps the list's adapter; it never starts another
 * Activity. The one poll (3 s, only while the screen is visible) feeds the
 * tiles and whichever tab is showing, so no section is fetched twice. The Web
 * Panel tab is a one-row list driven by {@link WebPanelController}.</p>
 */
public class ContainersActivity extends AppCompatActivity {
    private static final long POLL_MS = 3000;
    private static final String STATE_TAB = "tab";
    private static final String STATE_SCROLL_POS = "scrollPos";
    private static final String STATE_SCROLL_TOP = "scrollTop";

    /** The four tabs, in chip order. */
    enum Tab {
        CONTAINERS(ThothDock.TAB_CONTAINERS), IMAGES(ThothDock.TAB_IMAGES),
        VOLUMES(ThothDock.TAB_VOLUMES), PANEL(ThothDock.TAB_PANEL);

        final String key;

        Tab(String key) {
            this.key = key;
        }

        static Tab of(String key) {
            for (Tab t : values()) if (t.key.equals(key)) return t;
            return CONTAINERS;
        }
    }

    /** What one poll saw. */
    private static final class Snapshot {
        boolean online;
        String error;
        String version = "";
        String api = "";
        String arch = "";
        int images;
        int volumes = -1;
        ListSources.Result imageList = new ListSources.Result();
        ListSources.Result volumeList = new ListSources.Result();
        List<ContainerRow> containers = new ArrayList<>();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService poller = Executors.newSingleThreadExecutor();
    private final ExecutorService actions = Executors.newCachedThreadPool();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Set<String> busy = new HashSet<>();
    private final List<ContainerRow> rows = new ArrayList<>();
    private final List<ListSources.Item> imageItems = new ArrayList<>();
    private final List<ListSources.Item> volumeItems = new ArrayList<>();
    private final int[] scrollPos = new int[Tab.values().length];
    private final int[] scrollTop = new int[Tab.values().length];

    private ApiClient api;
    private boolean started;
    private Tab tab = Tab.CONTAINERS;
    private Snapshot last;
    private ListView list;
    private RowAdapter adapter;
    private ItemAdapter imageAdapter;
    private ItemAdapter volumeAdapter;
    private PanelAdapter panelAdapter;
    private WebPanelController panelController;
    private View header;
    private TextView engineState;
    private TextView engineDetail;
    private TextView statRunning;
    private TextView statStopped;
    private TextView statImages;
    private TextView statVolumes;
    private TextView offlineHint;
    private Button startEngine;
    private TextView emptyView;
    private TextView tabNote;
    private final TextView[] chips = new TextView[Tab.values().length];

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
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);
        ThothDock dock = ThothDock.getIfInitialized();
        api = new ApiClient(dock == null ? "" : dock.socketPath());
        panelController = new WebPanelController(this);

        header = LayoutInflater.from(this).inflate(R.layout.header_containers, null, false);
        engineState = header.findViewById(R.id.engine_state);
        engineDetail = header.findViewById(R.id.engine_detail);
        statRunning = header.findViewById(R.id.stat_running);
        statStopped = header.findViewById(R.id.stat_stopped);
        statImages = header.findViewById(R.id.stat_images);
        statVolumes = header.findViewById(R.id.stat_volumes);
        chips[Tab.CONTAINERS.ordinal()] = header.findViewById(R.id.chip_containers);
        chips[Tab.IMAGES.ordinal()] = header.findViewById(R.id.chip_images);
        chips[Tab.VOLUMES.ordinal()] = header.findViewById(R.id.chip_volumes);
        chips[Tab.PANEL.ordinal()] = header.findViewById(R.id.chip_panel);
        for (Tab t : Tab.values()) {
            chips[t.ordinal()].setOnClickListener(v -> selectTab(t));
        }
        offlineHint = header.findViewById(R.id.offline_hint);
        startEngine = header.findViewById(R.id.start_engine);
        startEngine.setOnClickListener(v -> {
            ThothDock d = ThothDock.getIfInitialized();
            if (d != null) d.ensureRunning();
            Toast.makeText(this, R.string.containers_starting_engine, Toast.LENGTH_SHORT).show();
        });
        emptyView = header.findViewById(R.id.containers_empty);
        tabNote = header.findViewById(R.id.tab_note);
        list = findViewById(R.id.containers_list);
        list.addHeaderView(header, null, false);
        adapter = new RowAdapter();
        imageAdapter = new ItemAdapter(imageItems);
        volumeAdapter = new ItemAdapter(volumeItems);
        panelAdapter = new PanelAdapter();

        Tab initial = Tab.of(getIntent().getStringExtra(ThothDock.EXTRA_TAB));
        if (savedInstanceState != null) {
            initial = Tab.of(savedInstanceState.getString(STATE_TAB));
            int[] pos = savedInstanceState.getIntArray(STATE_SCROLL_POS);
            int[] top = savedInstanceState.getIntArray(STATE_SCROLL_TOP);
            if (pos != null && pos.length == scrollPos.length) System.arraycopy(pos, 0, scrollPos, 0, pos.length);
            if (top != null && top.length == scrollTop.length) System.arraycopy(top, 0, scrollTop, 0, top.length);
        }
        showTab(initial);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String key = intent.getStringExtra(ThothDock.EXTRA_TAB);
        if (key != null) selectTab(Tab.of(key));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        rememberScroll();
        out.putString(STATE_TAB, tab.key);
        out.putIntArray(STATE_SCROLL_POS, scrollPos);
        out.putIntArray(STATE_SCROLL_TOP, scrollTop);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (tab == Tab.PANEL) panelController.show();
        ui.post(pollLoop);
    }

    @Override
    protected void onStop() {
        started = false;
        ui.removeCallbacks(pollLoop);
        panelController.hide();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        poller.shutdownNow();
        actions.shutdownNow();
        panelController.destroy();
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

    // ---- tabs ------------------------------------------------------------------

    private void selectTab(Tab t) {
        if (t == tab) return;
        rememberScroll();
        panelController.hide();
        showTab(t);
        if (t == Tab.PANEL && started) panelController.show();
    }

    private void rememberScroll() {
        View first = list.getChildAt(0);
        scrollPos[tab.ordinal()] = list.getFirstVisiblePosition();
        scrollTop[tab.ordinal()] = first == null ? 0 : first.getTop() - list.getPaddingTop();
    }

    /** Makes t the visible tab: highlight, title, adapter, header note and scroll position. */
    private void showTab(Tab t) {
        tab = t;
        for (Tab c : Tab.values()) {
            TextView chip = chips[c.ordinal()];
            boolean on = c == t;
            chip.setBackgroundResource(on ? R.drawable.thothdock_chip_selected : R.drawable.thothdock_chip);
            chip.setSelected(on);
        }
        ActionBar bar = getSupportActionBar();
        if (bar != null) bar.setTitle(titleFor(t));
        switch (t) {
            case IMAGES:
                list.setAdapter(imageAdapter);
                break;
            case VOLUMES:
                list.setAdapter(volumeAdapter);
                break;
            case PANEL:
                list.setAdapter(panelAdapter);
                break;
            default:
                list.setAdapter(adapter);
        }
        showContentNote();
        list.setSelectionFromTop(scrollPos[t.ordinal()], scrollTop[t.ordinal()]);
    }

    private int titleFor(Tab t) {
        switch (t) {
            case IMAGES:
                return R.string.images_title;
            case VOLUMES:
                return R.string.volumes_title;
            case PANEL:
                return com.thothterm.R.string.web_panel;
            default:
                return com.thothterm.R.string.containers;
        }
    }

    /** The summary line and empty text under the tabs, for the visible tab only. */
    private void showContentNote() {
        boolean online = last != null && last.online;
        tabNote.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        if (tab == Tab.PANEL || last == null || !online) return;
        switch (tab) {
            case IMAGES:
                tabNote.setText(last.imageList.summary
                        + (imageItems.isEmpty() ? "" : "\n" + getString(R.string.list_readonly_hint)));
                tabNote.setVisibility(View.VISIBLE);
                emptyView.setText(R.string.images_empty);
                emptyView.setVisibility(imageItems.isEmpty() ? View.VISIBLE : View.GONE);
                break;
            case VOLUMES:
                tabNote.setText(last.volumeList.summary
                        + (volumeItems.isEmpty() ? "" : "\n" + getString(R.string.list_readonly_hint)));
                tabNote.setVisibility(View.VISIBLE);
                emptyView.setText(R.string.volumes_empty);
                emptyView.setVisibility(volumeItems.isEmpty() ? View.VISIBLE : View.GONE);
                break;
            default:
                emptyView.setText(R.string.containers_empty);
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
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
            s.imageList = ListSources.images(images);
            JSONArray volumes = api.getObject("/volumes").optJSONArray("Volumes");
            s.volumes = volumes == null ? 0 : volumes.length();
            s.volumeList = ListSources.volumes(volumes);
            s.online = true;
        } catch (IOException e) {
            s.online = false;
            s.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            ThothLog.w(LogCategory.RUNTIME, "Containers: the engine did not answer: " + s.error);
        }
        return s;
    }

    private void render(Snapshot s) {
        last = s;
        if (s.online) {
            int running = 0;
            for (ContainerRow r : s.containers) if (r.isRunning()) running++;
            engineState.setText(R.string.containers_engine_running);
            engineState.setTextColor(getColor(R.color.thothdock_success));
            engineDetail.setText(getString(R.string.containers_engine_detail, s.version, s.api, s.arch));
            statRunning.setText(String.valueOf(running));
            statStopped.setText(String.valueOf(s.containers.size() - running));
            statImages.setText(String.valueOf(s.images));
            statVolumes.setText(s.volumes < 0 ? "–" : String.valueOf(s.volumes));
            offlineHint.setVisibility(View.GONE);
            startEngine.setVisibility(View.GONE);
            rows.clear();
            rows.addAll(s.containers);
            imageItems.clear();
            imageItems.addAll(s.imageList.items);
            volumeItems.clear();
            volumeItems.addAll(s.volumeList.items);
        } else {
            engineState.setText(R.string.containers_engine_offline);
            engineState.setTextColor(getColor(R.color.thothdock_error));
            engineDetail.setText("");
            statRunning.setText("–");
            statStopped.setText("–");
            statImages.setText("–");
            statVolumes.setText("–");
            offlineHint.setText(getString(R.string.containers_offline_hint)
                    + (s.error == null ? "" : "\n\n" + s.error));
            offlineHint.setVisibility(View.VISIBLE);
            startEngine.setVisibility(View.VISIBLE);
            rows.clear();
            imageItems.clear();
            volumeItems.clear();
        }
        // Only the visible tab is redrawn; the Web Panel tab has no engine data.
        showContentNote();
        switch (tab) {
            case CONTAINERS:
                adapter.notifyDataSetChanged();
                break;
            case IMAGES:
                imageAdapter.notifyDataSetChanged();
                break;
            case VOLUMES:
                volumeAdapter.notifyDataSetChanged();
                break;
            default:
        }
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

    /** Rows of the read-only Images and Volumes tabs. */
    private final class ItemAdapter extends BaseAdapter {
        private final List<ListSources.Item> items;

        ItemAdapter(List<ListSources.Item> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(ContainersActivity.this).inflate(R.layout.item_simple, parent, false);
            ListSources.Item it = items.get(position);
            ((TextView) v.findViewById(R.id.simple_title)).setText(it.title);
            ((TextView) v.findViewById(R.id.simple_badge)).setText(it.badge);
            ((TextView) v.findViewById(R.id.simple_sub)).setText(it.sub);
            ((TextView) v.findViewById(R.id.simple_meta)).setText(it.meta);
            return v;
        }
    }

    /** The Web Panel tab: one row whose view the controller binds. */
    private final class PanelAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public Object getItem(int position) {
            return "panel";
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(ContainersActivity.this).inflate(R.layout.panel_content, parent, false);
            panelController.bind(v);
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
