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
 * Launcher screen for the Ubuntu edition. On first run it extracts the
 * embedded rootfs locally; on later runs it continues straight to the
 * terminal with no preparation screen.
 */
public class UbuntuSetupActivity extends AppCompatActivity
        implements RootfsManager.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView status;
    private ProgressBar progress;
    private View actions;

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
        progress = findViewById(R.id.setup_progress);
        actions = findViewById(R.id.setup_actions);

        Button retry = findViewById(R.id.setup_retry);
        retry.setOnClickListener(view -> {
            actions.setVisibility(View.GONE);
            progress.setIndeterminate(true);
            status.setText(R.string.ubuntu_preparing);
            manager.start();
        });

        Button logs = findViewById(R.id.setup_logs);
        logs.setOnClickListener(view ->
                startActivity(new Intent(this, LogsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        RootfsManager manager = RootfsManager.get();
        manager.setListener(this);
        manager.start();
    }

    @Override
    protected void onPause() {
        RootfsManager.get().setListener(null);
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
            if (progress != null) {
                progress.setIndeterminate(false);
                progress.setProgress(0);
            }
            if (actions != null) actions.setVisibility(View.VISIBLE);
        });
    }
}
