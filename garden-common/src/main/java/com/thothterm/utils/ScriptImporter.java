/*
 * Copyright (C) 2021-2023 Roumen Petrov.  All rights reserved.
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

package com.thothterm.utils;

import android.net.Uri;

import com.thothterm.R;
import com.thothterm.widget.ScreenMessage;

import java.io.IOException;
import java.io.InputStream;

import androidx.appcompat.app.AppCompatActivity;
import jackpal.androidterm.emulatorview.TermSession;


public class ScriptImporter {

    public static void paste(AppCompatActivity activity, Uri uri, TermSession session) {
        if (uri == null) return;

        new Thread(() -> {
            try {
                InputStream inraw = activity.getContentResolver().openInputStream(uri);
                if (inraw == null) throw new IOException("null script input stream");

                Stream.copy(inraw, session.getTermOut());
            } catch (IOException ignore) {
                activity.runOnUiThread(() ->
                        showError(activity, R.string.script_import_error));
            } catch (SecurityException ignore) {
                activity.runOnUiThread(() ->
                        showError(activity, R.string.script_access_error));
            }
        }).start();
    }

    private static void showError(AppCompatActivity activity, int rid) {
        ScreenMessage.show(activity.getApplicationContext(), rid);
    }
}
