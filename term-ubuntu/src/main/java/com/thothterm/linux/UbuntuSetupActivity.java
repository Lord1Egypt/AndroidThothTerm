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

package com.thothterm.linux;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.thothterm.AppCompatActivity;
import com.thothterm.LogsActivity;
import com.thothterm.R;
import com.thothterm.TermActivity;

/**
 * Launcher screen for the Ubuntu edition. On first run it prepares the rootfs;
 * on later runs it continues straight to the terminal with no preparation
 * screen.
 *
 * <p>A build that embeds the userland prepares it immediately. A build that
 * does not — the F-Droid flavour — first shows what would be downloaded and
 * waits: {@link RootfsManager#start()} is not called until the user accepts, so
 * nothing executable is fetched without a deliberate choice.
 */
public class UbuntuSetupActivity extends AppCompatActivity
        implements RootfsManager.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView status;
    private TextView hint;
    private TextView consent;
    private ProgressBar progress;
    private View actions;
    private View consentActions;
    /** True while waiting for an answer; blocks the automatic start in onResume. */
    private boolean awaitingConsent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RootfsManager.init(getApplicationContext());
        RootfsManager manager = RootfsManager.get();

        if (manager.isReady()) {
            continueToTerminal();
            return;
        }

        setContentView(R.layout.activity_ubuntu_setup);

        status = findViewById(R.id.setup_status);
        hint = findViewById(R.id.setup_hint);
        progress = findViewById(R.id.setup_progress);
        actions = findViewById(R.id.setup_actions);

        Button retry = findViewById(R.id.setup_retry);
        retry.setOnClickListener(view -> {
            actions.setVisibility(View.GONE);
            if (manager.needsImageDownload()) {
                askForConsent(manager);
                return;
            }
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            status.setText(R.string.ubuntu_preparing);
            hint.setText(R.string.ubuntu_prepare_once);
            manager.start();
        });

        Button logs = findViewById(R.id.setup_logs);
        logs.setOnClickListener(view ->
                startActivity(new Intent(this, LogsActivity.class)));

        consent = findViewById(R.id.setup_consent);
        consentActions = findViewById(R.id.setup_consent_actions);
        Button accept = findViewById(R.id.setup_consent_accept);
        Button decline = findViewById(R.id.setup_consent_decline);

        accept.setOnClickListener(view -> {
            awaitingConsent = false;
            hideConsent();
            status.setText(R.string.ubuntu_preparing);
            hint.setText(R.string.ubuntu_prepare_once);
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            RootfsManager.get().start();
        });

        decline.setOnClickListener(view -> {
            // Stay on this screen with the download still one tap away; the
            // app must not look broken because the answer was no.
            consentActions.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            status.setText(R.string.ubuntu_consent_declined);
            hint.setText("");
            actions.setVisibility(View.VISIBLE);
        });

        if (manager.needsImageDownload()) askForConsent(manager);
    }

    private void askForConsent(RootfsManager manager) {
        awaitingConsent = true;
        status.setText(R.string.ubuntu_consent_title);
        hint.setText("");
        progress.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        consent.setText(getString(R.string.ubuntu_consent_body,
                manager.imageVersion(),
                manager.downloadSizeMb(),
                manager.imageSourceUrl()));
        consent.setVisibility(View.VISIBLE);
        consentActions.setVisibility(View.VISIBLE);
    }

    private void hideConsent() {
        if (consent != null) consent.setVisibility(View.GONE);
        if (consentActions != null) consentActions.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RootfsManager manager = RootfsManager.get();
        manager.setListener(this);
        if (awaitingConsent || manager.needsImageDownload()) return;
        manager.start();
    }

    @Override
    protected void onPause() {
        RootfsManager.get().clearListener(this);
        super.onPause();
    }

    private void continueToTerminal() {
        startActivity(new Intent(this, TermActivity.class));
        finish();
    }

    @Override
    public void onStatus(String message) {
        main.post(() -> {
            if (status != null) status.setText(message);
            if (hint != null) hint.setText(R.string.ubuntu_prepare_once);
        });
    }

    @Override
    public void onProgress(int percent, long entries) {
        main.post(() -> {
            if (progress == null) return;
            progress.setIndeterminate(false);
            progress.setProgress(percent);
            if (status != null) {
                status.setText(getString(R.string.ubuntu_extracting_percent, percent));
            }
        });
    }

    @Override
    public void onComplete() {
        main.post(this::continueToTerminal);
    }

    @Override
    public void onError(String message, Throwable cause) {
        main.post(() -> {
            if (status != null) status.setText(R.string.ubuntu_prepare_failed);
            if (hint != null) hint.setText(R.string.ubuntu_prepare_failed_hint);
            if (progress != null) {
                progress.setIndeterminate(false);
                progress.setProgress(0);
            }
            hideConsent();
            if (actions != null) actions.setVisibility(View.VISIBLE);
        });
    }
}
