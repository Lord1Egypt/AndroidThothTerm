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
import java.util.Timer;
import java.util.TimerTask;

import jackpal.androidterm.util.TermSettings;


/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys it's process group
 * upon stopping.
 */
public abstract class ShellTermSession extends GenericTermSession {
    private static final int PROCESS_EXITED = 1;

    private final String mInitialCommand;
    private final int mProcId;
    private final Thread mWatcherThread;
    /** Set once waitExit() has reaped the shell. */
    private volatile boolean mExited;


    public ShellTermSession(TermSettings settings, String initialCommand) throws IOException {
        super(ParcelFileDescriptor.open(new File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE),
                settings, false);

        mInitialCommand = initialCommand;

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
        sendInitialCommand();
    }

    private void sendInitialCommand() {
        if (mInitialCommand.length() == 0) return;

        // wait display of shell prompt (speculative)
        // before to enter initial commands
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                write(mInitialCommand + '\r');
            }
        }, 500);
    }

    private int createShellProcess(TermSettings settings) throws IOException {
        ArrayList<String> argList = buildArgv(settings);
        String arg0 = argList.get(0);
        File file = new File(arg0);
        if (!file.canExecute()) {
            ThothLog.e(LogCategory.SHELL,
                    "Session executable missing or not executable: " + file.getName());
            throw new FileNotFoundException(arg0);
        }
        String[] args = argList.toArray(new String[0]);
        ThothLog.d(LogCategory.SHELL, "Session executable name=" + file.getName());

        Map<String, String> map = buildEnvironment(settings);
        String[] env = new String[map.size()];
        int k = 0;
        for (Map.Entry<String, String> entry : map.entrySet())
            env[k++] = entry.getKey() + "=" + entry.getValue();

        return Process.createSubprocess(mTermFd, arg0, args, env, workingDirectory());
    }

    /** The session's argv; element 0 is the executable. */
    protected abstract ArrayList<String> buildArgv(TermSettings settings);

    /** The complete environment of the session process. */
    protected abstract Map<String, String> buildEnvironment(TermSettings settings);

    /** Host-side working directory the session process starts in. */
    protected abstract String workingDirectory();

    private void onProcessExit(int result) {
        if (result == 0)
            ThothLog.i(LogCategory.SHELL, "Shell exited code=0");
        else
            ThothLog.w(LogCategory.SHELL, "Shell exited code=" + result);
        onProcessExit();
    }

    /** How long a closed window's shell gets to honour SIGHUP before SIGKILL. */
    private static final long KILL_GRACE_MS = 400;

    /** android.os.Process has no named constant for SIGHUP. */
    private static final int SIGNAL_HUP = 1;

    @Override
    public void finish() {
        final int foreground = SessionProcesses.foregroundGroupOf(mProcId);

        Process.finishChilds(mProcId);
        // Hang up the whole session, as a real terminal hangup does: job
        // control gives the foreground job and every background job a process
        // group of their own, so the shell's group alone misses them. A
        // background job that ignores SIGHUP (nohup) keeps running, as
        // intended.
        for (int pid : SessionProcesses.members(mProcId)) {
            android.os.Process.sendSignal(pid, SIGNAL_HUP);
        }
        super.finish();

        // Backstop for whatever ignored the hangup in the window's foreground:
        // the shell itself and the job the user was running there. Only this
        // session's groups are killed -- the shell's only while it is
        // unreaped, the job's only while the group is still in this session --
        // so a recycled id is never hit.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!mExited) Process.killChilds(mProcId);
            if (foreground > 0 && foreground != mProcId
                    && SessionProcesses.groupInSession(foreground, mProcId)) {
                Process.killChilds(foreground);
            }
        }, KILL_GRACE_MS);
    }

    /** The shell pid, which is also its process group id. */
    public int getProcessId() {
        return mProcId;
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
