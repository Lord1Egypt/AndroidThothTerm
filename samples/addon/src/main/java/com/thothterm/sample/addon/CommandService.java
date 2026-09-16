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

package com.thothterm.sample.addon;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import com.thothterm.v1.ICommand;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;


public class CommandService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new RBinder();
    }

    private static final class RBinder extends ICommand.Stub {
        public String[] getCommands() {
            return new String[]{
                    "addon1",
                    "addon2",
            };
        }

        @Override
        public String getPath(String cmd) {
            return Application.xbindir.getPath() + "/libcmd-addon.so";
        }

        @Override
        public String[] getEnvironment(String cmd) {
            if (!"addon2".equals(cmd)) return new String[0];

            ArrayList<String> env = new ArrayList<>();

            String cmd_conf = Application.etcdir.getPath() + "/addon.conf";
            env.add("ADDON_CONF=" + cmd_conf);
            return env.toArray(new String[0]);
        }

        @Override
        public ParcelFileDescriptor openConfiguration(String path) {
            if ("/etc/addon.conf".equals(path))
                return open_sysconfig("addon.conf");
            return null;
        }

        private ParcelFileDescriptor open_sysconfig(String name) {
            try {
                File conf = new File(Application.etcdir.getPath(), name);
                try (FileInputStream in = new FileInputStream(conf)) {
                    FileDescriptor fd = in.getFD();
                    return ParcelFileDescriptor.dup(fd);
                }
            } catch (FileNotFoundException ignore) {
            } catch (IOException ignore) {
            }
            return null;
        }
    }
}
