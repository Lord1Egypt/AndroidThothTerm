/*
 * Copyright (C) 2023-2024 Roumen Petrov.  All rights reserved.
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

package com.thothterm.remote;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.thothterm.BuildConfig;
import com.thothterm.v1.ICommand;

import java.util.HashMap;
import java.util.Set;


public class TrustedApplications {
    private static final HashMap<String, RemoteConnection> list;

    static {
        list = new HashMap<>();
        // keep synchronised with package "queries" in Android manifest
        list.put("sb", new RemoteConnection(
                "com.thothterm.securebox" + BuildConfig.APPLICATION_ID_SUFFIX,
                "com.thothterm.services.CommandService"));
        list.put("demo", new RemoteConnection(
                "com.thothterm.sample.addon" + BuildConfig.APPLICATION_ID_SUFFIX,
                "com.thothterm.sample.addon.CommandService"));
    }

    public static int size() {
        return list.size();
    }

    public static Set<String> keySet() {
        return list.keySet();
    }

    public static ICommand getRemote(String key) {
        RemoteConnection v = list.get(key);
        if (v == null) return null;
        return v.remote;
    }

    public static boolean bind(Context context, String key, final OnApplicationConnectionListener cb) {
        RemoteConnection v = list.get(key);
        if (v == null) return false;

        v.setOnApplicationConnectionListener((status) -> {
            if (!status) return;
            cb.onApplicationConnection();
        });

        return v.bind(context);
    }

    public static void unbind(Context context, String key) {
        RemoteConnection v = list.get(key);
        if (v == null) return;

        context.unbindService(v.connection);
    }


    public interface OnApplicationConnectionListener {
        void onApplicationConnection();
    }

    static class RemoteConnection {
        private final ComponentName component;
        private ICommand remote;
        private OnApplicationConnectionListener callback;

        private final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                remote = ICommand.Stub.asInterface(service);
                if (callback == null) return;
                callback.onApplicationConnection(true);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                remote = null;
                if (callback == null) return;
                callback.onApplicationConnection(false);
            }
        };

        RemoteConnection(String application, String config_class) {
            component = new ComponentName(application, config_class);
        }

        private void setOnApplicationConnectionListener(OnApplicationConnectionListener listener) {
            callback = listener;
        }

        private boolean bind(Context context) {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".command.v1")
                    .setComponent(component);
            return context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        private interface OnApplicationConnectionListener {
            void onApplicationConnection(boolean status);
        }
    }
}
