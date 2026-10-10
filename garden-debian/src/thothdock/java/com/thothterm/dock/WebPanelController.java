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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

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
 * The Web Panel tab of the Containers screen: whether the panel runs, who can
 * open it (this phone or the Wi-Fi network), and what a browser needs (the
 * https:// address, the pairing code and the certificate fingerprint).
 *
 * <p>The screen's list shows the tab's view as one row and may recycle it, so
 * the controller keeps all state in fields and redraws whatever view is bound.
 * It never polls: it redraws when {@link WebPanel} reports a change and when a
 * code expires (one timer set to that moment). It listens only while the tab
 * is visible, and it never starts or stops the panel by itself: that is the
 * Start/Stop button's job, so leaving the tab leaves the server alone.</p>
 */
final class WebPanelController implements WebPanel.Listener {
    private final Activity activity;
    private final WebPanel panel = WebPanel.get();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable expiry = this::render;
    private ExecutorService worker;

    private boolean visible;
    private boolean busy;
    private boolean starting;
    private boolean rendering;
    private String problem;

    private View root;
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

    WebPanelController(Activity activity) {
        this.activity = activity;
    }

    /** Binds the controller to the view the list is showing (a recycled one included). */
    void bind(View v) {
        if (v == root) {
            render();
            return;
        }
        root = v;
        dot = v.findViewById(R.id.panel_dot);
        state = v.findViewById(R.id.panel_state);
        stateDetail = v.findViewById(R.id.panel_state_detail);
        problemView = v.findViewById(R.id.panel_problem);
        toggle = v.findViewById(R.id.panel_toggle);
        modeGroup = v.findViewById(R.id.panel_mode);
        modeLocal = v.findViewById(R.id.mode_local);
        modeWifi = v.findViewById(R.id.mode_wifi);
        modeWifiDetail = v.findViewById(R.id.mode_wifi_detail);
        wifiWarning = v.findViewById(R.id.panel_wifi_warning);
        connect = v.findViewById(R.id.panel_connect);
        url = v.findViewById(R.id.panel_url);
        code = v.findViewById(R.id.panel_code);
        codeState = v.findViewById(R.id.panel_code_state);
        fingerprint = v.findViewById(R.id.panel_fingerprint);
        newCode = v.findViewById(R.id.panel_new_code);
        ((TextView) v.findViewById(R.id.panel_help)).setText(R.string.panel_help);

        rendering = true;
        modeGroup.check("wifi".equals(panel.savedMode()) ? R.id.mode_wifi : R.id.mode_local);
        rendering = false;
        modeGroup.setOnCheckedChangeListener((group, id) -> {
            if (rendering) return;
            panel.saveMode(id == R.id.mode_wifi ? WebPanel.MODE_WIFI : WebPanel.MODE_LOCAL);
            render();
        });
        toggle.setOnClickListener(x -> {
            if (panel.isRunning()) stopPanel();
            else startPanel();
        });
        newCode.setOnClickListener(x -> regenerateCode());
        v.findViewById(R.id.panel_copy_url).setOnClickListener(x ->
                copy(R.string.panel_address_title, url.getText(), R.string.panel_url_copied));
        v.findViewById(R.id.panel_copy_fingerprint).setOnClickListener(x -> {
            WebPanel.Info info = panel.info();
            if (info != null) copy(R.string.panel_fingerprint_title, info.fingerprint, R.string.panel_fingerprint_copied);
        });
        v.findViewById(R.id.panel_open).setOnClickListener(x -> openInBrowser());
        render();
    }

    /** The tab became visible: listen for changes and redraw. */
    void show() {
        visible = true;
        panel.addListener(this);
        render();
    }

    /** The tab was left or the screen stopped: stop listening and cancel the expiry timer. */
    void hide() {
        visible = false;
        panel.removeListener(this);
        ui.removeCallbacks(expiry);
    }

    void destroy() {
        hide();
        root = null;
        if (worker != null) worker.shutdownNow();
    }

    @Override
    public void onWebPanelChanged() {
        ui.post(this::render);
    }

    // ---- actions ---------------------------------------------------------------

    private String chosenHost() {
        if (modeWifi.isChecked()) return WebPanel.lanAddress(activity);
        return "127.0.0.1";
    }

    private void startPanel() {
        final String host = chosenHost();
        if (host == null) {
            Toast.makeText(activity, R.string.panel_lan_unavailable, Toast.LENGTH_LONG).show();
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
        if (worker == null) worker = Executors.newSingleThreadExecutor();
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
                    problem = shown == null ? null : activity.getString(R.string.panel_failed, shown);
                    starting = false;
                    busy = false;
                    render();
                });
            });
        } catch (RuntimeException rejected) {
            // The screen is closing.
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
        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        if (clipboard == null || text == null || text.length() == 0) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(labelRes), text));
        Toast.makeText(activity, doneRes, Toast.LENGTH_SHORT).show();
    }

    private void openInBrowser() {
        CharSequence address = url.getText();
        if (address == null || !address.toString().startsWith("https://")) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address.toString())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.panel_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    // ---- rendering -------------------------------------------------------------

    private void render() {
        if (root == null || activity.isFinishing() || activity.isDestroyed()) return;
        boolean running = panel.isRunning();
        String lan = WebPanel.lanAddress(activity);

        rendering = true;
        modeGroup.setEnabled(!running && !busy);
        modeLocal.setEnabled(!running && !busy);
        modeWifi.setEnabled(!running && !busy && lan != null);
        if (lan == null && modeWifi.isChecked() && !running) modeLocal.setChecked(true);
        rendering = false;
        modeWifiDetail.setText(lan == null ? activity.getString(R.string.panel_lan_unavailable)
                : activity.getString(R.string.panel_lan_detail, lan));
        wifiWarning.setVisibility(modeWifi.isChecked() ? View.VISIBLE : View.GONE);

        int dotColor = running ? R.color.thothdock_state_running : R.color.thothdock_state_stopped;
        dot.getBackground().setTint(activity.getColor(dotColor));
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
                codeState.setText(activity.getString(R.string.panel_code_state, time));
                code.setAlpha(1f);
                if (visible) ui.postDelayed(expiry, wait + 250);
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
