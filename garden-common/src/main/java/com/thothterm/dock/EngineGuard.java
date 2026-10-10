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
 * ThothDock's Engine Guard for a Debian-family guest: empty placeholder
 * packages for the stock Docker Engine ({@code docker.io}, {@code containerd},
 * {@code runc} ...) plus an apt pin and hook, so that {@code apt upgrade} and
 * {@code apt install docker.io} can never put a real {@code dockerd} where
 * ThothDock is the engine. The Docker CLI is not restricted. The packages are
 * built by ThothDock's {@code engine-guard/} and bundled as assets; their
 * SHA-256 is checked before anything enters the guest.
 *
 * <p>Two parts, with different timing:</p>
 * <ul>
 * <li><b>Enforcement</b> -- the hook, its apt configuration and the pin -- is
 * a required part of the guest being ready. {@link #enforcement} reads those
 * three files out of the verified guard package and {@code RootfsManager}
 * writes them, each by atomic rename, hook first, in every step that makes the
 * guest usable (a fresh install, a repair, and the start of every terminal
 * window), before any guest process can run apt. It is a few small file writes,
 * so it adds nothing noticeable to terminal start, and it needs neither the
 * daemon, dpkg nor the network. With it in place the stock engine cannot be
 * installed: the hook refuses any protected package that is not the ThothDock
 * placeholder.</li>
 * <li><b>Registration</b> -- installing the placeholders and the guard package
 * with dpkg, so that dpkg and apt list them as installed -- runs on a
 * background thread once the daemon is ready. It needs the dpkg lock and a
 * guest process, so it never blocks a terminal; a failure is logged and retried
 * at the next daemon start. Until it finishes, protection is already complete.</li>
 * </ul>
 */
public final class EngineGuard {
    static final String[] PROTECTED = {
            "docker.io", "docker-ce", "docker-engine", "moby-engine", "containerd", "containerd.io", "runc",
    };
    static final String GUARD_PACKAGE = "thothdock-engine-guard";
    private static final String ASSET_DIR = "thothdock/engine-guard";
    private static final String CACHE_DIR = "var/cache/thothdock-guard";
    private static final long READY_WAIT_MS = 15 * 60_000L;
    private static final int ATTEMPTS = 3;
    private static final long RETRY_MS = 60_000L;

    /** Guest paths of the enforcement files, in the order they must be written: the hook is referenced by the apt configuration. */
    static final String HOOK_PATH = "usr/lib/thothdock/engine-guard-hook";
    static final String APT_CONF_PATH = "etc/apt/apt.conf.d/99thothdock-engine-guard";
    static final String PIN_PATH = "etc/apt/preferences.d/thothdock-engine-guard";

    private static final AtomicBoolean running = new AtomicBoolean();
    private static volatile Enforcement cachedEnforcement;

    private EngineGuard() {
    }

    /** The enforcement files, exactly as the guard package ships them. */
    public static final class Enforcement {
        public final String hook;
        public final String aptConf;
        public final String pin;

        public Enforcement(String hook, String aptConf, String pin) {
            this.hook = hook;
            this.aptConf = aptConf;
            this.pin = pin;
        }
    }

    /**
     * The enforcement files from the bundled, hash-verified guard package, or
     * null when this build carries no Engine Guard. A bundled package that does
     * not verify is an error: the guest must not become ready without a guard
     * this build was meant to have.
     */
    public static Enforcement enforcement(Context ctx) throws IOException {
        Enforcement e = cachedEnforcement;
        if (e != null) return e;
        ThothDock dock = ThothDock.getIfInitialized();
        if (dock == null || !dock.isBundled() || dock.guardVersion().isEmpty()) return null;
        Map<String, String> sums = readSums(ctx);
        if (sums == null) return null;
        String guardDeb = null;
        for (String name : sums.keySet()) {
            if (name.startsWith(GUARD_PACKAGE + "_") && name.endsWith(".deb")) guardDeb = name;
        }
        if (guardDeb == null) throw new IOException("the Engine Guard package is missing from SHA256SUMS");
        byte[] deb = readAsset(ctx, ASSET_DIR + "/" + guardDeb);
        if (!sha256(deb).equalsIgnoreCase(sums.get(guardDeb))) {
            throw new IOException("bundled package " + guardDeb + " does not match its SHA-256");
        }
        Map<String, byte[]> files = DebData.files(deb, HOOK_PATH, APT_CONF_PATH, PIN_PATH);
        e = new Enforcement(utf8(files.get(HOOK_PATH)), utf8(files.get(APT_CONF_PATH)), utf8(files.get(PIN_PATH)));
        cachedEnforcement = e;
        return e;
    }

    private static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
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
            if (guardInstalled(status, dock.guardVersion(), dock.guardPackageVersion())) {
                ThothLog.i(LogCategory.RUNTIME, "Engine Guard: protected");
                ComposeSetup.ensure(rootfs);
                return;
            }
            try {
                install(ctx, rootfs, sums);
            } catch (IOException e) {
                // Most likely the user is running apt (dpkg lock): try again.
                ThothLog.w(LogCategory.RUNTIME, "Engine Guard install attempt " + attempt + " failed: " + e.getMessage());
                removeCache(rootfs);
            }
            if (guardInstalled(status, dock.guardVersion(), dock.guardPackageVersion())) {
                ThothLog.i(LogCategory.RUNTIME, "Engine Guard: installed");
                ComposeSetup.ensure(rootfs);
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

    /**
     * True when every protected placeholder is installed at {@code version}
     * ("9999:1.0+thothdock.N") and the guard package at {@code guardVersion}.
     */
    static boolean guardInstalled(File dpkgStatus, String version, String guardVersion) {
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

    /**
     * Reads named regular files out of a {@code .deb}: an ar archive whose
     * {@code data.tar} member is an uncompressed ustar archive, as the guard
     * package is built (dpkg-deb -Znone). Anything else is refused.
     */
    static final class DebData {
        private DebData() {
        }

        static Map<String, byte[]> files(byte[] deb, String... wanted) throws IOException {
            byte[] tar = dataTar(deb);
            Map<String, byte[]> found = new HashMap<>();
            int pos = 0;
            while (pos + 512 <= tar.length) {
                if (tar[pos] == 0) break; // end of archive
                String name = field(tar, pos, 100);
                String prefix = field(tar, pos + 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = octal(tar, pos + 124, 12);
                char type = (char) tar[pos + 156];
                pos += 512;
                if (size < 0 || size > tar.length - pos) throw new IOException("corrupt data.tar");
                if (type == '0' || type == 0) {
                    String clean = name.startsWith("./") ? name.substring(2) : name;
                    for (String w : wanted) {
                        if (w.equals(clean)) found.put(w, java.util.Arrays.copyOfRange(tar, pos, pos + (int) size));
                    }
                }
                pos += (int) ((size + 511) / 512 * 512);
            }
            for (String w : wanted) {
                if (!found.containsKey(w)) throw new IOException("the guard package does not contain " + w);
            }
            return found;
        }

        private static byte[] dataTar(byte[] deb) throws IOException {
            byte[] magic = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
            if (deb.length < 8 || !java.util.Arrays.equals(java.util.Arrays.copyOf(deb, 8), magic)) {
                throw new IOException("not an ar archive");
            }
            int pos = 8;
            while (pos + 60 <= deb.length) {
                String name = field(deb, pos, 16).trim();
                long size = Long.parseLong(field(deb, pos + 48, 10).trim());
                pos += 60;
                if (size < 0 || size > deb.length - pos) throw new IOException("corrupt ar member " + name);
                if (name.equals("data.tar")) return java.util.Arrays.copyOfRange(deb, pos, pos + (int) size);
                if (name.startsWith("data.tar")) throw new IOException("compressed data member " + name);
                pos += (int) size + (int) (size & 1);
            }
            throw new IOException("no data.tar member");
        }

        private static String field(byte[] b, int off, int len) {
            int end = off;
            while (end < off + len && b[end] != 0) end++;
            return new String(b, off, end - off, StandardCharsets.US_ASCII);
        }

        private static long octal(byte[] b, int off, int len) throws IOException {
            String f = field(b, off, len).trim();
            try {
                return f.isEmpty() ? 0 : Long.parseLong(f, 8);
            } catch (NumberFormatException e) {
                throw new IOException("corrupt tar header");
            }
        }
    }
}
