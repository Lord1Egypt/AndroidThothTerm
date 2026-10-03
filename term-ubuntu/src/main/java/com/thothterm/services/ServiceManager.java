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

package com.thothterm.services;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import jackpal.androidterm.TermService;


public class ServiceManager {
    public Intent intent;
    private OnServiceConnectionListener listener;

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (listener == null) return;
            TermService.TSBinder binder = (TermService.TSBinder) service;
            listener.onServiceConnection(binder.getService());
        }

        public void onServiceDisconnected(ComponentName name) {
            if (listener == null) return;
            listener.onServiceConnection(null);
        }
    };

    public void onCreate(Context context) {
        intent = StartServiceCompat.start(context);
    }

    public void onStart(Context context) {
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new IllegalStateException("Failed to bind to TermService!");
    }

    public void onStop(Context context) {
        context.unbindService(connection);
    }

    public void onDestroy(Context context) {
        listener = null;
        context.stopService(intent);
    }

    public void setOnServiceConnectionListener(OnServiceConnectionListener listener) {
        this.listener = listener;
    }


    public interface OnServiceConnectionListener {
        void onServiceConnection(TermService service);
    }

    private static class StartServiceCompat {
        private static Intent start(Context context) {
            Intent intent = new Intent(context, TermService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /*API level 26*/)
                Compat26.start(context, intent);
            else
                Compat.start(context, intent);
            return intent;
        }

        @RequiresApi(26)
        private static class Compat26 {
            private static void start(Context context, Intent intent) {
                context.startForegroundService(intent);
            }
        }

        private static class Compat {
            private static void start(Context context, Intent intent) {
                context.startService(intent);
            }
        }
    }
}
