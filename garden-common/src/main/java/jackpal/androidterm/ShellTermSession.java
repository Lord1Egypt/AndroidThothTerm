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

package jackpal.androidterm;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;

import com.thothterm.Process;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;

import jackpal.androidterm.util.TermSettings;


/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys it's process group
 * upon stopping. Subclasses say what to run; there is no host-shell fallback.
 */
public abstract class ShellTermSession extends GenericTermSession {
    private static final int PROCESS_EXITED = 1;

    private final int mProcId;
    private final Thread mWatcherThread;
    /** Set once waitExit() has reaped the shell. */
    private volatile boolean mExited;


    public ShellTermSession(TermSettings settings) throws IOException {
        // exitOnEOF: the window closes when nothing holds its terminal any more
        // -- the shell exited and PRoot hung up the rest of its session --
        // even while PRoot keeps running for a nohup'd job.
        super(ParcelFileDescriptor.open(new File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE),
                settings, true);

        mProcId = createShellProcess(settings);
        ThothLog.i(LogCategory.SHELL, "Shell process started pid=" + mProcId);
        final Handler handler = new ProcessHandler(this);
        mWatcherThread = new Thread(() -> {
            ThothLog.d(LogCategory.SHELL, "Waiting for shell exit pid=" + mProcId);
            int result = Process.waitExit(mProcId);
            mExited = true;
            handler.sendMessage(handler.obtainMessage(PROCESS_EXITED, result));
        });
        mWatcherThread.setName("Process watcher");
    }

    @Override
    public void initializeEmulator(int columns, int rows) {
        super.initializeEmulator(columns, rows);

        mWatcherThread.start();
    }

    protected int createShellProcess(TermSettings settings) throws IOException {
        ArrayList<String> argList = buildArgv(settings);
        String arg0 = argList.get(0);
        File file = new File(arg0);
        if (!file.exists()) {
            ThothLog.e(LogCategory.SHELL,
                    "Shell executable not found: " + file.getName());
            throw new FileNotFoundException(arg0);
        } else if (!file.canExecute()) {
            ThothLog.e(LogCategory.SHELL,
                    "Shell executable not executable: " + file.getName());
            throw new FileNotFoundException(arg0);
        }
        String[] args = argList.toArray(new String[0]);
        ThothLog.d(LogCategory.SHELL, "Shell executable name=" + file.getName());

        Map<String, String> map = buildEnvironment(settings);

        String[] env = new String[map.size()];
        int k = 0;
        for (Map.Entry<String, String> entry : map.entrySet())
            env[k++] = entry.getKey() + "=" + entry.getValue();

        return Process.createSubprocess(mTermFd, arg0, args, env);
    }

    protected abstract ArrayList<String> buildArgv(TermSettings settings);

    protected abstract Map<String, String> buildEnvironment(TermSettings settings);

    private void onProcessExit(int result) {
        if (result == 0)
            ThothLog.i(LogCategory.SHELL, "Shell exited code=0");
        else
            ThothLog.w(LogCategory.SHELL, "Shell exited code=" + result);
        onProcessExit();
    }

    @Override
    public void finish() {
        SessionHangup.hangUp(mProcId, () -> mExited);
        super.finish();
    }

    /** The shell pid, which is also its process group id. */
    public int getProcessId() {
        return mProcId;
    }

    /**
     * SIGKILL whatever is left of this session's process group. Only for the
     * Exit action, after {@link #finish()} has been given its chance -- a
     * PRoot tree that is wedged in a syscall will not act on SIGHUP.
     */
    public void kill() {
        Process.killChilds(mProcId);
    }

    private static class ProcessHandler extends Handler {
        private final WeakReference<ShellTermSession> reference;

        ProcessHandler(ShellTermSession session) {
            super(Looper.getMainLooper());
            reference = new WeakReference<>(session);
        }

        @Override
        public void handleMessage(Message msg) {
            ShellTermSession session = reference.get();
            if (session == null) return;
            if (!session.isRunning()) return;

            if (msg.what == PROCESS_EXITED)
                session.onProcessExit((Integer) msg.obj);
        }
    }
}
