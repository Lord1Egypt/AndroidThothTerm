/*
 * Copyright (C) 2019-2025 Roumen Petrov.  All rights reserved.
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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.remote.CommandCollector;
import com.thothterm.services.ServiceManager;
import com.thothterm.utils.ThemeManager;

import jackpal.androidterm.TermService;


public class RemoteActionActivity extends AppCompatActivity {
    private final ServiceManager service_manager = new ServiceManager();

    private TermService term_service = null;
    private boolean command_collected = false;


    private void onServiceConnection(TermService service) {
        if (service != null) {
            ThothLog.d(LogCategory.SESSION, "Remote action connected to terminal service");
            term_service = service;
            processIntent();
        } else {
            ThothLog.d(LogCategory.SESSION, "Remote action disconnected from terminal service");
            term_service = null;
        }
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(ThemeManager.presetTheme(this, false, resid));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* intent is required */
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_remote_action);
        {
            View view = findViewById(R.id.progress);
            view.setAlpha(1.0f);
        }
        {
            TextView msg = findViewById(R.id.progress_message);
            msg.setText(R.string.app_collection_progress);
        }

        CommandCollector.collect(this, () -> {
            command_collected = true;
            processIntent();
        });

        service_manager.onCreate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        service_manager.setOnServiceConnectionListener(RemoteActionActivity.this::onServiceConnection);
        service_manager.onStart(this);
    }

    @Override
    protected void onStop() {
        service_manager.onStop(this);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (term_service != null) {
            if (term_service.getSessionCount() == 0)
                service_manager.onDestroy(this);
            term_service = null;
        }
        super.onDestroy();
    }

    protected TermService getTermService() {
        return term_service;
    }

    protected void processAction(@NonNull Intent intent, @NonNull String action) {
        //nop, override at child level
    }

    private void processIntent() {
        /* process intent after path collection and start of service */
        if (term_service == null) return;
        if (!command_collected) return;

        /* intent is required - see onCreate() */
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action != null)
            processAction(intent, action);

        finish();
    }
}
