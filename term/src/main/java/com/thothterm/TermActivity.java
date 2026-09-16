/*
 * Copyright (C) 2018-2024 Roumen Petrov.  All rights reserved.
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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.thothterm.utils.ScriptImporter;
import com.thothterm.utils.ThemeManager;
import com.thothterm.widget.ScreenMessage;

import jackpal.androidterm.emulatorview.TermSession;


public class TermActivity extends jackpal.androidterm.Term {

    private final ActivityResultLauncher<Intent> request_paste_script =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> onRequestPasteScript(result.getResultCode(), result.getData())
            );

    private static Intent getTermActivityIntent(Context context) {
        return new Intent(context, TermActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static Intent getNewWindowIntent(Context context) {
        return getTermActivityIntent(context)
                .setAction(WINDOW_ACTION_NEW);
    }

    public static Intent getSwitchWindowIntent(Context context) {
        return getTermActivityIntent(context)
                .setAction(WINDOW_ACTION_SWITCH);
    }

    public static Intent getNotificationIntent(Context context) {
        return getTermActivityIntent(context);
    }

    private void doPasteScript() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType("text/*")
                .putExtra("CONTENT_TYPE", "text/x-shellscript")
                .putExtra("TITLE", R.string.script_intent_title);
        try {
            request_paste_script.launch(intent);
        } catch (ActivityNotFoundException ignore) {
            ScreenMessage.show(getApplicationContext(),
                    R.string.script_source_content_error);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private void onRequestPasteScript(int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK) return;
        if (data == null) return;

        TermSession session = getCurrentTermSession();
        if (session == null) return;
        ScriptImporter.paste(this, data.getData(), session);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.setHeaderTitle(R.string.edit_text);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_session, menu);
        if (!canPaste()) {
            MenuItem item = menu.findItem(R.id.session_paste);
            if (item != null) item.setEnabled(false);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        /* NOTE: Resource IDs will be non-final in Android Gradle Plugin version 5.0,
           avoid using them in switch case statements */
        if (id == R.id.session_select_text)
            getCurrentEmulatorView().toggleSelectingText();
        else if (id == R.id.session_copy_all)
            doCopyAll();
        else if (id == R.id.session_paste)
            doPaste();
        else if (id == R.id.session_paste_script)
            doPasteScript();
        else if (id == R.id.session_send_cntr)
            getCurrentEmulatorView().sendControlKey();
        else if (id == R.id.session_send_fn)
            getCurrentEmulatorView().sendFnKey();
        else
            return super.onContextItemSelected(item);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // do not process preference "Theme Mode"
        if (ThemeManager.PREF_THEME_MODE.equals(key)) return;

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    @Override
    protected void updatePrefs() {
        Integer theme_resid = getThemeId();
        if (theme_resid != null) {
            if (theme_resid != ThemeManager.presetTheme(this, false, theme_resid)) {
                restart(R.string.restart_thememode_change);
                return;
            }
        }
        super.updatePrefs();

        setScreenOrientation();
    }

    private void setScreenOrientation() {
        int o;
        switch (Application.settings.getOrientation()) {
            case Settings.Orientation.LANDSCAPE:
                o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case Settings.Orientation.PORTRAIT:
                o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case Settings.Orientation.SYSTEM:
                o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                break;
            default:
                o = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
        setRequestedOrientation(o);
    }
}
