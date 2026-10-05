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
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.AppCompatActivity;
import com.thothterm.debian.R;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Live logs of one container, read from the ThothDock API (the same stream
 * `docker logs -f` reads). Memory is bounded: only the last {@link #MAX_LINES}
 * lines are kept, and the stream is closed when the screen is no longer
 * visible, so nothing keeps reading in the background.
 */
public class ContainerLogsActivity extends AppCompatActivity {
    static final String EXTRA_ID = "id";
    static final String EXTRA_NAME = "name";
    private static final int MAX_LINES = 1500;
    private static final int MAX_LINE_CHARS = 4000;
    private static final int INITIAL_TAIL = 200;
    private static final long REDRAW_MS = 250;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Deque<String> lines = new ArrayDeque<>();
    private final StringBuilder partial = new StringBuilder();
    private final Object lock = new Object();

    private TextView text;
    private ScrollView scroll;
    private String id;
    private volatile ApiClient.LogStream stream;
    private volatile Thread reader;
    private boolean dirty;
    private boolean redrawScheduled;

    private final Runnable redraw = new Runnable() {
        @Override
        public void run() {
            String snapshot;
            synchronized (lock) {
                redrawScheduled = false;
                if (!dirty) return;
                dirty = false;
                StringBuilder sb = new StringBuilder();
                for (String l : lines) sb.append(l).append('\n');
                sb.append(partial);
                snapshot = sb.toString();
            }
            boolean atBottom = scroll.getChildAt(0) == null
                    || scroll.getScrollY() + scroll.getHeight() >= scroll.getChildAt(0).getHeight() - 48;
            text.setText(snapshot);
            if (atBottom) scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container_logs);
        Toolbar toolbar = findViewById(com.thothterm.R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        id = getIntent().getStringExtra(EXTRA_ID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(getString(R.string.containers_logs_title, name == null ? "" : name));
        }
        text = findViewById(R.id.logs_text);
        scroll = findViewById(R.id.logs_scroll);
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            text.setText(R.string.containers_logs_invalid);
            id = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (id != null) startReading();
    }

    @Override
    protected void onStop() {
        stopReading();
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startReading() {
        synchronized (lock) {
            lines.clear();
            partial.setLength(0);
            dirty = true;
        }
        final String containerId = id;
        Thread t = new Thread(() -> {
            ApiClient api = new ApiClient(ThothDock.get().socketPath());
            try {
                boolean tty = false;
                try {
                    JSONObject info = api.getObject("/containers/" + containerId + "/json");
                    tty = info.optJSONObject("Config") != null && info.getJSONObject("Config").optBoolean("Tty");
                } catch (Exception ignored) {
                    // a failure here surfaces when the stream opens
                }
                ApiClient.LogStream s = api.openLogs(containerId, tty, INITIAL_TAIL);
                stream = s;
                byte[] chunk;
                while (!Thread.currentThread().isInterrupted() && (chunk = s.next()) != null) {
                    append(new String(chunk, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    append("\n[" + e.getMessage() + "]\n");
                }
            } finally {
                ApiClient.LogStream s = stream;
                if (s != null) s.close();
            }
        }, "ThothDock-logs");
        t.setDaemon(true);
        reader = t;
        t.start();
        ui.post(redraw);
    }

    private void stopReading() {
        Thread t = reader;
        reader = null;
        if (t != null) t.interrupt();
        ApiClient.LogStream s = stream;
        stream = null;
        if (s != null) s.close(); // unblocks a read in progress
    }

    private void append(String chunk) {
        synchronized (lock) {
            int start = 0;
            for (int i = 0; i < chunk.length(); i++) {
                if (chunk.charAt(i) == '\n') {
                    partial.append(chunk, start, i);
                    pushLine(partial.toString());
                    partial.setLength(0);
                    start = i + 1;
                }
            }
            partial.append(chunk, start, chunk.length());
            while (partial.length() > MAX_LINE_CHARS) {
                pushLine(partial.substring(0, MAX_LINE_CHARS));
                partial.delete(0, MAX_LINE_CHARS);
            }
            dirty = true;
            if (!redrawScheduled) {
                redrawScheduled = true;
                ui.postDelayed(redraw, REDRAW_MS);
            }
        }
    }

    private void pushLine(String line) {
        lines.addLast(line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS) : line);
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }
}
