/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm.lan;

import android.os.ParcelFileDescriptor;

import com.thothterm.Process;
import com.thothterm.TermIO;
import com.thothterm.linux.RootfsManager;
import com.thothterm.linux.UbuntuRuntime;
import com.thothterm.linux.UbuntuTermSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jackpal.androidterm.SessionHangup;

/**
 * A browser terminal's PTY: the same PRoot command line, environment, rootfs,
 * resolver and thoth account as a phone window, on a pseudo-terminal of its
 * own, started through the same libtermexec call.
 */
final class UbuntuPty implements Pty {
    /** xterm.js implements xterm's 256-colour terminal. */
    private static final String TERMINAL_TYPE = "xterm-256color";

    private final ParcelFileDescriptor master;
    private final int pid;
    private final InputStream input;
    private final OutputStream output;
    private volatile boolean exited;
    private final AtomicBoolean hungUp = new AtomicBoolean();

    private UbuntuPty(ParcelFileDescriptor master, int pid) {
        this.master = master;
        this.pid = pid;
        this.input = new FileInputStream(master.getFileDescriptor());
        this.output = new FileOutputStream(master.getFileDescriptor());
    }

    static final Factory FACTORY = UbuntuPty::open;

    static UbuntuPty open(int columns, int rows) throws IOException {
        UbuntuTermSession.prepareRuntime();
        UbuntuRuntime runtime = UbuntuRuntime.from(RootfsManager.get(), TERMINAL_TYPE);
        List<String> argv = runtime.buildArgv();
        String[] env = UbuntuRuntime.toEnvArray(runtime.buildEnvironment());

        ParcelFileDescriptor master = ParcelFileDescriptor.open(new File("/dev/ptmx"),
                ParcelFileDescriptor.MODE_READ_WRITE);
        try {
            TermIO.setUTF8Input(master, true);
            TermIO.setWindowSize(master, rows, columns);
            int pid = Process.createSubprocess(master, argv.get(0),
                    argv.toArray(new String[0]), env);
            if (pid <= 0) throw new IOException("PRoot did not start");
            return new UbuntuPty(master, pid);
        } catch (IOException | RuntimeException e) {
            master.close();
            throw e;
        }
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    @Override
    public void resize(int columns, int rows) throws IOException {
        TermIO.setWindowSize(master, rows, columns);
    }

    @Override
    public int waitFor() {
        int status = Process.waitExit(pid);
        exited = true;
        return status;
    }

    @Override
    public void hangUp() {
        if (!hungUp.compareAndSet(false, true)) return;
        // Once proot is reaped its tracees are gone too, and its pid may
        // already be someone else's: signal only a live tree.
        if (!exited) SessionHangup.hangUp(pid, () -> exited);
        try {
            master.close();
        } catch (IOException ignore) {
            // The master is released either way.
        }
    }

    @Override
    public int pid() {
        return pid;
    }
}
