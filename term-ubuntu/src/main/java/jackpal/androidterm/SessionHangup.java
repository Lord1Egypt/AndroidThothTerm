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

package jackpal.androidterm;

import android.os.Handler;
import android.os.Looper;

import com.thothterm.Process;

import java.util.function.BooleanSupplier;

/**
 * Hanging up one terminal: what closing a phone window and closing a LAN
 * browser terminal both do to the PRoot tree behind it.
 */
public final class SessionHangup {
    /** How long a closed terminal's processes get to honour SIGHUP before SIGKILL. */
    static final long KILL_GRACE_MS = 400;

    /** android.os.Process has no named constant for SIGHUP. */
    private static final int SIGNAL_HUP = 1;

    private SessionHangup() {
    }

    /**
     * Hang up the session led by {@code leader} (the proot pid), then clean up
     * after a grace period. {@code exited} reports whether the leader has been
     * reaped.
     */
    public static void hangUp(int leader, BooleanSupplier exited) {
        Process.finishChilds(leader);
        // Hang up the whole session, as a real terminal hangup does: job
        // control gives the foreground job and every background job a process
        // group of their own, so the shell's group alone misses them. A
        // background job that ignores SIGHUP (nohup) keeps running, as
        // intended.
        for (int pid : SessionProcesses.members(leader)) {
            android.os.Process.sendSignal(pid, SIGNAL_HUP);
        }

        // Backstop for whatever survived the hangup without having chosen to
        // ignore it: a shell or job that traps SIGHUP, or a stopped job. The
        // session leader here is proot, which ignores SIGHUP and has to stay
        // while it still traces a nohup'd job -- killing it would take that
        // job down (PTRACE_O_EXITKILL) -- so it is left to exit by itself
        // once its last tracee has. Skipped once proot is reaped: its tracees
        // are gone with it, and its pid may already belong to someone else.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (exited.getAsBoolean()) return;
            for (int pid : SessionProcesses.members(leader)) {
                if (pid != leader && !SessionProcesses.ignoresHangup(pid)) {
                    android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL);
                }
            }
        }, KILL_GRACE_MS);
    }
}
