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

package com.thothterm.linux;

import android.content.Context;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * Owns the embedded Ubuntu image: state, first-run extraction, checksum
 * verification, and readiness. Extraction is crash-safe (staging directory
 * renamed only after verification) and performed once.
 */
public final class RootfsManager {
    public interface Listener {
        void onStatus(String message);

        void onProgress(int percent, long entries);

        void onComplete();

        void onError(String message, Throwable cause);
    }

    private static final String LINUX_ROOT = "linux";
    private static final String DISTRO_DIR = "ubuntu-26.04";
    private static final String IMAGE_ASSET = "ubuntu/image.properties";
    private static final String ROOTFS_ASSET_DIR = "ubuntu";
    private static final String RUNTIME_ASSET_DIR = "runtime/arm64-v8a";

    private static final String[] RUNTIME_LIBS = {
            "libtalloc.so.2",
            "libandroid-shmem.so",
            "libandroid-selinux.so"
    };

    private static volatile RootfsManager sInstance;

    private final Context appContext;
    private final File linuxDir;
    private final File rootfsDir;
    private final File stagingDir;
    private final File stateFile;
    private final File runtimeDir;
    private final File runtimeLibDir;
    private final File prootTmpDir;
    private final File nativeLibDir;
    private final FileOps fileOps = new AndroidFileOps();

    private ImageInfo image;
    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean failed;
    private volatile String message = "";
    private volatile int percent;
    private volatile long entries;
    private volatile Throwable lastError;

