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

import com.thothterm.Application;
import com.thothterm.Process;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import jackpal.androidterm.util.TermSettings;


/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys it's process group
 * upon stopping.
 */
public class ShellTermSession extends GenericTermSession {
    private static final int PROCESS_EXITED = 1;

    private final String mInitialCommand;
    private final int mProcId;
    private final Thread mWatcherThread;


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

    protected int createShellProcess(TermSettings settings) throws IOException {
        ArrayList<String> argList;
        String arg0;
        String[] args;

        try {
            argList = buildArgv(settings);
            arg0 = argList.get(0);
            File file = new File(arg0);
            if (!file.exists()) {
                ThothLog.e(LogCategory.SHELL,
                        "Shell executable not found: " + new File(arg0).getName());
                throw new FileNotFoundException(arg0);
            } else if (!file.canExecute()) {
                ThothLog.e(LogCategory.SHELL,
                        "Shell executable not executable: " + new File(arg0).getName());
                throw new FileNotFoundException(arg0);
            }
            args = argList.toArray(new String[0]);
        } catch (Exception e) {
            argList = parse(settings.getFailsafeShell());
            arg0 = argList.get(0);
            args = argList.toArray(new String[0]);
        }
        ThothLog.d(LogCategory.SHELL, "Shell executable name=" + new File(arg0).getName());

        Map<String, String> map = buildEnvironment(settings);

        String[] env = new String[map.size()];
        int k = 0;
        for (Map.Entry<String, String> entry : map.entrySet())
            env[k++] = entry.getKey() + "=" + entry.getValue();

        return Process.createSubprocess(mTermFd, arg0, args, env);
    }

    protected ArrayList<String> buildArgv(TermSettings settings) {
        return parse(settings.getShell());
    }

    protected Map<String, String> buildEnvironment(TermSettings settings) {
        Map<String, String> map = new HashMap<>(System.getenv());
        map.put("TERM", settings.getTermType());
        map.put("PATH", Application.buildPATH());
        map.put("HOME", settings.getHomePath());
        map.put("TMPDIR", Application.getTmpPath());
        map.put("ENV", Application.getScriptFilePath());
        return map;
    }

    private ArrayList<String> parse(String cmd) {
        final int PLAIN = 0;
        final int WHITESPACE = 1;
        final int INQUOTE = 2;
        int state = WHITESPACE;
        ArrayList<String> result = new ArrayList<>();
        int cmdLen = cmd.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cmdLen; i++) {
            char c = cmd.charAt(i);
            if (state == PLAIN) {
                if (Character.isWhitespace(c)) {
                    result.add(builder.toString());
                    builder.delete(0, builder.length());
                    state = WHITESPACE;
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    builder.append(c);
                }
            } else if (state == WHITESPACE) {
                if (Character.isWhitespace(c)) {
                    // do nothing
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    state = PLAIN;
                    builder.append(c);
                }
            } else if (state == INQUOTE) {
                if (c == '\\') {
                    if (i + 1 < cmdLen) {
                        i += 1;
                        builder.append(cmd.charAt(i));
                    }
                } else if (c == '"') {
                    state = PLAIN;
                } else {
                    builder.append(c);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }

    private void onProcessExit(int result) {
        if (result == 0)
            ThothLog.i(LogCategory.SHELL, "Shell exited code=0");
        else
            ThothLog.w(LogCategory.SHELL, "Shell exited code=" + result);
        onProcessExit();
    }

    @Override
    public void finish() {
        Process.finishChilds(mProcId);
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
