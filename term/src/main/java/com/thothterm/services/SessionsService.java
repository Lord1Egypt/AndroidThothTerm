/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.thothterm.services;

import android.app.Service;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.util.SessionList;


public abstract class SessionsService extends Service {
    private final SessionList sessions = new SessionList();


    public SessionList getSessions() {
        return sessions;
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public TermSession getSession(int index) {
        try {
            return sessions.get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public void addSession(TermSession session) {
        addSession(session, this::onSessionFinish);
    }

    protected void addSession(TermSession session, TermSession.FinishCallback callback) {
        sessions.add(session);
        session.setFinishCallback(callback);
    }

    protected void removeSession(TermSession session) {
        sessions.remove(session);
    }

    public void clearSessions() {
        for (TermSession session : sessions) {
            /* Don't automatically remove from list of sessions -- we clear the
             * list below anyway and we could trigger
             * ConcurrentModificationException if we do */
            session.setFinishCallback(null);
            session.finish();
        }
        sessions.clear();
    }

    private void onSessionFinish(TermSession session) {
        removeSession(session);
    }
}
