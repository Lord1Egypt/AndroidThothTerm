/*
 * Copyright (C) 2017-2026 Roumen Petrov.  All rights reserved.
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.thothterm.utils.WrapOpenURL;


/**
 * An action bar for terminal emulator activity.
 */

public class TermActionBar {
    private final DrawerLayout drawer;
    private final NavigationView nav_view;
    private final Toolbar toolbar;
    private final ActionBar appbar;

    private TermActionBar(AppCompatActivity context, boolean floating) {
        toolbar = context.findViewById(R.id.toolbar);
        context.setSupportActionBar(toolbar);
        toolbar.setAlpha(floating ? 0.625f : 1.0f);

        drawer = context.findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                context, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        ) {
            @Override
            public void onDrawerOpened(View drawerView) {
                hideSoftInput(drawerView);
                super.onDrawerOpened(drawerView);
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        nav_view = context.findViewById(R.id.nav_view);
        NavigationBackground.presetColors(context, nav_view);
        {
            View header = nav_view.getHeaderView(0);
            TextView ver = header.findViewById(R.id.app_ver);
            ver.setText(Application.VER);
            View home = header.findViewById(R.id.app_home);
            home.setOnClickListener(this::onAppIconClicked);
        }

        appbar = context.getSupportActionBar();
        if (appbar != null) {
            appbar.setTitle(R.string.application_terminal);
        }

        if (floating)
            hide();
    }

    public static TermActionBar setTermContentView(AppCompatActivity context, boolean floating) {
        if (floating)
            context.setContentView(R.layout.drawer_term_floatbar);
        else
            context.setContentView(R.layout.drawer_term);

        return new TermActionBar(context, floating);
    }

    /**
     * Show the current session in the toolbar subtitle. The label is ellipsized
     * by the toolbar, so long shell titles never wrap or crowd the menu.
     */
    public void setSessionTitle(CharSequence title) {
        if (appbar == null) return;
        appbar.setSubtitle(title);
    }

    /**
     * Tapping the title/subtitle area opens the window switcher.
     */
    public void setOnTitleClickListener(View.OnClickListener listener) {
        toolbar.setOnClickListener(listener);
    }

    public void setOnNavigationItemSelectedListener(
            NavigationView.OnNavigationItemSelectedListener listener
    ) {
        nav_view.setNavigationItemSelectedListener(item -> {
            boolean result = listener.onNavigationItemSelected(item);
            drawer.closeDrawer(GravityCompat.START);
            return result;
        });
    }

    public boolean isShowing() {
        return toolbar.getVisibility() == View.VISIBLE;
    }

    public void hide() {
        toolbar.setVisibility(View.GONE);
    }

    public void show() {
        toolbar.setVisibility(View.VISIBLE);
    }

    public void doToggleActionBar() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    public void lockDrawer(boolean flag) {
        if (flag)
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        else
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void hideSoftInput(final View view) {
        new Thread() {
            @Override
            public void run() {
                Context context = view.getContext();

                InputMethodManager imm = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm == null) return;

                android.os.IBinder token = view.getWindowToken();
                imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();
    }

    public void onAppIconClicked(View view) {
        WrapOpenURL.launch(view.getContext(), urlApplicationSite());
    }

    private static class NavigationBackground {
        private static void presetColors(AppCompatActivity context, NavigationView view) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP /* API Level 21*/) {
                // managed as attribute in drawable header_background
                return;
            }

            @ColorInt final int[] colors = {
                    // see header_background
                    0x78909C /* Blue Gray 50, 400 */,
                    0x607D8B /* Blue Gray 50, 500 */,
                    0x455A64 /* Blue Gray 50, 700 */
            };
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = context.getTheme();
            if (theme.resolveAttribute(R.attr.colorPrimaryLight, typedValue, true))
                colors[0] = typedValue.data;
            if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true))
                colors[1] = typedValue.data;
            if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true))
                colors[2] = typedValue.data;

            Compat16.setColors(view, colors);
        }

        private static class Compat16 {
            private static void setColors(NavigationView view, int[] colors) {
                try {
                    View header = view.getHeaderView(0);
                    GradientDrawable drawable = (GradientDrawable) header.getBackground();
                    drawable.setColors(colors);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private String urlApplicationSite() {
        Context context = drawer.getContext();
        return context.getResources().getString(R.string.application_site);
    }
}
