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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.storage.StorageManager;

import androidx.preference.PreferenceManager;

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

    public void clearListener(Listener expected) {
        if (listener == expected) listener = null;
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
        percent = 0;
        entries = 0;
        message = "";
        new Thread(this::runPrepare, "ThothTerm-ubuntu-rootfs").start();
    }

    private void runPrepare() {
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
            running = false;
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

        long usable = usableSpace();
        if (!StorageSpace.isSufficient(usable, image.uncompressedSize())) {
            ThothLog.w(LogCategory.STORAGE, "Insufficient storage for Ubuntu extraction");
            throw new IOException("Not enough free storage to prepare Ubuntu");
        }

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
                count -> publishProgress(counting.getCount(), expectedSize, count));
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
        publishProgress(expectedSize, expectedSize, extractor.extractedEntries());
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

        setupRootfs(stagingDir, true);

        SafeFileTree.deleteTree(fileOps, linuxDir, rootfsDir);
        if (!stagingDir.renameTo(rootfsDir)) {
            throw new IOException("Cannot finalize extracted rootfs");
        }
        writeState();
        ThothLog.i(LogCategory.ROOTFS, "Extraction complete");
    }

    @SuppressLint("UsableSpace") // Safe fallback when StorageManager cannot report a quota.
    private long usableSpace() {
        StorageManager storage = (StorageManager) appContext.getSystemService(
                Context.STORAGE_SERVICE);
        if (storage != null) {
            try {
                File volume = linuxDir.getParentFile();
                return storage.getAllocatableBytes(storage.getUuidForPath(volume));
            } catch (IOException | RuntimeException ignored) {
                // Fall through to the conservative filesystem value.
            }
        }
        return linuxDir.getParentFile().getUsableSpace();
    }

    /** Refreshes app-managed integration without re-extracting or replacing user data. */
    public synchronized void prepareSession() throws IOException {
        if (!isReady()) throw new IOException("Linux environment is not ready");
        setupRootfs(rootfsDir, false);
    }

    private void setupRootfs(File root, boolean installSkeleton) throws IOException {
        File home = new File(root, "home/thoth");
        if (!home.exists() && !home.mkdirs()) {
            throw new IOException("Cannot create Linux home directory");
        }

        File skel = new File(root, "etc/skel");
        if (installSkeleton && skel.isDirectory()) {
            copyDirectoryContents(skel, home);
        }

        installBashIntegration(new File(home, ".bashrc"));

        setupUserAccount(root);
        setupSudo(root);

        File profileDir = new File(root, "etc/profile.d");
        if (!profileDir.exists() && !profileDir.mkdirs()) {
            throw new IOException("Cannot create profile.d directory");
        }
        File localeFix = new File(profileDir, "01-locale-fix.sh");
        writeTextIfChanged(localeFix, GuestConfig.localeFixScript());

        setupDebconfFrontend(root);
        writeRuntimeConfigVersion(root);

        copyManagedAsset("linux/thothterm-ubuntu.sh",
                new File(profileDir, "thothterm-ubuntu.sh"));

        File binDir = new File(root, "usr/local/bin");
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new IOException("Cannot create managed command directory");
        }
        File thothfetch = new File(binDir, "thothfetch");
        copyManagedAsset("linux/thothfetch", thothfetch);
        fileOps.setMode(thothfetch, 0755);

        File managedDir = new File(root, "etc/thothterm");
        if (!managedDir.exists() && !managedDir.mkdirs()) {
            throw new IOException("Cannot create managed configuration directory");
        }
        File welcomeEnabled = new File(managedDir, "welcome-enabled");
        boolean showWelcome = PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("ubuntu_show_welcome", true);
        if (showWelcome) {
            writeTextIfChanged(welcomeEnabled, "enabled\n");
        } else if (welcomeEnabled.exists() && !welcomeEnabled.delete()) {
            throw new IOException("Cannot disable welcome banner");
        }

        File hostname = new File(root, "etc/hostname");
        if (!hostname.exists() || "android".equals(readText(hostname).trim())) {
            writeText(hostname, "thothterm\n");
        }
        File hosts = new File(root, "etc/hosts");
        if (!hosts.exists()) {
            writeText(hosts, "127.0.0.1 localhost thothterm\n::1 localhost ip6-localhost\n");
        }
        File resolv = new File(root, "etc/resolv.conf");
        if (fileOps.isSymlink(resolv)) {
            if (!resolv.delete()) throw new IOException("Cannot prepare resolver mount point");
            writeText(resolv, "# Runtime resolver is bind-mounted by ThothTerm.\n");
        }
        File tmp = new File(root, "tmp");
        if (!tmp.exists() && !tmp.mkdirs()) throw new IOException("Cannot create /tmp");
        fileOps.setMode(tmp, 0777);
    }

    private void setupUserAccount(File root) throws IOException {
        File passwd = new File(root, "etc/passwd");
        if (passwd.isFile()) {
            writeTextIfChanged(passwd, GuestConfig.ensurePasswd(readText(passwd)));
        }

        File group = new File(root, "etc/group");
        if (group.isFile()) {
            writeTextIfChanged(group, GuestConfig.ensureGroup(readText(group)));
        }

        File shadow = new File(root, "etc/shadow");
        if (shadow.isFile()) {
            writeTextIfChanged(shadow, GuestConfig.ensureShadow(readText(shadow)));
        }
    }

    private void setupSudo(File root) throws IOException {
        File sudoersDir = new File(root, "etc/sudoers.d");
        if (!sudoersDir.exists() && !sudoersDir.mkdirs()) {
            throw new IOException("Cannot create sudoers.d directory");
        }
        File thothSudoers = new File(sudoersDir, "thoth");
        writeTextIfChanged(thothSudoers, GuestConfig.sudoersEntry());
        fileOps.setMode(thothSudoers, 0440);

        File pamSu = new File(root, "etc/pam.d/su");
        if (pamSu.isFile()) {
            writeTextIfChanged(pamSu, GuestConfig.ensurePasswordlessSu(readText(pamSu)));
        }

        File sudoHelper = new File(new File(root, "usr/local/bin"), "sudo");
        writeTextIfChanged(sudoHelper, GuestConfig.managedSudoScript());
        fileOps.setMode(sudoHelper, 0755);
    }

    private void setupDebconfFrontend(File root) throws IOException {
        File debconfDir = new File(root, "var/cache/debconf");
        if (!debconfDir.exists() && !debconfDir.mkdirs()) {
            throw new IOException("Cannot create debconf cache directory");
        }

        File configDat = new File(debconfDir, "config.dat");
        if (configDat.isFile()) {
            writeTextIfChanged(configDat,
                    GuestConfig.ensureDebconfFrontendConfig(readText(configDat)));
        }

        File templatesDat = new File(debconfDir, "templates.dat");
        if (templatesDat.isFile()) {
            writeTextIfChanged(templatesDat,
                    GuestConfig.ensureDebconfFrontendTemplate(readText(templatesDat)));
        }
    }

    /** Records the managed guest-configuration schema applied to this rootfs. */
    private void writeRuntimeConfigVersion(File root) throws IOException {
        File managedDir = new File(root, "etc/thothterm");
        if (!managedDir.exists() && !managedDir.mkdirs()) {
            throw new IOException("Cannot create managed configuration directory");
        }
        writeTextIfChanged(new File(managedDir, "runtime-config-version"),
                GuestConfig.RUNTIME_CONFIG_VERSION + "\n");
    }

    private void installBashIntegration(File bashrc) throws IOException {
        String original = bashrc.isFile() ? readText(bashrc) : "";
        writeTextIfChanged(bashrc, ShellIntegration.updateBashrc(original));
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

    private void publishProgress(long consumedBytes, long totalBytes, long entryCount) {
        entries = entryCount;
        int value = ExtractionProgress.percent(consumedBytes, totalBytes);
        if (value <= percent && value != 100) return;
        percent = value;
        Listener current = listener;
        if (current != null) current.onProgress(value, entryCount);
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

    private void writeTextIfChanged(File file, String text) throws IOException {
        if (file.isFile() && text.equals(readText(file))) return;
        writeText(file, text);
    }

    private void copyManagedAsset(String assetName, File destination) throws IOException {
        InputStream input = appContext.getAssets().open(assetName);
        try {
            writeTextIfChanged(destination, readText(input));
        } finally {
            input.close();
        }
    }

    private String readText(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return readText(in);
        } finally {
            in.close();
        }
    }

    private String readText(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        StringBuilder text = new StringBuilder();
        int read;
        while ((read = in.read(buffer)) > 0) {
            text.append(new String(buffer, 0, read, "UTF-8"));
        }
        return text.toString();
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
