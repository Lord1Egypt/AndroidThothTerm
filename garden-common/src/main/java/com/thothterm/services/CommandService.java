/*
 * Copyright (C) 2019-2025 Roumen Petrov.  All rights reserved.
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

import android.os.Process;
import android.text.TextUtils;

import com.thothterm.BuildConfig;
import com.thothterm.Installer;
import com.thothterm.remote.CommandCollector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import jackpal.androidterm.TermService;


public class CommandService implements UnixSocketServer.ConnectionHandler {
    private static final String socket_prefix = BuildConfig.APPLICATION_ID + "-app_info-";

    private final TermService service;
    private UnixSocketServer socket;

    public CommandService(TermService service) {
        this.service = service;
        try {
            socket = new UnixSocketServer(socket_prefix + Process.myUid(), this);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public void start() {
        if (socket == null) return;
        socket.start();
    }

    public void stop() {
        if (socket == null) return;
        socket.stop();
        socket = null;
    }

    @Override
    public void handle(InputStream basein, OutputStream baseout) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(basein));

        // Note only one command per connection!
        String line = in.readLine();
        if (TextUtils.isEmpty(line)) return;

        switch (line) {
            case "get aliases":
                printAliases(baseout);
                break;
            case "get cmd_path":
                handleCommandPath(in, baseout);
                break;
            case "get cmd_env":
                handleCommandEnvironment(in, baseout);
                break;
            case "open sysconfig":
                handleCommandConfiguration(in, baseout);
                break;
        }
    }

    private void printAliases(OutputStream baseout) {
        PrintStream out = new PrintStream(baseout);

        // force interactive shell
        out.println("alias sh='sh -i'");

        if (!TextUtils.isEmpty(Installer.APPEXEC_COMMAND))
            CommandCollector.printExternalAliases(out);
        out.flush();
    }

    private ArrayList<String> getArguments(BufferedReader in) throws IOException {
        // Note "end of line" command is required.
        ArrayList<String> args = new ArrayList<>();
        boolean eol = false;
        do {
            String line = in.readLine();
            if (TextUtils.isEmpty(line)) break;
            if ("<eol>".equals(line)) {
                eol = true;
                break;
            }
            args.add(line);
        } while (true);
        return eol ? args : null;
    }

    private void endResponse(OutputStream baseout) throws IOException {
        baseout.flush();

        PrintStream out = new PrintStream(baseout);
        out.println("<eol>");
        out.flush();
    }

    private void handleCommandPath(BufferedReader in, OutputStream out) throws IOException {
        if (TextUtils.isEmpty(Installer.APPEXEC_COMMAND)) return;

        ArrayList<String> args = getArguments(in);
        if (args == null) return;

        CommandCollector.writeCommandPath(service.getApplicationContext(), args, out);
        endResponse(out);
    }

    private void handleCommandEnvironment(BufferedReader in, OutputStream out) throws IOException {
        ArrayList<String> args = getArguments(in);
        if (args == null) return;

        CommandCollector.writeCommandEnvironment(service.getApplicationContext(), args, out);
        endResponse(out);
    }

    private void handleCommandConfiguration(BufferedReader in, OutputStream out) throws IOException {
        ArrayList<String> args = getArguments(in);
        if (args == null) return;

        CommandCollector.openCommandConfiguration(service.getApplicationContext(), args, out);
    }
}
