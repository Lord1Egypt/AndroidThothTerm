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

import com.thothterm.linux.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs ThothDock's Engine Guard in a Debian-family guest: empty
 * placeholder packages for the stock Docker Engine ({@code docker.io},
 * {@code containerd}, {@code runc} ...) plus an apt pin and hook, so that
 * {@code apt upgrade} and {@code apt install docker.io} can never put a real
 * {@code dockerd} where ThothDock is the engine. The Docker CLI is not
 * restricted. The packages are built by ThothDock's {@code engine-guard/}
 * and bundled as assets; their SHA-256 is checked before they enter the guest.
 *
 * <p>It is an optional capability: it starts only after the daemon is ready
 * and the guest is healthy, runs on its own thread, and any failure is logged
 * and retried at the next daemon start. The terminal never waits for it.</p>
 */
final class EngineGuard {
    static final String[] PROTECTED = {
            "docker.io", "docker-ce", "docker-engine", "moby-engine", "containerd", "containerd.io", "runc",
    };
    static final String GUARD_PACKAGE = "thothdock-engine-guard";
    private static final String ASSET_DIR = "thothdock/engine-guard";
    private static final String CACHE_DIR = "var/cache/thothdock-guard";
    private static final long READY_WAIT_MS = 15 * 60_000L;
    private static final int ATTEMPTS = 3;
    private static final long RETRY_MS = 60_000L;

    private static final AtomicBoolean running = new AtomicBoolean();

    private EngineGuard() {
    }

    static void scheduleEnsure() {
        if (!running.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                ensure();
            } catch (Throwable e) {
                ThothLog.e(LogCategory.RUNTIME, "Engine Guard failed: " + e, e);
            } finally {
                running.set(false);
            }
        }, "ThothDock-engine-guard");
        t.setDaemon(true);
        t.start();
    }

    private static void ensure() throws IOException, InterruptedException {
        ThothDock dock = ThothDock.getIfInitialized();
        if (dock == null || !dock.isBundled()) return;
        Context ctx = dock.context();
        if (dock.guardVersion().isEmpty()) {
            ThothLog.i(LogCategory.RUNTIME, "Engine Guard: not part of this build");
            return;
        }
        Map<String, String> sums = readSums(ctx);
        if (sums == null) {
            ThothLog.i(LogCategory.RUNTIME, "Engine Guard: no packages bundled");
            return;
        }
        RootfsManager rootfs = RootfsManager.get();
        long deadline = System.currentTimeMillis() + READY_WAIT_MS;
        while (!rootfs.isReady()) {
            if (System.currentTimeMillis() > deadline) {
                ThothLog.w(LogCategory.RUNTIME, "Engine Guard: the guest was not ready in time; will retry at the next start");
                return;
            }
            Thread.sleep(5000);
        }
        File status = new File(rootfs.rootfsDir(), "var/lib/dpkg/status");
        if (!status.isFile()) {
            ThothLog.i(LogCategory.RUNTIME, "Engine Guard: the guest has no dpkg; not applicable");
            return;
        }
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (guardInstalled(status, dock.guardVersion())) {
                ThothLog.i(LogCategory.RUNTIME, "Engine Guard: protected");
                return;
            }
            try {
                install(ctx, rootfs, sums);
            } catch (IOException e) {
                // Most likely the user is running apt (dpkg lock): try again.
                ThothLog.w(LogCategory.RUNTIME, "Engine Guard install attempt " + attempt + " failed: " + e.getMessage());
                removeCache(rootfs);
            }
            if (guardInstalled(status, dock.guardVersion())) {
                ThothLog.i(LogCategory.RUNTIME, "Engine Guard: installed");
                return;
            }
            Thread.sleep(RETRY_MS);
        }
        ThothLog.w(LogCategory.RUNTIME, "Engine Guard: not installed; will retry at the next start");
    }

    private static void install(Context ctx, RootfsManager rootfs, Map<String, String> sums) throws IOException {
        File cache = new File(rootfs.rootfsDir(), CACHE_DIR);
        removeCache(rootfs);
        if (!cache.mkdirs() && !cache.isDirectory()) throw new IOException("cannot create " + cache);
        for (Map.Entry<String, String> e : sums.entrySet()) {
            String name = e.getKey();
            if (!name.endsWith(".deb") || name.contains("/") || name.contains("..")) {
                throw new IOException("unexpected bundled file name " + name);
            }
            byte[] data = readAsset(ctx, ASSET_DIR + "/" + name);
            if (!sha256(data).equalsIgnoreCase(e.getValue())) {
                throw new IOException("bundled package " + name + " does not match its SHA-256");
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(cache, name))) {
                out.write(data);
            }
        }
        // Two passes: with a real engine already installed, the placeholders
        // cannot all land in one dpkg run (docker-ce conflicts with the real
        // docker.io until that has been replaced); the second pass finishes.
        String script = "export DEBIAN_FRONTEND=noninteractive; cd /" + CACHE_DIR
                + " && { dpkg -i ./*.deb || dpkg -i ./*.deb; }; rc=$?; cd /; rm -rf /" + CACHE_DIR + "; exit $rc";
        String report = rootfs.runGuestAdmin(script);
        ThothLog.i(LogCategory.RUNTIME, "Engine Guard dpkg: " + report.replace('\n', ' ').trim());
    }

    private static void removeCache(RootfsManager rootfs) {
        File cache = new File(rootfs.rootfsDir(), CACHE_DIR);
        File[] files = cache.listFiles();
        if (files != null) for (File f : files) f.delete();
        cache.delete();
    }

    /** True when every protected placeholder and the guard package are installed at the bundled version. */
    static boolean guardInstalled(File dpkgStatus, String version) {
        Map<String, String> installed = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(dpkgStatus), StandardCharsets.UTF_8))) {
            String name = null, ver = null;
            boolean ok = false;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    if (name != null && ok && ver != null) installed.put(name, ver);
                    name = ver = null;
                    ok = false;
                } else if (line.startsWith("Package: ")) {
                    name = line.substring(9);
                } else if (line.startsWith("Version: ")) {
                    ver = line.substring(9);
                } else if (line.startsWith("Status: ")) {
                    ok = line.endsWith(" installed");
                }
            }
            if (name != null && ok && ver != null) installed.put(name, ver);
        } catch (IOException e) {
            return false;
        }
        for (String p : PROTECTED) {
            if (!version.equals(installed.get(p))) return false;
        }
        String guardVersion = version.substring(version.indexOf(':') + 1);
        return guardVersion.equals(installed.get(GUARD_PACKAGE));
    }

    private static Map<String, String> readSums(Context ctx) {
        try {
            String text = new String(readAsset(ctx, ASSET_DIR + "/SHA256SUMS"), StandardCharsets.UTF_8);
            Map<String, String> sums = new java.util.LinkedHashMap<>();
            for (String line : text.split("\n")) {
                String[] f = line.trim().split("\\s+");
                if (f.length == 2) sums.put(f[1], f[0]);
            }
            return sums.isEmpty() ? null : sums;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAsset(Context ctx, String path) throws IOException {
        try (InputStream in = ctx.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] data) throws IOException {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
