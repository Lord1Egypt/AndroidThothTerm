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
import android.widget.Toast;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.thothterm.AppCompatActivity;
import com.thothterm.LogsActivity;
import com.thothterm.R;
import com.thothterm.TermActivity;
import com.thothterm.linux.RootfsLifecycle.Condition;

/**
 * Launcher screen for the Ubuntu edition. On first run it prepares the rootfs;
 * on later runs it continues straight to the terminal with no preparation
 * screen.
 *
 * <p>A build that embeds the userland prepares it immediately. A build that
 * does not — the F-Droid flavour — first shows what would be downloaded and
 * waits: {@link RootfsManager#start()} is not called until the user accepts, so
 * nothing executable is fetched without a deliberate choice.
 *
 * <p>An installed environment that is damaged is never reinstalled from here
 * on its own: the screen explains it, and a reinstall (which keeps /home)
 * happens only after the user confirms it in a dialog.</p>
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
    private View attentionActions;
    private Button resetButton;
    private Button resetCancel;
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

        attentionActions = findViewById(R.id.setup_attention_actions);
        resetButton = findViewById(R.id.setup_reset);
        resetCancel = findViewById(R.id.setup_reset_cancel);
        resetButton.setOnClickListener(view -> onResetPressed(manager));
        resetCancel.setOnClickListener(view -> {
            try {
                manager.cancelReset();
            } catch (java.io.IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            attentionActions.setVisibility(View.GONE);
            showPreparing();
            manager.start();
        });
        findViewById(R.id.setup_attention_logs).setOnClickListener(view ->
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
            // The long consent text goes too: left in place it pushes the
            // actions below the fold. Continue setup shows it again.
            consent.setVisibility(View.GONE);
            consentActions.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            status.setText(R.string.ubuntu_consent_declined);
            hint.setText("");
            actions.setVisibility(View.VISIBLE);
        });

        if (manager.needsImageDownload()) askForConsent(manager);
    }

    private void showPreparing() {
        if (status != null) status.setText(R.string.ubuntu_preparing);
        if (hint != null) hint.setText(R.string.ubuntu_prepare_once);
        if (progress != null) {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
        }
    }

    /**
     * Reinstalling the system is the one destructive action, so it always goes
     * through this dialog; the default is to do nothing.
     */
    private void onResetPressed(RootfsManager manager) {
        if (manager.condition() == Condition.EXPLICIT_RESET_REQUESTED) {
            confirmedReset(manager);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.ubuntu_reset_confirm_title)
                .setMessage(R.string.ubuntu_reset_confirm_body)
                .setNegativeButton(R.string.ubuntu_reset_confirm_no, null)
                .setPositiveButton(R.string.ubuntu_reset_confirm_yes,
                        (dialog, which) -> confirmedReset(manager))
                .show();
    }

    private void confirmedReset(RootfsManager manager) {
        try {
            manager.requestReset();
        } catch (java.io.IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        attentionActions.setVisibility(View.GONE);
        if (manager.needsImageDownload()) {
            askForConsent(manager);
            return;
        }
        showPreparing();
        manager.start();
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
            // A named stage after extraction has no percentage of its own.
            if (progress != null && progress.getProgress() >= 100) {
                progress.setIndeterminate(true);
            }
        });
    }

    @Override
    public void onDownloadProgress(int percent) {
        main.post(() -> {
            if (progress == null) return;
            progress.setIndeterminate(false);
            progress.setProgress(percent);
            if (status != null) {
                status.setText(getString(R.string.ubuntu_downloading_percent, percent));
            }
        });
    }

    @Override
    public void onAttentionNeeded(Condition condition) {
        main.post(() -> {
            hideConsent();
            if (progress != null) progress.setVisibility(View.GONE);
            if (actions != null) actions.setVisibility(View.GONE);
            boolean pendingReset = condition == Condition.EXPLICIT_RESET_REQUESTED;
            if (status != null) {
                status.setText(pendingReset ? R.string.ubuntu_reset_pending_title
                        : R.string.ubuntu_damaged_title);
            }
            if (hint != null) {
                hint.setText(pendingReset ? R.string.ubuntu_reset_pending_body
                        : R.string.ubuntu_damaged_body);
            }
            if (resetButton != null) {
                resetButton.setText(pendingReset ? R.string.ubuntu_reset_continue
                        : R.string.ubuntu_reset_action);
            }
            if (resetCancel != null) resetCancel.setVisibility(pendingReset ? View.VISIBLE : View.GONE);
            if (attentionActions != null) attentionActions.setVisibility(View.VISIBLE);
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