    private RootfsManager(Context context) {
        this.appContext = context.getApplicationContext();
        File filesDir = appContext.getFilesDir();
        this.linuxDir = new File(new File(filesDir, LINUX_ROOT), DISTRO_DIR);
        this.rootfsDir = new File(linuxDir, "rootfs");
        this.stagingDir = new File(linuxDir, "rootfs.staging");
        this.stateFile = new File(linuxDir, "state.properties");
        this.runtimeDir = new File(new File(filesDir, LINUX_ROOT), "runtime");
        this.runtimeLibDir = new File(runtimeDir, "lib");
        this.prootTmpDir = new File(runtimeDir, "tmp");
        this.nativeLibDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
        this.image = loadImage();
    }

    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (RootfsManager.class) {
                if (sInstance == null) sInstance = new RootfsManager(context);
            }
        }
    }

    public static RootfsManager get() {
        RootfsManager manager = sInstance;
        if (manager == null) {
            throw new IllegalStateException("RootfsManager not initialized");
        }
        return manager;
    }

    public boolean isSupportedDevice() {
        return DeviceArchitecture.isArm64Supported(android.os.Build.SUPPORTED_ABIS);
    }

    public File rootfsDir() {
        return rootfsDir;
    }

    public File runtimeLibDir() {
        return runtimeLibDir;
    }

    public File prootTmpDir() {
        return prootTmpDir;
    }

    public String prootPath() {
        return new File(nativeLibDir, "libproot.so").getAbsolutePath();
    }

    public String loaderPath() {
        return new File(nativeLibDir, "libproot_loader.so").getAbsolutePath();
    }

    public ImageInfo image() {
        return image;
    }

    public boolean isReady() {
        if (image == null) return false;

        RootfsState state = RootfsState.read(stateFile);
        if (!state.matches(image)) return false;
        if (!new File(rootfsDir, "usr/bin/bash").exists()) return false;
        return new File(runtimeLibDir, "libtalloc.so.2").exists();
    }

    public void setListener(Listener newListener) {
        this.listener = newListener;
        if (isReady()) {
            notifyComplete();
        } else if (failed) {
            notifyError();
        } else if (running) {
            notifyStatus();
        }
    }

    public void start() {
        if (isReady()) {
            notifyComplete();
            return;
        }
        if (running) {
            notifyStatus();
            return;
        }
        running = true;
        failed = false;
        new Thread(this::runPrepare, "ThothTerm-ubuntu-rootfs").start();
    }

    private void runPrepare() {
        Listener current = listener;
        try {
            if (image == null) {
                throw new IOException("Embedded image metadata is missing");
            }

            String[] supportedAbis = android.os.Build.SUPPORTED_ABIS;
            String osArch = System.getProperty("os.arch");
            ThothLog.d(LogCategory.RUNTIME, "Supported Android ABIs count="
                    + (supportedAbis == null ? 0 : supportedAbis.length)
                    + " supportedAbis=" + DeviceArchitecture.describe(supportedAbis)
                    + " osArch=" + osArch);

            if (!DeviceArchitecture.isArm64Supported(supportedAbis)) {
                ThothLog.e(LogCategory.RUNTIME, "ARM64 compatibility check failed supportedAbis="
                        + DeviceArchitecture.describe(supportedAbis)
                        + " osArch=" + osArch);
                throw new IOException("ThothTerm Ubuntu requires an arm64 device");
            }
            ThothLog.i(LogCategory.RUNTIME, "ARM64 compatibility verified");

            publish("Preparing Linux environment\u2026");
            copyRuntimeLibraries();
            extractRootfs();
            failed = false;
            notifyComplete();
        } catch (Throwable t) {
            failed = true;
            lastError = t;
            ThothLog.e(LogCategory.ROOTFS, "Extraction failed type="
                    + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
            cleanupStagingQuietly();
            notifyError();
        } finally {
            running = false;
        }
    }

    /**
     * Best-effort removal of the staging tree after a failure. A cleanup
     * failure is logged separately and never replaces the original error.
     */
    private void cleanupStagingQuietly() {
        try {
            ThothLog.d(LogCategory.ROOTFS, "Staging cleanup started");
            SafeFileTree.deleteTree(fileOps, linuxDir, stagingDir);
            ThothLog.i(LogCategory.ROOTFS, "Staging cleanup complete");
        } catch (Throwable cleanup) {
            ThothLog.e(LogCategory.ROOTFS, "Staging cleanup failed type="
                    + cleanup.getClass().getSimpleName()
                    + " message=" + cleanup.getMessage(), cleanup);
        }
    }

    private void copyRuntimeLibraries() throws IOException {
        if (!runtimeLibDir.exists() && !runtimeLibDir.mkdirs()) {
            throw new IOException("Cannot create runtime library directory");
        }
        if (!prootTmpDir.exists() && !prootTmpDir.mkdirs()) {
            throw new IOException("Cannot create PRoot temporary directory");
        }
        for (String name : RUNTIME_LIBS) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = appContext.getAssets().open(RUNTIME_ASSET_DIR + "/" + name);
                out = new FileOutputStream(new File(runtimeLibDir, name));
                copyStream(in, out);
            } finally {
                closeQuietly(out);
                closeQuietly(in);
            }
        }
        ThothLog.d(LogCategory.ROOTFS, "Runtime libraries staged");
    }

    private void extractRootfs() throws Exception {
        ThothLog.i(LogCategory.ROOTFS, "Extraction started image=" + image.imageId()
                + " version=" + image.ubuntuVersion());

        ThothLog.d(LogCategory.ROOTFS, "Staging cleanup started");
        SafeFileTree.deleteTree(fileOps, linuxDir, stagingDir);
        if (stagingDir.exists()) {
            throw new IOException("Cannot clear staging directory");
        }
        ThothLog.i(LogCategory.ROOTFS, "Staging cleanup complete");

        if (!stagingDir.mkdirs()) {
            throw new IOException("Cannot create staging directory");
        }

        final long expectedSize = image.compressedSize();
        InputStream asset = appContext.getAssets().open(
                ROOTFS_ASSET_DIR + "/" + image.assetName());
        CountingInputStream counting = new CountingInputStream(asset);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        java.security.DigestInputStream digesting =
                new java.security.DigestInputStream(counting, digest);
        GZIPInputStream gzip = new GZIPInputStream(digesting, 64 * 1024);

        TarballExtractor extractor = new TarballExtractor(
                fileOps, stagingDir,
                count -> publishProgress(count, expectedSize));
        try {
            extractor.extract(gzip);
        } finally {
            closeQuietly(gzip);
        }

        String actualSha = toHex(digest.digest());
        if (!actualSha.equalsIgnoreCase(image.upstreamSha256())) {
            ThothLog.w(LogCategory.SECURITY, "Embedded rootfs checksum mismatch");
            throw new IOException("Embedded rootfs checksum mismatch");
        }
        ThothLog.d(LogCategory.ROOTFS, "Archive entries processed count="
                + extractor.extractedEntries()
                + " rejected=" + extractor.rejectedEntries()
                + " skipped=" + extractor.skippedSpecialEntries());
        if (extractor.hardlinkFallbacks() > 0) {
            ThothLog.w(LogCategory.ROOTFS,
                    "Hardlink fallback used count=" + extractor.hardlinkFallbacks()
                            + " (Android SELinux forbids untrusted-app hardlinks)");
        }

        if (!new File(stagingDir, "usr/bin/bash").exists()
                || !new File(stagingDir, "etc/os-release").exists()) {
            throw new IOException("Extracted rootfs is incomplete");
        }

        setupUserHome(stagingDir);

        SafeFileTree.deleteTree(fileOps, linuxDir, rootfsDir);
        if (!stagingDir.renameTo(rootfsDir)) {
            throw new IOException("Cannot finalize extracted rootfs");
        }
        writeState();
        ThothLog.i(LogCategory.ROOTFS, "Extraction complete");
    }

    private void setupUserHome(File root) throws IOException {
        File home = new File(root, "home/thoth");
        if (!home.exists() && !home.mkdirs()) {
            throw new IOException("Cannot create Linux home directory");
        }

        File skel = new File(root, "etc/skel");
        if (skel.isDirectory()) {
            copyDirectoryContents(skel, home);
        }

        File bashrc = new File(home, ".bashrc");
        appendLine(bashrc, "PS1='thoth@android:\\w\\$ '");

        File profileDir = new File(root, "etc/profile.d");
        if (!profileDir.exists() && !profileDir.mkdirs()) {
            throw new IOException("Cannot create profile.d directory");
        }
        String profile = "# ThothTerm Ubuntu environment\n"
                + "export HOME=/home/thoth\n"
                + "export USER=thoth\n"
                + "export LOGNAME=thoth\n"
                + "export LANG=C.UTF-8\n"
                + "export PS1='thoth@android:\\w\\$ '\n";
        writeText(new File(profileDir, "thothterm-ubuntu.sh"), profile);

        writeText(new File(root, "etc/hostname"), "android\n");
    }

    private void writeState() throws IOException {
        RootfsState state = new RootfsState();
        state.imageId = image.imageId();
        state.ubuntuVersion = image.ubuntuVersion();
        state.architecture = image.architecture();
        state.imageSha256 = image.upstreamSha256();
        state.schemaVersion = image.schemaVersion();
        state.installedAt = System.currentTimeMillis();
        state.complete = true;
        state.write(stateFile);
    }

    private ImageInfo loadImage() {
        InputStream in = null;
        try {
            in = appContext.getAssets().open(IMAGE_ASSET);
            return ImageInfo.load(in);
        } catch (Exception e) {
            ThothLog.e(LogCategory.ROOTFS, "Cannot read embedded image metadata", e);
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private void publish(String text) {
        message = text;
        Listener current = listener;
        if (current != null) current.onStatus(text);
    }

    private void publishProgress(long count, long total) {
        entries = count;
        int value = 0;
        if (total > 0) value = (int) Math.min(99, (count * 100) / total);
        percent = value;
        Listener current = listener;
        if (current != null) current.onProgress(value, count);
    }

    private void notifyStatus() {
        Listener current = listener;
        if (current == null) return;
        if (!message.isEmpty()) current.onStatus(message);
        if (percent > 0) current.onProgress(percent, entries);
    }

    private void notifyComplete() {
        Listener current = listener;
        if (current != null) current.onComplete();
    }

    private void notifyError() {
        Listener current = listener;
        if (current == null) return;
        String text = (lastError != null && lastError.getMessage() != null)
                ? lastError.getMessage()
                : "Linux environment could not be prepared.";
        current.onError(text, lastError);
    }

    private void copyDirectoryContents(File source, File destination) throws IOException {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory()) {
                if (!target.exists() && !target.mkdirs()) {
                    throw new IOException("Cannot create " + target);
                }
                copyDirectoryContents(child, target);
            } else if (!target.exists()) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = new FileInputStream(child);
                    out = new FileOutputStream(target);
                    copyStream(in, out);
                } finally {
                    closeQuietly(out);
                    closeQuietly(in);
                }
            }
        }
    }

    private void appendLine(File file, String line) throws IOException {
        StringBuilder text = new StringBuilder();
        if (file.isFile()) {
            text.append(readText(file));
            if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
        }
        text.append(line).append('\n');
        writeText(file, text.toString());
    }

    private void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private String readText(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            StringBuilder text = new StringBuilder();
            int read;
            while ((read = in.read(buffer)) > 0) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
            return text.toString();
        } finally {
            in.close();
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static final class CountingInputStream extends java.io.FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long getCount() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) count += read;
            return read;
        }
    }
}
