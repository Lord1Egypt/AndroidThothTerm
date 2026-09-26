/*
 * Copyright (C) 2023-2025 Roumen Petrov.  All rights reserved.
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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.thothterm.Application;
import com.thothterm.utils.Stream;
import com.thothterm.v1.ICommand;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;


/**
 * Functionality that replaces broadcast based "path collection".
 * New command collection is based on remote procedure calls to
 * set of trusted applications that extract command details.
 * Commands are stored as public list of pairs. Each pair describes
 * command as key and command information details application
 * and other command attributes.
 */
public class CommandCollector {
    private static final HashMap<String, CommandInfo> list = new HashMap<>();
    private int pending = 0;
    private OnCommandsConnectedListener callback;

    public static void writeCommandPath(Context context, @NonNull ArrayList<String> args, OutputStream out) {
        //noinspection SizeReplaceableByIsEmpty
        if (args.size() < 1) return;

        String cmd = args.get(0);
        CommandInfo info = list.get(cmd);
        if (info == null) return;

        info.getPath(context, cmd);
        if (info.path == null) return;

        PrintStream prn = new PrintStream(out);
        prn.println(info.path);
    }

    public static void writeCommandEnvironment(Context context, @NonNull ArrayList<String> args, OutputStream out) {
        //noinspection SizeReplaceableByIsEmpty
        if (args.size() < 1) return;

        String cmd = args.get(0);
        CommandInfo info = list.get(cmd);
        if (info == null) return;

        info.getEnv(context, cmd);
        if (info.env == null) return;

        PrintStream prn = new PrintStream(out);
        for (String item : info.env) {
            prn.println(item);
        }

        // setup "loader" environment
        File cmdpath = new File(info.path);
        File cmddir = cmdpath.getParentFile();
        String libpath = (cmddir != null) ? cmddir.getPath() : null;
        libpath = Application.buildLoaderLibraryPath(libpath);
        prn.println("LD_LIBRARY_PATH=" + libpath);
    }

    public static void openCommandConfiguration(Context context, @NonNull ArrayList<String> args, OutputStream out) {
        if (args.size() < 2) return;

        String cmd = args.get(0);
        CommandInfo info = list.get(cmd);
        if (info == null) return;

        String path = args.get(1);
        try (ParcelFileDescriptor pfd = info.openSysconfig(context, path)) {
            if (pfd == null) return;

            FileDescriptor fd = pfd.getFileDescriptor();
            if (fd == null) return;

            FileInputStream in = new FileInputStream(fd);
            /* start application response with requested file name
             * as indicator for successful "open" operation
             */
            out.write(path.getBytes());
            Stream.copy(in, out);
        } catch (IOException ignore) {
        }
    }

    public static void printExternalAliases(PrintStream out) {
        for (String app : TrustedApplications.keySet()) {
            ICommand remote = TrustedApplications.getRemote(app);
            if (remote == null) continue;

            String[] app_cmds;
            try {
                app_cmds = remote.getCommands();
            } catch (RemoteException ignore) {
                continue;
            }
            if (app_cmds == null) continue;

            for (String cmd : app_cmds) {
                CommandInfo info = list.get(cmd);
                if (info != null) continue;
                list.put(cmd, new CommandInfo(app));
            }
        }

        for (String cmd : list.keySet()) {
            out.println("alias " + cmd + "='t1pcmd " + cmd + "'");
        }
        out.flush();
    }

    public static void collect(Context context, OnCommandsConnectedListener listener) {
        final CommandCollector collector = new CommandCollector();
        collector.setOnCommandsConnectedListener(listener);
        collector.start(context);
    }

    private void start(Context context) {
        pending = TrustedApplications.size();
        new Handler(Looper.getMainLooper()).post(() -> {
            for (String app : TrustedApplications.keySet()) {
                ICommand remote = TrustedApplications.getRemote(app);
                if (remote == null) {
                    boolean flag = TrustedApplications.bind(context, app,
                            CommandCollector.this::onApplicationConnectionNotification);
                    if (flag) continue;
                }
                onApplicationConnectionNotification();
            }
        });
    }

    public void setOnCommandsConnectedListener(OnCommandsConnectedListener listener) {
        callback = listener;
    }

    private void onApplicationConnectionNotification() {
        if (--pending > 0) return;
        if (callback == null) return;
        callback.onCommandsConnected();
    }


    public interface OnCommandsConnectedListener {
        void onCommandsConnected();
    }


    private static class CommandInfo {
        private final String app;
        private String path;
        private String[] env;

        CommandInfo(String app) {
            this.app = app;
        }

        private void getPath(Context context, String cmd) {
            if (path != null) {
                File exe = new File(path);
                if (!exe.exists()) {
                    // path may change on trusted application upgrade/preinstallation
                    path = null;
                }
            }
            if (path != null) return;

            ICommand remote = getRemote(context);
            if (remote == null) return;

            {
                try {
                    // trust result
                    path = remote.getPath(cmd);
                } catch (RemoteException ignore) {
                }
            }
        }

        private void getEnv(Context context, String cmd) {
            if (env != null) return;

            ICommand remote = getRemote(context);
            if (remote == null) return;

            {
                try {
                    // trust result
                    env = remote.getEnvironment(cmd);
                } catch (RemoteException ignore) {
                }
            }
        }

        private ParcelFileDescriptor openSysconfig(Context context, String path) {
            ICommand remote = getRemote(context);
            if (remote == null) return null;

            try {
                return remote.openConfiguration(path);
            } catch (RemoteException ignore) {
            }
            return null;
        }

        private ICommand getRemote(Context context) {
            ICommand remote = TrustedApplications.getRemote(app);
            if (remote != null) return remote;

            final Semaphore semaphore = new Semaphore(0);

            new Handler(Looper.getMainLooper()).post(() -> {
                boolean flag = TrustedApplications.bind(context, app, semaphore::release);
                if (!flag)
                    semaphore.release();
            });

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            return TrustedApplications.getRemote(app);
        }
    }
}
