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
 * <p>Active only in editions that bundle {@code libthothdock.so} and
 * {@code libdocker.so} (ThothTerm Trixie 0.3.0 and later); without them every
 * method is a no-op. ThothDock is
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
    /** Intent extra: text the Containers screen wants typed into the current terminal window. */
    public static final String EXTRA_TERMINAL_INPUT = "com.thothterm.dock.TERMINAL_INPUT";

    /** The Containers screen (compiled only into builds that carry the ThothDock UI overlay). */
    public static final String CONTAINERS_ACTIVITY = "com.thothterm.dock.ContainersActivity";

    /** The Web Panel page (same overlay as the Containers screen). */
    /** Intent extra naming the tab the management screen opens on (one of the TAB_ values). */
    public static final String EXTRA_TAB = "com.thothterm.dock.extra.TAB";
    public static final String TAB_CONTAINERS = "containers";
    public static final String TAB_IMAGES = "images";
    public static final String TAB_VOLUMES = "volumes";
    public static final String TAB_PANEL = "panel";

    /** ThothDock's own binary inside the guest, for `thothdock doctor --guard`. */
    public static final String GUEST_TOOL = "/usr/local/bin/thothdock";

    /** Containers get this long to stop; the daemon adds 5 s of its own. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final long STOP_WAIT_MS = (SHUTDOWN_TIMEOUT_SECONDS + 8) * 1000L;
    private static final long READY_TIMEOUT_MS = 30_000;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_WINDOW_MS = 60_000;

    private static volatile ThothDock sInstance;

    private final Context appContext;
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
    /** Start time (see {@link #startTimeOf}) of {@link #process}: its identity, which a reused pid does not share. */
    private long processStart;

    private ThothDock(Context context) {
        Context app = context.getApplicationContext();
        appContext = app;
        nativeLibDir = new File(app.getApplicationInfo().nativeLibraryDir);
        root = new File(app.getFilesDir(), "thothdock");
        socketDir = new File(root, "sock");
        socket = new File(socketDir, "thothdock.sock");
        pidFile = new File(root, "run/thothdock.pid");
        logFile = new File(root, "daemon.log");
        bundled = new File(nativeLibDir, DAEMON).isFile() && new File(nativeLibDir, CLI).isFile();
        status = bundled ? Status.STOPPED : Status.ABSENT;
        if (bundled) cleanupStale();
    }

    /**
     * Runs once per app process, before any terminal session can look at the
     * socket: if no ThothDock daemon of ours is alive, its socket and pid file
     * are leftovers of a crash or force-stop and are removed, so the guest
     * (and the banner) never see a stale socket. A live daemon is left alone
     * here; {@link #startDaemon} replaces it. Nothing is signalled.
     */
    private void cleanupStale() {
        long[] rec = readPidRecord();
        if (rec != null && isOurDaemon((int) rec[0], rec[1])) return;
        removeSocketPath();
        pidFile.delete();
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

    Context context() {
        return appContext;
    }

    /**
     * The Engine Guard placeholder version bundled with this build
     * ("9999:1.0+thothdock.N"), from the staged components file.
     */
    String guardVersion() {
        try (java.io.InputStream in = appContext.getAssets().open("thothdock/components.properties")) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            String v = p.getProperty("guardVersion");
            return v == null ? "" : v;
        } catch (IOException e) {
            return "";
        }
    }

    /** The version of the {@code thothdock-engine-guard} package bundled with this build, or "". */
    String guardPackageVersion() {
        try (java.io.InputStream in = appContext.getAssets().open("thothdock/components.properties")) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            String v = p.getProperty("guardPackageVersion");
            return v == null ? "" : v;
        } catch (IOException e) {
            return "";
        }
    }

    /** True when this build carries the Containers screen. */
    public boolean hasContainersUi() {
        try {
            appContext.getPackageManager().getActivityInfo(
                    new android.content.ComponentName(appContext.getPackageName(), CONTAINERS_ACTIVITY), 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The API socket on the host (app-private). */
    public String socketPath() {
        return socket.getAbsolutePath();
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
                "--bind=" + cliPath() + ":" + GUEST_CLI,
                "--bind=" + new File(nativeLibDir, DAEMON).getAbsolutePath() + ":" + GUEST_TOOL);
    }

    /** The guest environment that points the stock Docker CLI at the socket. */
    public java.util.Map<String, String> guestEnvironment() {
        if (!bundled) return java.util.Collections.emptyMap();
        return java.util.Collections.singletonMap("DOCKER_HOST", GUEST_DOCKER_HOST);
    }

    /** The engine binary in the APK's library directory (also the Web Panel). */
    File daemonBinary() {
        return new File(nativeLibDir, DAEMON);
    }

    /** The engine's data root. */
    File dataRoot() {
        return root;
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
                    EngineGuard.scheduleEnsure();
                } else if (p.isAlive()) {
                    ThothLog.w(LogCategory.RUNTIME, "ThothDock did not answer in time; stopping it");
                    killHard(p, startOf(p));
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
        long start = startTimeOf(pidOf(p));
        synchronized (lock) {
            process = p;
            processStart = start;
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
        WebPanel panel = WebPanel.getIfCreated();
        if (panel != null && panel.isRunning()) {
            Thread pt = new Thread(panel::stop, "ThothDock-panel-stop");
            pt.setDaemon(true);
            pt.start();
        }
        final Process p;
        final long start;
        synchronized (lock) {
            wanted = false;
            p = process;
            start = processStart;
        }
        if (p == null) return;
        signal(p, start, OsConstants.SIGTERM);
        Thread t = new Thread(() -> {
            try {
                if (!p.waitFor(STOP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    ThothLog.w(LogCategory.RUNTIME, "ThothDock ignored SIGTERM; SIGKILL");
                    killHard(p, start);
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
        removeSocketPath();
    }

    /** Unlinks the socket path (a socket, a file or a planted symlink) without following it. */
    private void removeSocketPath() {
        try {
            if (Files.exists(socket.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(socket.toPath());
            }
        } catch (IOException e) {
            ThothLog.w(LogCategory.RUNTIME, "Cannot remove stale ThothDock socket: " + e.getMessage());
        }
    }

    /**
     * A daemon from an earlier app process (the system restarted the app)
     * still holds the data root's lock. It is found through its pid file --
     * "pid start-time" -- and killed only if /proc confirms that exact
     * process: same start time, and argv[0] is our libthothdock.so. A reused
     * pid never matches, so an unrelated process is never signalled.
     */
    private void killLeftover() {
        long[] rec = readPidRecord();
        if (rec == null) return;
        int pid = (int) rec[0];
        if (!isOurDaemon(pid, rec[1])) return;
        ThothLog.w(LogCategory.RUNTIME, "Stopping a leftover ThothDock pid=" + pid);
        try {
            Os.kill(pid, OsConstants.SIGKILL);
        } catch (ErrnoException e) {
            return;
        }
        for (int i = 0; i < 30 && isOurDaemon(pid, rec[1]); i++) sleep(100);
    }

    /** {pid, start time} from the pid file, or null if absent or malformed. */
    private long[] readPidRecord() {
        try {
            String[] f = new String(Files.readAllBytes(pidFile.toPath()), StandardCharsets.US_ASCII).trim().split("\\s+");
            if (f.length != 2) return null;
            long pid = Long.parseLong(f[0]);
            long start = Long.parseLong(f[1]);
            if (pid <= 1 || pid > Integer.MAX_VALUE || start <= 0) return null;
            return new long[]{pid, start};
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    /** True only for the live process that started at {@code start} and runs our daemon binary. */
    private boolean isOurDaemon(int pid, long start) {
        if (startTimeOf(pid) != start) return false;
        try {
            byte[] cmd = Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath());
            String first = new String(cmd, StandardCharsets.UTF_8);
            int nul = first.indexOf('\0');
            if (nul >= 0) first = first.substring(0, nul);
            return first.equals(new File(nativeLibDir, DAEMON).getAbsolutePath());
        } catch (IOException e) {
            return false;
        }
    }

    /** Field 22 of /proc/pid/stat (clock ticks after boot), or 0 if the process does not exist. */
    static long startTimeOf(int pid) {
        try {
            String stat = new String(Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), StandardCharsets.UTF_8);
            int close = stat.lastIndexOf(')');
            if (close < 0) return 0;
            String[] f = stat.substring(close + 1).trim().split("\\s+");
            return f.length > 19 ? Long.parseLong(f[19]) : 0;
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    static int pidOf(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(p);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** The identity recorded for {@code p}, or 0 when it is no longer the current daemon. */
    private long startOf(Process p) {
        synchronized (lock) {
            return process == p ? processStart : 0;
        }
    }

    /**
     * Signals the daemon only while it is still the process that started at
     * {@code start}. The child is reaped by the runtime as soon as it exits and
     * its pid can then be handed to an unrelated process, so a bare kill(pid)
     * could hit a stranger; a pid that no longer has the recorded start time is
     * not signalled. SIGTERM goes through {@link Process#destroy}, which the
     * runtime serialises with its own reaping.
     */
    private static void signal(Process p, long start, int sig) {
        int pid = pidOf(p);
        if (sig == OsConstants.SIGTERM || pid <= 0) {
            p.destroy();
            return;
        }
        if (!sameProcess(pid, start)) return;
        try {
            Os.kill(pid, sig);
        } catch (ErrnoException ignored) {
            // Already gone.
        }
    }

    /** True only for the live process {@code pid} that started at {@code start}. */
    static boolean sameProcess(int pid, long start) {
        return pid > 0 && start > 0 && startTimeOf(pid) == start;
    }

    /** destroyForcibly() is only SIGTERM on Android; the kill is explicit. */
    private static void killHard(Process p, long start) {
        signal(p, start, OsConstants.SIGKILL);
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
