/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
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

package com.thothterm.dock;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.thothterm.linux.AndroidNetworkResolver;
import com.thothterm.linux.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Supervises ThothDock, the Docker Engine API-compatible container engine,
 * in the app's own context beside Garden's PRoot -- never inside the guest,
 * where nested PRoot does not work. The guest reaches it through one
 * bind-mounted directory holding only its owner-only Unix socket.
 *
 * <p>Present only in QA integration builds that bundle {@code libthothdock.so}
 * and {@code libdocker.so}; without them every method is a no-op. ThothDock is
 * optional: the terminal never waits for it, and a failure only changes the
 * status shown in the notification.</p>
 */
public final class ThothDock {
    public enum Status { ABSENT, STOPPED, STARTING, RUNNING, UNAVAILABLE }

    public interface Listener {
        void onThothDockStatus(Status status);
    }

    static final String DAEMON = "libthothdock.so";
    static final String CLI = "libdocker.so";
    /** Where the guest sees the socket directory, and the CLI. */
    public static final String GUEST_SOCKET_DIR = "/run/thothdock";
    public static final String GUEST_DOCKER_HOST = "unix:///run/thothdock/thothdock.sock";
    public static final String GUEST_CLI = "/usr/local/bin/docker";

    /** Containers get this long to stop; the daemon adds 5 s of its own. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final long STOP_WAIT_MS = (SHUTDOWN_TIMEOUT_SECONDS + 8) * 1000L;
    private static final long READY_TIMEOUT_MS = 30_000;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_WINDOW_MS = 60_000;

    private static volatile ThothDock sInstance;

    private final File nativeLibDir;
    private final File root;
    private final File socketDir;
    private final File socket;
    private final File pidFile;
    private final File logFile;
    private final boolean bundled;

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private Status status;
    private boolean wanted;
    private boolean superviseRunning;
    private Process process;

    private ThothDock(Context context) {
        Context app = context.getApplicationContext();
        nativeLibDir = new File(app.getApplicationInfo().nativeLibraryDir);
        root = new File(app.getFilesDir(), "thothdock");
        socketDir = new File(root, "sock");
        socket = new File(socketDir, "thothdock.sock");
        pidFile = new File(root, "run/thothdock.pid");
        logFile = new File(root, "daemon.log");
        bundled = new File(nativeLibDir, DAEMON).isFile() && new File(nativeLibDir, CLI).isFile();
        status = bundled ? Status.STOPPED : Status.ABSENT;
    }

    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (ThothDock.class) {
                if (sInstance == null) sInstance = new ThothDock(context);
            }
        }
    }

    /** The instance, or null before {@link #init} (plain unit tests). */
    public static ThothDock getIfInitialized() {
        return sInstance;
    }

    public static ThothDock get() {
        ThothDock dock = sInstance;
        if (dock == null) throw new IllegalStateException("ThothDock not initialized");
        return dock;
    }

    public boolean isBundled() {
        return bundled;
    }

    public Status status() {
        synchronized (lock) {
            return status;
        }
    }

