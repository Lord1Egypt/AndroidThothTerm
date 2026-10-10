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

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.AppCompatActivity;
import com.thothterm.debian.R;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Web Panel page, opened from the drawer like LAN Mode: whether the panel
 * runs, who can open it (this phone or the Wi-Fi network), and what a browser
 * needs (the https:// address, the pairing code and the certificate
 * fingerprint). It never polls: the page redraws when {@link WebPanel} reports
 * a change and when a code expires (one timer set to that moment).
 */
public class WebPanelActivity extends AppCompatActivity implements WebPanel.Listener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable expiry = this::render;

    private WebPanel panel;
    private boolean busy;
    private boolean starting;
    private String problem;
    private boolean rendering;

    private View dot;
    private TextView state;
    private TextView stateDetail;
    private TextView problemView;
    private Button toggle;
    private RadioGroup modeGroup;
    private RadioButton modeLocal;
    private RadioButton modeWifi;
    private TextView modeWifiDetail;
    private TextView wifiWarning;
    private View connect;
    private TextView url;
    private TextView code;
    private TextView codeState;
    private TextView fingerprint;
    private Button newCode;
    private TextView help;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_panel);
        Toolbar toolbar = findViewById(com.thothterm.R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(com.thothterm.R.string.web_panel);
        }
        panel = WebPanel.get();
        dot = findViewById(R.id.panel_dot);
        state = findViewById(R.id.panel_state);
        stateDetail = findViewById(R.id.panel_state_detail);
        problemView = findViewById(R.id.panel_problem);
        toggle = findViewById(R.id.panel_toggle);
        modeGroup = findViewById(R.id.panel_mode);
        modeLocal = findViewById(R.id.mode_local);
        modeWifi = findViewById(R.id.mode_wifi);
        modeWifiDetail = findViewById(R.id.mode_wifi_detail);
        wifiWarning = findViewById(R.id.panel_wifi_warning);
        connect = findViewById(R.id.panel_connect);
        url = findViewById(R.id.panel_url);
        code = findViewById(R.id.panel_code);
        codeState = findViewById(R.id.panel_code_state);
        fingerprint = findViewById(R.id.panel_fingerprint);
        newCode = findViewById(R.id.panel_new_code);
        help = findViewById(R.id.panel_help);
        help.setText(R.string.panel_help);

        modeGroup.check("wifi".equals(panel.savedMode()) ? R.id.mode_wifi : R.id.mode_local);
        modeGroup.setOnCheckedChangeListener((group, id) -> {
            if (rendering) return;
            panel.saveMode(id == R.id.mode_wifi ? WebPanel.MODE_WIFI : WebPanel.MODE_LOCAL);
            render();
        });
        toggle.setOnClickListener(v -> {
            if (panel.isRunning()) stopPanel();
            else startPanel();
        });
        newCode.setOnClickListener(v -> regenerateCode());
        findViewById(R.id.panel_copy_url).setOnClickListener(v -> copy(R.string.panel_address_title, url.getText(), R.string.panel_url_copied));
        findViewById(R.id.panel_copy_fingerprint).setOnClickListener(v -> {
            WebPanel.Info info = panel.info();
            if (info != null) copy(R.string.panel_fingerprint_title, info.fingerprint, R.string.panel_fingerprint_copied);
        });
        findViewById(R.id.panel_open).setOnClickListener(v -> openInBrowser());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        panel.addListener(this);
        render();
    }

    @Override
    protected void onPause() {
        panel.removeListener(this);
        ui.removeCallbacks(expiry);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onWebPanelChanged() {
        ui.post(this::render);
    }

    // ---- actions ---------------------------------------------------------------

    private String chosenHost() {
        if (modeWifi.isChecked()) return WebPanel.lanAddress(this);
        return "127.0.0.1";
    }

    private void startPanel() {
        final String host = chosenHost();
        if (host == null) {
            Toast.makeText(this, R.string.panel_lan_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        problem = null;
        starting = true;
        busy = true;
        render();
        run(() -> {
            panel.start(host);
            waitForInfo();
        });
    }

    private void stopPanel() {
        problem = null;
        busy = true;
        render();
        run(panel::stop);
    }

    private void regenerateCode() {
        busy = true;
        render();
        run(() -> {
            panel.newCode();
            waitForInfo();
        });
    }

    private interface Job {
        void run() throws IOException;
    }

    private void run(Job job) {
        try {
            worker.execute(() -> {
                String failure = null;
                try {
                    job.run();
                } catch (IOException e) {
                    failure = e.getMessage();
                    ThothLog.w(LogCategory.RUNTIME, "Web Panel action failed: " + failure);
                }
                final String shown = failure;
                ui.post(() -> {
                    problem = shown == null ? null : getString(R.string.panel_failed, shown);
                    starting = false;
                    busy = false;
                    render();
                });
            });
        } catch (RuntimeException rejected) {
            // The page is closing.
        }
    }

    /** The panel writes pairing.json once its certificate and code exist (a few hundred ms). */
    private void waitForInfo() throws IOException {
        for (int i = 0; i < 50; i++) {
            if (panel.info() != null) return;
            if (!panel.isRunning()) throw new IOException("it exited; see thothdock/panel.log");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        }
        throw new IOException("no answer in 5 seconds");
    }

    private void copy(int labelRes, CharSequence text, int doneRes) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null || text == null || text.length() == 0) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(labelRes), text));
        Toast.makeText(this, doneRes, Toast.LENGTH_SHORT).show();
    }

    private void openInBrowser() {
        CharSequence address = url.getText();
        if (address == null || !address.toString().startsWith("https://")) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address.toString())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.panel_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    // ---- rendering -------------------------------------------------------------

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        boolean running = panel.isRunning();
        String lan = WebPanel.lanAddress(this);

        rendering = true;
        modeGroup.setEnabled(!running && !busy);
        modeLocal.setEnabled(!running && !busy);
        modeWifi.setEnabled(!running && !busy && lan != null);
        if (lan == null && modeWifi.isChecked() && !running) modeLocal.setChecked(true);
        rendering = false;
        modeWifiDetail.setText(lan == null ? getString(R.string.panel_lan_unavailable)
                : getString(R.string.panel_lan_detail, lan));
        wifiWarning.setVisibility(modeWifi.isChecked() ? View.VISIBLE : View.GONE);

        int dotColor = running ? R.color.thothdock_state_running : R.color.thothdock_state_stopped;
        dot.getBackground().setTint(getColor(dotColor));
        String listening = panel.listenHost();
        if (starting) {
            state.setText(R.string.panel_state_starting);
            stateDetail.setText("");
        } else if (running) {
            state.setText(R.string.panel_state_on);
            stateDetail.setText("127.0.0.1".equals(listening) ? R.string.panel_detail_local : R.string.panel_detail_wifi);
        } else {
            state.setText(R.string.panel_state_off);
            stateDetail.setText(R.string.panel_detail_off);
        }
        problemView.setText(problem);
        problemView.setVisibility(problem == null ? View.GONE : View.VISIBLE);
        toggle.setText(running ? R.string.panel_stop : R.string.panel_start);
        toggle.setEnabled(!busy);

        ui.removeCallbacks(expiry);
        WebPanel.Info info = running ? panel.info() : null;
        connect.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            url.setText(info != null && !info.urls.isEmpty() ? httpsOnly(info.urls.get(0))
                    : WebPanel.httpsUrl(listening == null ? "127.0.0.1" : listening));
            newCode.setEnabled(!busy);
            if (info == null) {
                code.setText("— — — —");
                codeState.setText(R.string.panel_code_wait);
                fingerprint.setText("");
            } else if (info.code.isEmpty()) {
                code.setText("— — — —");
                code.setAlpha(0.4f);
                codeState.setText(R.string.panel_code_used);
                fingerprint.setText(lines(info.fingerprint));
            } else {
                code.setText(group(info.code));
                fingerprint.setText(lines(info.fingerprint));
                showExpiry(info.expires);
            }
        }
    }

    /** Whatever the panel reports, a browser address here always has the https:// scheme. */
    private static String httpsOnly(String address) {
        return address.startsWith("https://") ? address : "https://" + address.replaceFirst("^[a-zA-Z]+://", "");
    }

    private void showExpiry(String iso) {
        try {
            Instant at = Instant.parse(iso);
            long wait = at.toEpochMilli() - System.currentTimeMillis();
            if (wait <= 0) {
                codeState.setText(R.string.panel_code_expired);
                code.setAlpha(0.4f);
            } else {
                String time = DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(ZoneId.systemDefault()));
                codeState.setText(getString(R.string.panel_code_state, time));
                code.setAlpha(1f);
                ui.postDelayed(expiry, wait + 250);
            }
        } catch (RuntimeException e) {
            codeState.setText("");
        }
    }

    private static String group(String digits) {
        return digits.length() == 8 ? digits.substring(0, 4) + " " + digits.substring(4) : digits;
    }

    /** The 95-character fingerprint in four lines of eight byte pairs. */
    private static String lines(String fingerprint) {
        StringBuilder out = new StringBuilder();
        String[] pairs = fingerprint.split(":");
        for (int i = 0; i < pairs.length; i++) {
            out.append(pairs[i]);
            if (i == pairs.length - 1) break;
            out.append((i + 1) % 8 == 0 ? "\n" : ":");
        }
        return out.toString();
    }
}
