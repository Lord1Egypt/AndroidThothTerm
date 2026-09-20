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
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.preference.PreferenceManager;

import com.thothterm.R;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    private static final String SUDO_ASSET_DIR = "sudo";
    private static final String SUDO_STAGE_DIR = "var/cache/thothterm/packages";
    private static final long PROVISION_TIMEOUT_SECONDS = 180;
    /** PRoot's fake_id0 elevates only on the setuid bit; force it on the real binary. */
    private static final int SUDO_SETUID_MODE = 04755;

    /**
     * Shared objects PRoot links against, staged next to it so the dynamic
     * loader finds them. This list must match what {@code tools/build-proot.sh}
     * produces and what {@code readelf -d libproot.so} reports as NEEDED;
     * libandroid-selinux was carried over from the old prebuilt bundle and is
     * not referenced by the binary we build.
     */
    private static final String[] RUNTIME_LIBS = {
            "libtalloc.so.2",
            "libandroid-shmem.so"
    };

    private static volatile RootfsManager sInstance;

    private final Context appContext;
    private final File linuxDir;
    private final File rootfsDir;
    private final File stagingDir;
    /** Verified archive for builds that do not embed one; unused otherwise. */
    private final File downloadedImage;
    private final File stateFile;
    private final String prootRootfsPath;
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
        this.prootRootfsPath = canonicalPath(rootfsDir);
        this.stagingDir = new File(linuxDir, "rootfs.staging");
        this.downloadedImage = new File(linuxDir, "ubuntu-base.tar.gz");
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

    /**
     * The rootfs path to put on the PRoot command line. Inside an app's mount
     * namespace Android exposes {@code /data/user/0} as a symlink to
     * {@code /data/data}, so this is not the representation
     * {@link android.content.Context#getFilesDir()} reports. PRoot canonicalizes
     * {@code --rootfs} and then writes that canonical form into every
     * {@code --link2symlink} target, resolving those targets later by comparing
     * them against it as a string prefix. Handing PRoot any other representation
     * of the same directory makes links created in one session unresolvable in
     * another, so the canonical form is what must be passed.
     */
    public String prootRootfsPath() {
        return prootRootfsPath;
    }

    private static String canonicalPath(File dir) {
        try {
            return dir.getCanonicalPath();
        } catch (IOException e) {
            ThothLog.e(LogCategory.PROOT,
                    "Cannot canonicalize rootfs path; link2symlink targets may not resolve"
                            + " across sessions path=" + dir.getAbsolutePath(), e);
            return dir.getAbsolutePath();
        }
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

    /**
     * True when this build has no embedded userland and no verified archive yet,
     * so the user must be asked before anything is downloaded.
     */
    public boolean needsImageDownload() {
        if (com.thothterm.BuildConfig.EMBEDDED_ROOTFS) return false;
        if (isReady()) return false;
        return image != null && !new RootfsDownloader(downloadedImage, image).isVerified();
    }

    /** Megabytes to download, for the consent screen. */
    public int downloadSizeMb() {
        if (image == null) return 0;
        return (int) Math.max(1, (image.compressedSize() + 524_288L) / 1_048_576L);
    }

    /** Where the userland comes from, for the consent screen. */
    public String imageSourceUrl() {
        return image == null ? "" : image.sourceUrl();
    }

    public String imageVersion() {
        return image == null ? "" : image.ubuntuVersion();
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

    /**
     * Supplies the image bytes. For a build without an embedded archive this
     * downloads first; {@link RootfsDownloader} verifies the SHA-256 before the
     * file is promoted, so nothing unverified ever reaches the extractor.
     */
    private InputStream openImageStream() throws IOException {
        if (com.thothterm.BuildConfig.EMBEDDED_ROOTFS) {
            return appContext.getAssets().open(ROOTFS_ASSET_DIR + "/" + image.assetName());
        }
        RootfsDownloader downloader = new RootfsDownloader(downloadedImage, image);
        if (!downloader.isVerified()) {
            publish(appContext.getString(R.string.ubuntu_downloading));
            final long expected = image.compressedSize();
            downloader.download((done, total) ->
                    publishProgress(done, expected > 0 ? expected : total, 0));
            // Extraction reports its own 0..100; without this the monotonic
            // guard in publishProgress would swallow all of it.
            percent = 0;
            publish(appContext.getString(R.string.ubuntu_preparing));
        }
        return new java.io.FileInputStream(downloadedImage);
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
            ensureRealSudo(rootfsDir);
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
        // Both flavours extract the same bytes: "full" streams them out of the
        // APK, "fdroid" out of the archive it downloaded and verified first.
        InputStream source = openImageStream();
        CountingInputStream counting = new CountingInputStream(source);
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
            ThothLog.w(LogCategory.SECURITY, "Ubuntu rootfs checksum mismatch");
            throw new IOException("Ubuntu rootfs checksum mismatch");
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
        ensureRealSudo(rootfsDir);
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
        removeManagedSudoHelper(root);
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

        File fastfetchFit = new File(binDir, "fastfetch-fit");
        copyManagedAsset("linux/fastfetch-fit", fastfetchFit);
        fileOps.setMode(fastfetchFit, 0755);

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
        writeTextIfChanged(hosts,
                GuestConfig.ensureHosts(hosts.isFile() ? readText(hosts) : ""));
        File group = new File(root, "etc/group");
        writeTextIfChanged(group, GuestConfig.ensureGroups(
                group.isFile() ? readText(group) : "", android.os.Process.myUid()));
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

        // sudo/visudo require every sudoers.d file to be mode 0440; the package
        // README is not a conffile and can arrive with a laxer mode, which makes
        // `visudo -c` report "bad permissions" even though the syntax is valid.
        File sudoersReadme = new File(sudoersDir, "README");
        if (sudoersReadme.isFile()) {
            fileOps.setMode(sudoersReadme, 0440);
        }

        // Real Ubuntu sudo is the only elevation path; drop the v2 passwordless
        // su customization so su returns to its stock policy.
        File pamSu = new File(root, "etc/pam.d/su");
        if (pamSu.isFile()) {
            writeTextIfChanged(pamSu, GuestConfig.removePasswordlessSu(readText(pamSu)));
        }
    }

    /**
     * Removes the v2 ThothTerm-managed su-backed {@code /usr/local/bin/sudo}
     * helper, which would otherwise shadow the real {@code /usr/bin/sudo}. Only
     * a file carrying the ThothTerm marker is removed; a user replacement is
     * left untouched and logged.
     */
    private void removeManagedSudoHelper(File root) {
        File helper = new File(new File(root, "usr/local/bin"), "sudo");
        if (!helper.isFile()) return;
        try {
            if (GuestConfig.isManagedSudoHelper(readText(helper))) {
                if (helper.delete()) {
                    ThothLog.i(LogCategory.INSTALLER,
                            "Removed managed su-backed sudo helper");
                } else {
                    ThothLog.w(LogCategory.INSTALLER,
                            "Could not remove managed sudo helper");
                }
            } else {
                ThothLog.w(LogCategory.INSTALLER,
                        "Leaving unrecognized /usr/local/bin/sudo in place");
            }
        } catch (IOException e) {
            ThothLog.w(LogCategory.INSTALLER, "Cannot inspect /usr/local/bin/sudo");
        }
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

    /**
     * Ensures the genuine Ubuntu {@code sudo} package is installed from the
     * bundled offline packages and that PRoot's setuid-bit elevation can work.
     * The embedded Canonical rootfs is never modified; this is a post-extraction
     * layer. Best effort: any failure is logged and retried on the next session,
     * and it never blocks the terminal from opening.
     */
    private synchronized void ensureRealSudo(File root) {
        if (isSudoInstalled(root)) {
            forceSudoSetuid(root);
            return;
        }
        try {
            if (com.thothterm.BuildConfig.EMBEDDED_ROOTFS) {
                stageSudoPackages(root);
                runProvisioning(root, sudoInstallScript());
            } else {
                // No bundled .deb payload in this build: install sudo from
                // Ubuntu's own archive, so apt verifies it with the
                // distribution's signing keys rather than us re-implementing
                // that check.
                runProvisioning(root, sudoAptInstallScript());
            }
            boolean setuid = forceSudoSetuid(root);
            String report = runProvisioning(root, sudoVerifyScript());
            ThothLog.i(LogCategory.ROOTFS, "sudo provisioning complete setuid="
                    + setuid + " report=" + report.replace('\n', ' ').trim());
        } catch (Throwable t) {
            ThothLog.e(LogCategory.ROOTFS, "sudo provisioning failed type="
                    + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
        }
    }

    /** True when the dpkg database already records sudo as installed. */
    private boolean isSudoInstalled(File root) {
        File status = new File(root, "var/lib/dpkg/status");
        if (!status.isFile()) return false;
        try {
            String text = readText(status);
            for (String stanza : text.split("\n\n")) {
                if (!(stanza.startsWith("Package: sudo\n")
                        || stanza.contains("\nPackage: sudo\n"))) {
                    continue;
                }
                if (stanza.contains("\nStatus: install ok installed\n")
                        || stanza.endsWith("\nStatus: install ok installed")) {
                    return true;
                }
            }
        } catch (IOException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot read dpkg status");
        }
        return false;
    }

    private void stageSudoPackages(File root) throws IOException {
        File stage = new File(root, SUDO_STAGE_DIR);
        if (!stage.exists() && !stage.mkdirs()) {
            throw new IOException("Cannot create sudo package staging directory");
        }
        String[] assets = appContext.getAssets().list(SUDO_ASSET_DIR);
        int count = 0;
        if (assets != null) {
            for (String name : assets) {
                if (!name.endsWith(".deb")) continue;
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = appContext.getAssets().open(SUDO_ASSET_DIR + "/" + name);
                    out = new FileOutputStream(new File(stage, name));
                    copyStream(in, out);
                    count++;
                } finally {
                    closeQuietly(out);
                    closeQuietly(in);
                }
            }
        }
        if (count == 0) throw new IOException("No bundled admin packages");
        ThothLog.i(LogCategory.INSTALLER, "Staged admin packages count=" + count);
    }

    private String sudoInstallScript() {
        String stage = "/" + SUDO_STAGE_DIR;
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + "cd /\n"
                + "dpkg --force-confold -i " + stage + "/libapparmor1_*.deb\n"
                + "dpkg --force-confold -i " + stage + "/sudo-common_*.deb\n"
                + "dpkg --force-confold -i " + stage + "/sudo_*.deb\n"
                + "dpkg --configure -a\n";
    }

    /**
     * Installs sudo from the configured Ubuntu archive. apt checks the release
     * file's signature against the keyring already present in the base image,
     * which is a stronger guarantee than a checksum we pin ourselves, and it
     * resolves the dependency closure instead of assuming it.
     */
    private String sudoAptInstallScript() {
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + "export DEBIAN_FRONTEND=noninteractive\n"
                + "cd /\n"
                + "apt-get update\n"
                + "apt-get install -y --no-install-recommends sudo\n"
                + "dpkg --configure -a\n"
                // sudo refuses to read a drop-in that is not 0440, and under
                // PRoot's fake root dpkg does not reproduce that mode on the
                // README that sudo-common ships. visudo -c then reports the
                // whole directory as bad even though our own file is fine.
                + "[ -f /etc/sudoers.d/README ] && chmod 0440 /etc/sudoers.d/README\n"
                + "exit 0\n";
    }

    private String sudoVerifyScript() {
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + "dpkg-query -W -f='${Status} ${Version}\\n' sudo\n"
                + "test -x /usr/bin/sudo\n"
                + "visudo -c\n";
    }

    private static final String PATH_EXPORT =
            "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    /**
     * Forces the real setuid bit on {@code /usr/bin/sudo.ws} from Java, bypassing
     * PRoot's chmod shim. PRoot's fake_id0 extension grants fake euid 0 only when
     * the executed binary carries S_ISUID, and the Android-side extraction masks
     * setuid bits, so this explicit chmod is the required PRoot adjustment.
     */
    private boolean forceSudoSetuid(File root) {
        File sudo = new File(root, "usr/bin/sudo.ws");
        if (!sudo.isFile()) return false;
        try {
            Os.chmod(sudo.getAbsolutePath(), SUDO_SETUID_MODE);
        } catch (ErrnoException e) {
            ThothLog.w(LogCategory.ROOTFS,
                    "Cannot chmod /usr/bin/sudo.ws: " + e.getMessage());
        }
        try {
            return (Os.stat(sudo.getAbsolutePath()).st_mode & OsConstants.S_ISUID) != 0;
        } catch (ErrnoException e) {
            return false;
        }
    }

    /** Runs a one-shot fake-root PRoot command for offline provisioning. */
    private String runProvisioning(File root, String script) throws IOException {
        UbuntuRuntime runtime = UbuntuRuntime.from(this, "xterm-256color");
        List<String> argv = runtime.buildProvisioningArgv(
                Arrays.asList("/bin/sh", "-c", script));
        Map<String, String> env = runtime.buildProvisioningEnvironment();

        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(env);

        ThothLog.i(LogCategory.ROOTFS, "Provisioning command start");
        Process process = builder.start();
        final StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(readText(process.getInputStream()));
            } catch (IOException ignored) {
            }
        }, "ThothTerm-sudo-provision");
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(PROVISION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Provisioning interrupted");
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Provisioning timed out");
        }
        try {
            reader.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = process.exitValue();
        String report = output.toString();
        ThothLog.d(LogCategory.ROOTFS, "Provisioning exit=" + code);
        if (code != 0) {
            throw new IOException("Provisioning command failed (" + code + "): "
                    + report.replace('\n', ' ').trim());
        }
        return report;
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

    static String toHex(byte[] bytes) {
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
