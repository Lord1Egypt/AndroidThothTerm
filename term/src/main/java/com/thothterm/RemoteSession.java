/*
 * Copyright (C) 2024 Roumen Petrov.  All rights reserved.
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

import androidx.annotation.NonNull;
import jackpal.androidterm.BoundSession;
import jackpal.androidterm.RemoteInterface;
import jackpal.androidterm.TermService;
import jackpal.androidterm.emulatorview.TermSession;


public class RemoteSession extends RemoteInterface {
    protected void processAction(@NonNull Intent intent, @NonNull String action) {
        if (!Application.ACTION_OPEN_NEW_WINDOW.equals(action)) return;

        // Note target window is returned by startSession Terminal interface.
        String handle = intent.getStringExtra(Application.ARGUMENT_TARGET_WINDOW);
        if (handle == null)
            // Note RemoteInterface is also responsible to open new window.
            super.processAction(intent, action);

        switchWindow(handle);
    }

    private void switchWindow(@NonNull String handle) {
        TermService service = getTermService();
        if (service == null) return; // just in case

        // Find the bound session target window.
        // Note handle is required session attribute.
        TermSession target = null;
        int index;
        for (index = 0; index < service.getSessionCount(); ++index) {
            TermSession session = service.getSession(index);
            if (!(session instanceof BoundSession)) {
                continue;
            }
            String h = ((BoundSession) session).getHandle();
            if (handle.equals(h)) {
                target = session;
                break;
            }
        }
        if (target == null) return;

        switchWindowActivity(index);

        setResultWindow(handle);
    }
}
