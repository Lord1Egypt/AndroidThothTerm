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
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The ThothDock Web Panel process ({@code libthothdock.so panel}, ThothDock
 * docs/nextgen/adr/ADR-0005). Off until the owner starts it; it is a child of
 * the app with --exit-with-parent, so it never outlives the app, and stopping
 * it frees its listener and sessions in memory. It reaches the engine only
 * through the engine's Unix socket.
 */
public final class WebPanel {
    public static final int PORT = 7690;

    /** What the owner needs to pair a browser. */
    public static final class Info {
        public final List<String> urls;
        public final String fingerprint;
        public final String code;
        public final String expires;

        Info(List<String> urls, String fingerprint, String code, String expires) {
            this.urls = urls;
            this.fingerprint = fingerprint;
            this.code = code;
            this.expires = expires;
        }
    }

    private static volatile WebPanel sInstance;

    private final File daemon;
    private final File root;
    private final File pairingFile;
    private final File logFile;
    private final Object lock = new Object();
    private Process process;
    private long processStart;
    private String listenHost;

    private WebPanel(ThothDock dock) {
        daemon = dock.daemonBinary();
        root = dock.dataRoot();
        pairingFile = new File(root, "panel/pairing.json");
        logFile = new File(root, "panel.log");
    }

    public static WebPanel get() {
        WebPanel p = sInstance;
        if (p == null) {
            synchronized (WebPanel.class) {
                if (sInstance == null) sInstance = new WebPanel(ThothDock.get());
                p = sInstance;
            }
        }
        return p;
    }

    /**
     * The IPv4 address of the current Wi-Fi or Ethernet network, or null. A
     * cellular network is never offered: the panel is for a trusted LAN.
     */
    public static String lanAddress(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
            return null;
        }
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress() instanceof Inet4Address && !a.getAddress().isLoopbackAddress()) {
                return a.getAddress().getHostAddress();
            }
        }
        return null;
    }

    public boolean isRunning() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    /** The address the panel listens on while it runs. */
    public String listenHost() {
        synchronized (lock) {
            return process != null && process.isAlive() ? listenHost : null;
        }
    }

    /**
     * Starts the panel on {@code host} (127.0.0.1, or the LAN address). The
     * fork happens on a thread that then waits for the panel: the kernel's
     * --exit-with-parent signal (PR_SET_PDEATHSIG) follows the forking
     * thread, not the app process, so that thread must outlive the panel.
     */
    public void start(String host) throws IOException {
        stop();
        pairingFile.delete();
        List<String> argv = Arrays.asList(daemon.getAbsolutePath(), "panel",
                "--root", root.getAbsolutePath(),
                "--listen", host + ":" + PORT,
                "--exit-with-parent");
        ProcessBuilder b = new ProcessBuilder(argv);
        b.environment().clear();
        b.environment().put("ANDROID_ROOT", "/system");
        b.environment().put("ANDROID_DATA", "/data");
        b.environment().put("PATH", "/system/bin");
        b.environment().put("HOME", root.getAbsolutePath());
        b.directory(root);
        b.redirectErrorStream(true);
        b.redirectOutput(ProcessBuilder.Redirect.to(logFile));
        b.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        final IOException[] failure = new IOException[1];
        final CountDownLatch forked = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            Process p;
            try {
                p = b.start();
            } catch (IOException e) {
                failure[0] = e;
                forked.countDown();
                return;
            }
            synchronized (lock) {
                process = p;
                processStart = ThothDock.startTimeOf(ThothDock.pidOf(p));
                listenHost = host;
            }
            forked.countDown();
            ThothLog.i(LogCategory.RUNTIME, "Web Panel started on " + host + ":" + PORT);
            while (true) {
                try {
                    p.waitFor();
                    break;
                } catch (InterruptedException ignored) {
                    // Only the panel's exit ends this thread.
                }
            }
            synchronized (lock) {
                if (process == p) {
                    process = null;
                    listenHost = null;
                }
            }
            ThothLog.i(LogCategory.RUNTIME, "Web Panel exited");
        }, "ThothDock-panel");
        t.setDaemon(true);
        t.start();
        try {
            if (!forked.await(10, TimeUnit.SECONDS)) throw new IOException("the panel did not start");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (failure[0] != null) throw failure[0];
    }

    /** The pairing details, once the panel has written them (null before). */
    public Info info() {
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(pairingFile.toPath()), StandardCharsets.UTF_8));
            JSONArray u = o.optJSONArray("urls");
            List<String> urls = new ArrayList<>();
            for (int i = 0; u != null && i < u.length(); i++) urls.add(u.getString(i));
            return new Info(urls, o.optString("fingerprint"), o.optString("code"), o.optString("expires"));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Asks the running panel for a new pairing code (SIGUSR1). */
    public void newCode() {
        int pid;
        long start;
        synchronized (lock) {
            pid = process == null ? -1 : ThothDock.pidOf(process);
            start = processStart;
        }
        if (!ThothDock.sameProcess(pid, start)) return;
        pairingFile.delete();
        try {
            Os.kill(pid, OsConstants.SIGUSR1);
        } catch (ErrnoException ignored) {
            // Gone already.
        }
    }

    /** Stops the panel: SIGTERM, then SIGKILL if it does not leave within 3 s. */
    public void stop() {
        Process p;
        long start;
        synchronized (lock) {
            p = process;
            start = processStart;
        }
        if (p == null) return;
        p.destroy();
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                int pid = ThothDock.pidOf(p);
                if (ThothDock.sameProcess(pid, start)) Os.kill(pid, OsConstants.SIGKILL);
                p.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ErrnoException ignored) {
            // Gone already.
        }
        synchronized (lock) {
            if (process == p) {
                process = null;
                listenHost = null;
            }
        }
        pairingFile.delete();
        ThothLog.i(LogCategory.RUNTIME, "Web Panel stopped");
    }

    /** The panel if this process ever created it, for shutdown paths. */
    static WebPanel getIfCreated() {
        return sInstance;
    }

    /** Ends every paired browser session; the panel is restarted if it ran. */
    public void revokeAll() throws IOException {
        String host = listenHost();
        stop();
        Process p = new ProcessBuilder(daemon.getAbsolutePath(), "panel", "--root", root.getAbsolutePath(), "--revoke-all")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logFile)).start();
        try {
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (host != null) start(host);
    }
}
