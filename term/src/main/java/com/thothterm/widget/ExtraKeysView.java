/*
 * Copyright (C) 2026 ThothTerm contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.thothterm.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.thothterm.R;

import jackpal.androidterm.emulatorview.EmulatorView;

/** Compact, focus-preserving terminal keys displayed above the soft keyboard. */
public class ExtraKeysView extends LinearLayout
        implements EmulatorView.OnExtraModifierStateChangedListener {
    public interface TerminalProvider {
        EmulatorView getTerminalView();
    }

    private static final int KEY_CTRL = -1001;
    private static final int KEY_ALT = -1002;

    private TerminalProvider terminalProvider;
    private LinearLayout primaryPage;
    private LinearLayout symbolsPage;
    private AppCompatButton symbolsButton;
    private AppCompatButton controlButton;
    private AppCompatButton altButton;
    private int appliedRowHeight = -1;
    private int controlState = EmulatorView.EXTRA_MODIFIER_INACTIVE;
    private int altState = EmulatorView.EXTRA_MODIFIER_INACTIVE;

    public ExtraKeysView(Context context) {
        this(context, null);
    }

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setFocusable(false);
        setBackgroundColor(ContextCompat.getColor(context, R.color.brand_midnight));
        buildRows();
        applyMetrics();
    }

    public void setTerminalProvider(TerminalProvider provider) {
        terminalProvider = provider;
    }

    public void applyPreferences(SharedPreferences preferences) {
        setVisibility(preferences.getBoolean("extra_keys_enabled", true)
                ? View.VISIBLE : View.GONE);
        boolean symbolsEnabled = preferences.getBoolean("extra_keys_symbols", true);
        symbolsButton.setVisibility(symbolsEnabled ? View.VISIBLE : View.GONE);
        if (!symbolsEnabled) showPrimaryPage();
    }

    public void resetModifiers() {
        EmulatorView terminal = terminal();
        if (terminal != null) {
            terminal.clearExtraModifiers();
        }
        onExtraModifierStateChanged(EmulatorView.EXTRA_MODIFIER_INACTIVE,
                EmulatorView.EXTRA_MODIFIER_INACTIVE);
    }

    @Override
    public void onExtraModifierStateChanged(int newControlState, int newAltState) {
        controlState = newControlState;
        altState = newAltState;
        updateModifierButton(controlButton, "CTRL", controlState);
        updateModifierButton(altButton, "ALT", altState);
    }

    private void buildRows() {
        primaryPage = addPage();
        LinearLayout primaryTop = addFixedRow(primaryPage);
        addKey(primaryTop, "ESC", KeyEvent.KEYCODE_ESCAPE);
        controlButton = addModifier(primaryTop, "CTRL", KEY_CTRL);
        altButton = addModifier(primaryTop, "ALT", KEY_ALT);
        addKey(primaryTop, "TAB", KeyEvent.KEYCODE_TAB);
        addKey(primaryTop, "HOME", KeyEvent.KEYCODE_MOVE_HOME);

        LinearLayout primaryBottom = addFixedRow(primaryPage);
        addKey(primaryBottom, "END", KeyEvent.KEYCODE_MOVE_END);
        addKey(primaryBottom, "←", KeyEvent.KEYCODE_DPAD_LEFT, "Left");
        addKey(primaryBottom, "↓", KeyEvent.KEYCODE_DPAD_DOWN, "Down");
        addKey(primaryBottom, "↑", KeyEvent.KEYCODE_DPAD_UP, "Up");
        addKey(primaryBottom, "→", KeyEvent.KEYCODE_DPAD_RIGHT, "Right");
        symbolsButton = addPageKey(primaryBottom, "SYM", this::showSymbolsPage);

        symbolsPage = addPage();
        LinearLayout symbolsTop = addFixedRow(symbolsPage);
        addKey(symbolsTop, "PGUP", KeyEvent.KEYCODE_PAGE_UP);
        addKey(symbolsTop, "PGDN", KeyEvent.KEYCODE_PAGE_DOWN);
        addKey(symbolsTop, "INS", KeyEvent.KEYCODE_INSERT);
        addKey(symbolsTop, "DEL", KeyEvent.KEYCODE_FORWARD_DEL);
        addTextKey(symbolsTop, "|");
        addTextKey(symbolsTop, "/");

        LinearLayout symbolsBottom = addFixedRow(symbolsPage);
        for (String symbol : new String[]{"~", "_", "-", "=", "+"}) {
            addTextKey(symbolsBottom, symbol);
        }
        addPageKey(symbolsBottom, "NAV", this::showPrimaryPage);
        symbolsPage.setVisibility(View.GONE);
    }

    private LinearLayout addPage() {
        LinearLayout page = new LinearLayout(getContext());
        page.setOrientation(VERTICAL);
        page.setFocusable(false);
        addView(page, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return page;
    }

    private LinearLayout addFixedRow(LinearLayout page) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setFocusable(false);
        page.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT,
                res(R.dimen.extra_keys_row_height)));
        return row;
    }

    private AppCompatButton addModifier(LinearLayout row, String label, int key) {
        AppCompatButton button = makeButton(label);
        button.setOnClickListener(view -> toggleModifier(key, false));
        button.setOnLongClickListener(view -> {
            toggleModifier(key, true);
            return true;
        });
        row.addView(button, buttonLayout());
        return button;
    }

    private AppCompatButton addPageKey(LinearLayout row, String label,
                                        OnClickListener listener) {
        AppCompatButton button = makeButton(label);
        button.setOnClickListener(listener);
        row.addView(button, buttonLayout());
        return button;
    }

    private void addKey(LinearLayout row, String label, int keyCode) {
        addKey(row, label, keyCode, label);
    }

    private void addKey(LinearLayout row, String label, int keyCode, String description) {
        AppCompatButton button = makeButton(label);
        button.setContentDescription(description);
        button.setOnClickListener(view -> {
            EmulatorView terminal = terminal();
            if (terminal != null) terminal.sendExtraKey(keyCode);
        });
        row.addView(button, buttonLayout());
    }

    private void addTextKey(LinearLayout row, String text) {
        AppCompatButton button = makeButton(text);
        button.setOnClickListener(view -> {
            EmulatorView terminal = terminal();
            if (terminal != null) terminal.sendExtraText(text);
        });
        row.addView(button, buttonLayout());
    }

    private AppCompatButton makeButton(String text) {
        AppCompatButton button = new AppCompatButton(getContext());
        button.setText(text);
        button.setTextSize(getResources().getInteger(R.integer.extra_keys_button_text_sp));
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setSingleLine(true);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        setButtonSurface(button, EmulatorView.EXTRA_MODIFIER_INACTIVE);
        return button;
    }

    private LayoutParams buttonLayout() {
        int margin = res(R.dimen.extra_keys_button_margin);
        LayoutParams params = new LayoutParams(0, res(R.dimen.extra_keys_button_height), 1);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private void showSymbolsPage(View ignored) {
        showSymbolsPage();
    }

    private void showSymbolsPage() {
        primaryPage.setVisibility(View.GONE);
        symbolsPage.setVisibility(View.VISIBLE);
    }

    private void showPrimaryPage(View ignored) {
        showPrimaryPage();
    }

    private void showPrimaryPage() {
        symbolsPage.setVisibility(View.GONE);
        primaryPage.setVisibility(View.VISIBLE);
    }

    private void toggleModifier(int key, boolean lock) {
        boolean control = key == KEY_CTRL;
        int current = control ? controlState : altState;
        int next = lock ? EmulatorView.EXTRA_MODIFIER_LOCKED
                : current == EmulatorView.EXTRA_MODIFIER_INACTIVE
                ? EmulatorView.EXTRA_MODIFIER_ARMED
                : EmulatorView.EXTRA_MODIFIER_INACTIVE;
        EmulatorView terminal = terminal();
        if (terminal != null) {
            terminal.setExtraModifierState(control, next);
        }
    }

    private EmulatorView terminal() {
        return terminalProvider == null ? null : terminalProvider.getTerminalView();
    }

    private void updateModifierButton(AppCompatButton button, String label, int state) {
        if (button == null) return;
        if (state == EmulatorView.EXTRA_MODIFIER_LOCKED) {
            button.setText(getResources().getString(
                    R.string.extra_key_modifier_locked_label, label));
            button.setContentDescription(getResources().getString(
                    R.string.extra_key_modifier_locked_description, label));
        } else if (state == EmulatorView.EXTRA_MODIFIER_ARMED) {
            button.setText(getResources().getString(
                    R.string.extra_key_modifier_armed_label, label));
            button.setContentDescription(getResources().getString(
                    R.string.extra_key_modifier_armed_description, label));
        } else {
            button.setText(label);
            button.setContentDescription(getResources().getString(
                    R.string.extra_key_modifier_description, label));
        }
        setButtonSurface(button, state);
    }

    private void setButtonSurface(AppCompatButton button, int state) {
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(dp(5));
        int stroke = ContextCompat.getColor(getContext(), R.color.brand_text_muted_dark);
        int fill = ContextCompat.getColor(getContext(), R.color.brand_navy_light);
        int text = ContextCompat.getColor(getContext(), R.color.brand_white);
        if (state == EmulatorView.EXTRA_MODIFIER_ARMED) {
            fill = ContextCompat.getColor(getContext(), R.color.brand_teal_dark);
            stroke = ContextCompat.getColor(getContext(), R.color.brand_teal);
        } else if (state == EmulatorView.EXTRA_MODIFIER_LOCKED) {
            fill = ContextCompat.getColor(getContext(), R.color.brand_teal);
            stroke = ContextCompat.getColor(getContext(), R.color.brand_white);
            text = ContextCompat.getColor(getContext(), R.color.brand_midnight);
        }
        surface.setColor(fill);
        surface.setStroke(dp(state == EmulatorView.EXTRA_MODIFIER_LOCKED ? 2 : 1), stroke);
        button.setBackground(surface);
        button.setTextColor(text);
    }

    /**
     * Applies the current configuration's extra-keys metrics to the bar and to
     * every row and button already built.
     *
     * <p>This bar is a fixed-height sibling of the terminal viewport, so its
     * height comes straight out of the terminal's. A short window -- landscape
     * on a phone, or a split-screen pane -- uses the compact set in
     * {@code values/}, while {@code values-h500dp/} keeps the roomier sizing.
     * {@code TermActivity} declares {@code configChanges} for orientation and
     * screenSize, so it is never recreated on rotation and the views would
     * otherwise keep whatever metrics they were built with.</p>
     */
    private void applyMetrics() {
        int viewPadding = res(R.dimen.extra_keys_view_padding);
        int rowPadding = res(R.dimen.extra_keys_row_padding_horizontal);
        int rowHeight = res(R.dimen.extra_keys_row_height);
        int buttonHeight = res(R.dimen.extra_keys_button_height);
        int buttonMargin = res(R.dimen.extra_keys_button_margin);
        float textSize = getResources().getInteger(R.integer.extra_keys_button_text_sp);

        setPadding(0, viewPadding, 0, viewPadding);
        for (int p = 0; p < getChildCount(); p++) {
            View pageView = getChildAt(p);
            if (!(pageView instanceof LinearLayout)) continue;
            LinearLayout page = (LinearLayout) pageView;
            for (int r = 0; r < page.getChildCount(); r++) {
                View rowView = page.getChildAt(r);
                if (!(rowView instanceof LinearLayout)) continue;
                LinearLayout row = (LinearLayout) rowView;
                row.setPadding(rowPadding, 0, rowPadding, 0);
                LayoutParams rowParams = (LayoutParams) row.getLayoutParams();
                rowParams.height = rowHeight;
                row.setLayoutParams(rowParams);
                for (int b = 0; b < row.getChildCount(); b++) {
                    View button = row.getChildAt(b);
                    LayoutParams params = (LayoutParams) button.getLayoutParams();
                    params.height = buttonHeight;
                    params.setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin);
                    button.setLayoutParams(params);
                    if (button instanceof AppCompatButton) {
                        ((AppCompatButton) button).setTextSize(textSize);
                    }
                }
            }
        }
        appliedRowHeight = rowHeight;
        requestLayout();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyMetrics();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The constructor runs during inflation, while the activity's resources
        // can still carry the previous configuration -- TermActivity declares
        // configChanges for orientation and screenSize, so a rotation neither
        // recreates it nor necessarily reaches onConfigurationChanged. By the
        // time we are measured the configuration is correct, so re-resolve here
        // whenever the qualifier has selected a different set.
        if (res(R.dimen.extra_keys_row_height) != appliedRowHeight) {
            applyMetrics();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int res(int dimenId) {
        return getResources().getDimensionPixelSize(dimenId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