    /** "ThothDock: running" / "ThothDock: unavailable" for the notification. */
    public String statusLine() {
        switch (status()) {
            case RUNNING: return "ThothDock: running";
            case STARTING: return "ThothDock: starting";
            case STOPPED: return "ThothDock: stopped";
            default: return "ThothDock: unavailable";
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void setStatus(Status next) {
        synchronized (lock) {
            if (status == next) return;
            status = next;
        }
        ThothLog.i(LogCategory.RUNTIME, "ThothDock status=" + next);
        for (Listener l : listeners) l.onThothDockStatus(next);
    }

    /**
     * The host directory bound at {@link #GUEST_SOCKET_DIR}: it exists before
     * any PRoot session starts (PRoot drops a binding whose source is missing)
     * and holds nothing but the daemon's socket. Owner-only.
     */
    public String socketDirForGuest() {
        if (!bundled) return null;
        if (!socketDir.isDirectory() && !socketDir.mkdirs()) {
            ThothLog.w(LogCategory.RUNTIME, "Cannot create ThothDock socket directory");
            return null;
        }
        try {
            Os.chmod(socketDir.getAbsolutePath(), 0700);
            Os.chmod(root.getAbsolutePath(), 0700);
        } catch (ErrnoException e) {
            ThothLog.w(LogCategory.RUNTIME, "Cannot restrict ThothDock directories: " + e.getMessage());
            return null;
        }
        return socketDir.getAbsolutePath();
    }

    /** PRoot {@code --bind} arguments that expose the socket directory and the CLI to the guest. */
    public java.util.List<String> guestBinds() {
        String dir = socketDirForGuest();
        if (dir == null) return java.util.Collections.emptyList();
        return Arrays.asList("--bind=" + dir + ":" + GUEST_SOCKET_DIR,
                "--bind=" + cliPath() + ":" + GUEST_CLI);
    }

    /** The guest environment that points the stock Docker CLI at the socket. */
    public java.util.Map<String, String> guestEnvironment() {
        if (!bundled) return java.util.Collections.emptyMap();
        return java.util.Collections.singletonMap("DOCKER_HOST", GUEST_DOCKER_HOST);
    }

    /** The Docker CLI, bound read-only from the APK's library directory. */
    public String cliPath() {
        return bundled ? new File(nativeLibDir, CLI).getAbsolutePath() : null;
    }

    /**
     * Start the daemon unless it runs or is starting. Never blocks: the work
     * happens on the supervisor thread, which forks the daemon and stays
     * alive waiting for it -- the daemon's --exit-with-parent signal follows
     * the forking thread, so that thread must outlive the daemon.
     */
    public void ensureRunning() {
        if (!bundled) return;
        synchronized (lock) {
            wanted = true;
            if (superviseRunning) return;
            superviseRunning = true;
        }
        Thread t = new Thread(this::supervise, "ThothDock-supervisor");
        t.setDaemon(true);
        t.start();
    }

    private void supervise() {
        long windowStart = System.currentTimeMillis();
        int restarts = 0;
        try {
            while (true) {
                synchronized (lock) {
                    if (!wanted) break;
                }
                setStatus(Status.STARTING);
                Process p;
                try {
                    p = startDaemon();
                } catch (IOException e) {
                    ThothLog.e(LogCategory.RUNTIME, "ThothDock could not start: " + e.getMessage(), e);
                    setStatus(Status.UNAVAILABLE);
                    return;
                }
                if (waitReady(p)) {
                    setStatus(Status.RUNNING);
                } else if (p.isAlive()) {
                    ThothLog.w(LogCategory.RUNTIME, "ThothDock did not answer in time; stopping it");
                    killHard(p);
                }
                int code = waitExit(p);
                synchronized (lock) {
                    process = null;
                    if (!wanted) {
                        ThothLog.i(LogCategory.RUNTIME, "ThothDock stopped exit=" + code);
                        break;
                    }
                }
                ThothLog.w(LogCategory.RUNTIME, "ThothDock exited unexpectedly exit=" + code
                        + "; log: " + logFile.getName());
                removeStaleSocket();
                long now = System.currentTimeMillis();
                if (now - windowStart > RESTART_WINDOW_MS) {
                    windowStart = now;
                    restarts = 0;
                }
                if (++restarts > MAX_RESTARTS) {
                    ThothLog.e(LogCategory.RUNTIME, "ThothDock keeps failing; giving up until the next session");
                    setStatus(Status.UNAVAILABLE);
                    return;
                }
                sleep(1000L << (restarts - 1));
            }
            setStatus(Status.STOPPED);
        } finally {
            synchronized (lock) {
                superviseRunning = false;
                // ensureRunning() may have come between the loop's last check
                // and here; it saw a running supervisor and returned.
                if (wanted && status != Status.UNAVAILABLE) {
                    superviseRunning = true;
                    Thread t = new Thread(this::supervise, "ThothDock-supervisor");
                    t.setDaemon(true);
                    t.start();
                }
            }
        }
    }

    private Process startDaemon() throws IOException {
        killLeftover();
        if (socketDirForGuest() == null) throw new IOException("no socket directory");
        removeStaleSocket();
        RootfsManager rootfs = RootfsManager.get();
        File resolver = AndroidNetworkResolver.get().resolverFile();
        List<String> argv = Arrays.asList(
                new File(nativeLibDir, DAEMON).getAbsolutePath(), "serve",
                "--root", root.getAbsolutePath(),
                "--socket", socket.getAbsolutePath(),
                "--proot", rootfs.prootPath(),
                "--proot-loader", rootfs.loaderPath(),
                "--proot-lib-dir", rootfs.runtimeLibDir().getAbsolutePath(),
                "--resolv-conf", resolver.getAbsolutePath(),
                "--shutdown-timeout", String.valueOf(SHUTDOWN_TIMEOUT_SECONDS),
                "--exit-with-parent");
        rotateLog();
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.environment().clear();
        builder.environment().put("ANDROID_ROOT", "/system");
        builder.environment().put("ANDROID_DATA", "/data");
        builder.environment().put("PATH", "/system/bin");
        builder.environment().put("HOME", root.getAbsolutePath());
        builder.directory(root);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process p = builder.start();
        synchronized (lock) {
            process = p;
        }
        ThothLog.i(LogCategory.RUNTIME, "ThothDock started pid=" + pidOf(p));
        return p;
    }

    /** Keeps the previous run's log for diagnosis; never grows without bound. */
    private void rotateLog() {
        if (logFile.isFile()) {
            File old = new File(root, "daemon.log.1");
            old.delete();
            logFile.renameTo(old);
        }
    }

    /** True once the daemon answers /_ping on its socket. */
    private boolean waitReady(Process p) {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && p.isAlive()) {
            if (ping()) return true;
            sleep(200);
        }
        return false;
    }

    /** A plain HTTP /_ping over the Unix socket. */
    boolean ping() {
        if (!socket.exists()) return false;
        try (LocalSocket s = new LocalSocket()) {
            s.connect(new LocalSocketAddress(socket.getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM));
            s.setSoTimeout(2000);
            OutputStream out = s.getOutputStream();
            out.write("GET /_ping HTTP/1.0\r\nHost: thothdock\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[512];
            int n, total = 0;
            StringBuilder sb = new StringBuilder();
            while ((n = in.read(buf)) > 0 && total < 4096) {
                sb.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
                total += n;
            }
            return sb.toString().startsWith("HTTP/1.0 200") || sb.toString().startsWith("HTTP/1.1 200");
        } catch (IOException e) {
            return false;
        }
    }

    private static int waitExit(Process p) {
        while (true) {
            try {
                return p.waitFor();
            } catch (InterruptedException e) {
                // The supervisor is never interrupted on purpose; keep waiting.
            }
        }
    }

    /**
     * Ask the daemon to stop: SIGTERM now (non-blocking, safe on the main
     * thread), SIGKILL from a helper thread if it is still alive when its
     * shutdown budget is spent. Its containers stop with it.
     */
    public void stop() {
        if (!bundled) return;
        final Process p;
        synchronized (lock) {
            wanted = false;
            p = process;
        }
        if (p == null) return;
        signal(p, OsConstants.SIGTERM);
        Thread t = new Thread(() -> {
            try {
                if (!p.waitFor(STOP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    ThothLog.w(LogCategory.RUNTIME, "ThothDock ignored SIGTERM; SIGKILL");
                    killHard(p);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            removeStaleSocket();
        }, "ThothDock-stop");
        t.setDaemon(true);
        t.start();
    }

    /** The socket only exists while a daemon serves it. */
    private void removeStaleSocket() {
        synchronized (lock) {
            if (process != null && process.isAlive()) return;
        }
        try {
            if (socket.exists() || Files.isSymbolicLink(socket.toPath())) {
                Files.deleteIfExists(socket.toPath());
            }
        } catch (IOException e) {
            ThothLog.w(LogCategory.RUNTIME, "Cannot remove stale ThothDock socket: " + e.getMessage());
        }
    }

    /**
     * A daemon from an earlier app process (the system restarted the app)
     * still holds the data root's lock. It is found through its pid file and
     * killed only if /proc confirms it is ThothDock.
     */
    private void killLeftover() {
        int pid;
        try {
            pid = Integer.parseInt(new String(Files.readAllBytes(pidFile.toPath()), StandardCharsets.US_ASCII).trim());
        } catch (IOException | NumberFormatException e) {
            return;
        }
        if (pid <= 1 || !isThothDock(pid)) return;
        ThothLog.w(LogCategory.RUNTIME, "Stopping a leftover ThothDock pid=" + pid);
        try {
            Os.kill(pid, OsConstants.SIGKILL);
        } catch (ErrnoException e) {
            return;
        }
        for (int i = 0; i < 30 && isThothDock(pid); i++) sleep(100);
    }

    private boolean isThothDock(int pid) {
        try {
            byte[] cmd = Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath());
            String first = new String(cmd, StandardCharsets.UTF_8);
            int nul = first.indexOf('\0');
            if (nul >= 0) first = first.substring(0, nul);
            return first.equals(new File(nativeLibDir, DAEMON).getAbsolutePath())
                    || first.endsWith("/" + DAEMON);
        } catch (IOException e) {
            return false;
        }
    }

    private static int pidOf(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(p);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static void signal(Process p, int sig) {
        int pid = pidOf(p);
        if (pid <= 0) {
            p.destroy();
            return;
        }
        try {
            Os.kill(pid, sig);
        } catch (ErrnoException ignored) {
            // Already gone.
        }
    }

    /** destroyForcibly() is only SIGTERM on Android; the kill is explicit. */
    private static void killHard(Process p) {
        signal(p, OsConstants.SIGKILL);
        try {
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
