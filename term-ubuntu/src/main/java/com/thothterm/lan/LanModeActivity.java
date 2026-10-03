/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm.lan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.thothterm.AppCompatActivity;
import com.thothterm.R;
import com.thothterm.widget.ScreenMessage;

import java.util.Locale;

/**
 * The LAN Mode screen, opened from the drawer: the switch, the address to
 * open, the pairing PIN, and what is connected.
 */
public class LanModeActivity extends AppCompatActivity implements LanController.Listener {
    private static final long REFRESH_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private LanController lan;
    private SwitchCompat toggle;
    private boolean rendering;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lan_mode);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        lan = LanController.get();
        toggle = findViewById(R.id.lan_switch);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) return;
            if (checked) lan.start();
            else lan.stop();
            render();
        });
        Button stop = findViewById(R.id.lan_stop);
        stop.setOnClickListener(v -> lan.stop());
        Button newPin = findViewById(R.id.lan_new_pin);
        newPin.setOnClickListener(v -> lan.newPin());
        Button copy = findViewById(R.id.lan_copy);
        copy.setOnClickListener(v -> copyAddress());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        lan.addListener(this);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        lan.removeListener(this);
        super.onPause();
    }

    @Override
    public void onLanChanged() {
        render();
    }

    private void copyAddress() {
        LanController.State state = lan.state();
        if (state.url == null) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.lan_address_label), state.url));
        ScreenMessage.show(getApplicationContext(), R.string.lan_address_copied);
    }

    private void render() {
        LanController.State state = lan.state();
        rendering = true;
        toggle.setChecked(state.on);
        toggle.setEnabled(state.on || lan.canStart());
        rendering = false;

        TextView status = findViewById(R.id.lan_status);
        status.setText(getString(R.string.lan_status_label) + ": "
                + getString(state.on ? R.string.lan_state_active : R.string.lan_state_off));

        TextView problem = findViewById(R.id.lan_problem);
        int problemText = problemText(state.problem);
        problem.setVisibility(problemText == 0 ? View.GONE : View.VISIBLE);
        if (problemText != 0) problem.setText(problemText);

        findViewById(R.id.lan_active).setVisibility(state.on ? View.VISIBLE : View.GONE);
        TextView help = findViewById(R.id.lan_help);
        help.setText(state.on ? R.string.lan_help_on : R.string.lan_help_off);
        if (!state.on) return;

        ((TextView) findViewById(R.id.lan_address)).setText(state.url);
        TextView pin = findViewById(R.id.lan_pin);
        TextView pinState = findViewById(R.id.lan_pin_state);
        if (state.pinState == LanController.PinState.ACTIVE && state.pin != null) {
            pin.setVisibility(View.VISIBLE);
            pin.setText(state.pin.substring(0, 3) + " " + state.pin.substring(3));
            long left = Math.max(0, state.pinExpiresAtMs - System.currentTimeMillis()) / 1000;
            pinState.setText(getString(R.string.lan_pin_expires,
                    String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60)));
        } else {
            pin.setVisibility(View.GONE);
            pinState.setText(pinStateText(state.pinState));
        }
        ((TextView) findViewById(R.id.lan_browsers)).setText(
                getString(R.string.lan_browsers, state.pairedBrowsers));
        ((TextView) findViewById(R.id.lan_terminals)).setText(
                getString(R.string.lan_terminals, state.browserTerminals, state.connectedTerminals));
    }

    private static int pinStateText(LanController.PinState state) {
        switch (state) {
            case USED: return R.string.lan_pin_used;
            case LOCKED: return R.string.lan_pin_locked;
            default: return R.string.lan_pin_expired;
        }
    }

    private static int problemText(LanController.Problem problem) {
        switch (problem) {
            case NO_NETWORK: return R.string.lan_problem_no_network;
            case PORT_IN_USE: return R.string.lan_problem_port_in_use;
            case NETWORK_LOST: return R.string.lan_problem_network_lost;
            case START_FAILED: return R.string.lan_problem_start_failed;
            default: return 0;
        }
    }
}
