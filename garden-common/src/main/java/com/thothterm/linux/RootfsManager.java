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

import com.thothterm.R;
import com.thothterm.linux.RootfsLifecycle.Condition;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns the edition's Linux environment: its lifecycle, first-run extraction,
 * verification and readiness.
 *
 * <p>The lifecycle rule ({@link RootfsLifecycle}): only a genuinely
 * never-installed environment is set up automatically. A damaged one is
 * classified and at most repaired without touching guest data; replacing the
 * system takes an explicit, confirmed reset, which moves {@code /home} into the
 * new system instead of deleting it. Nothing in this class deletes an
 * installed rootfs.</p>
 *
 * <p>Setup reports explicit stages ({@link SetupState}): the extraction
 * percentage covers the archive only, then "Finalizing". The terminal depends
 * only on that core; it opens at {@link SetupState#TERMINAL_READY}. Optional
 * provisioning (sudo, a missing pacman keyring) runs afterwards in the
 * background ({@link OptionalSetup}): a failure leaves the terminal usable,
 * is reported and can be retried, and never re-extracts or touches
 * {@code /home}.</p>
 */
public final class RootfsManager {
    public interface Listener {
        void onStatus(String message);

        /** Download progress (bytes), before extraction starts. */
        void onDownloadProgress(int percent);

        /** Archive extraction progress; 100 means the archive is fully extracted. */
        void onProgress(int percent, long entries);

        void onComplete();

        void onError(String message, Throwable cause);

        /**
         * The environment needs a decision only the user can make (damaged,
         * or an unfinished reset). Nothing destructive has happened.
         */
        void onAttentionNeeded(Condition condition);
    }

    private static final String LINUX_ROOT = "linux";
    /** Only the full flavour packages an archive here; its presence is the switch. */
    private static final String ROOTFS_ASSET_DIR = "garden/rootfs";
    private static final String RUNTIME_ASSET_DIR = "runtime/arm64-v8a";
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
    private final RootfsLifecycle.Layout layout;
    /** Verified archive for builds that do not embed one; unused otherwise. */
    private final File downloadedImage;
    private final File stateFile;
    private final String prootRootfsPath;
    private final File runtimeDir;
    private final File runtimeLibDir;
    private final File prootTmpDir;
    private final File nativeLibDir;
    private final FileOps fileOps = new AndroidFileOps();

    private final DistroInfo image;
    /** The edition's colours; null when the edition ships none (plain banner). */
    private final GardenPalette palette;
    private final boolean embeddedRootfs;
    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean failed;
    private volatile String message = "";
    private volatile int percent;
    private volatile long entries;
    private volatile Throwable lastError;
    /** The user confirmed a reset in this process (the marker file persists it). */
    private volatile boolean resetConfirmed;

    /** Network or slow guest work that must never block a terminal. */
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ThothTerm-admin-tools");
        thread.setDaemon(true);
        return thread;
    });
    private final OptionalSetup optionalSetup = new OptionalSetup();
    /** Serializes guest package work; deliberately not {@code this}. */
    private final Object adminLock = new Object();

    private RootfsManager(Context context) {
        this.appContext = context.getApplicationContext();
        File filesDir = appContext.getFilesDir();
        this.image = loadImage();
        this.palette = loadPalette();
        this.embeddedRootfs = image != null && hasEmbeddedArchive(image);
        // Without valid metadata nothing is ever extracted; the directory only
        // has to be one no edition uses.
        this.linuxDir = new File(new File(filesDir, LINUX_ROOT),
                image != null ? image.distroDir() : "unconfigured");
        this.layout = new RootfsLifecycle.Layout(linuxDir);
        this.rootfsDir = layout.rootfs;
        this.prootRootfsPath = canonicalPath(rootfsDir);
        this.stagingDir = layout.staging;
        this.downloadedImage = new File(linuxDir, "rootfs.tar.gz");
        this.stateFile = layout.stateFile;
        this.runtimeDir = new File(new File(filesDir, LINUX_ROOT), "runtime");
        this.runtimeLibDir = new File(runtimeDir, "lib");
        this.prootTmpDir = new File(runtimeDir, "tmp");
        this.nativeLibDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
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

    /** True when this APK carries the rootfs archive (the full flavour). */
    public boolean isRootfsEmbedded() {
        return embeddedRootfs;
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

    public GardenPalette palette() {
        return palette;
    }

    public DistroInfo image() {
        return image;
    }

    // ---- lifecycle -------------------------------------------------------------

    /**
     * Where the environment stands, from a few {@code lstat} calls. An I/O
     * failure while looking is never read as "not installed".
     */
    public Condition condition() {
        if (image == null) return Condition.INSTALLED_DAMAGED;
        try {
            RootfsLifecycle.Facts facts = RootfsLifecycle.probe(fileOps, layout,
                    RootfsState.read(stateFile).isInstalled(),
                    GardenRuntime.GUEST_ENTRY_POINTS, runtimeFiles());
            Condition condition = RootfsLifecycle.assess(facts);
            if (condition != Condition.INSTALLED_HEALTHY
                    && condition != Condition.NOT_INSTALLED) {
                ThothLog.w(LogCategory.ROOTFS, "Environment condition=" + condition + " " + facts);
            }
            return condition;
        } catch (IOException e) {
            ThothLog.e(LogCategory.ROOTFS, "Cannot inspect the Linux environment", e);
            return Condition.INSTALLED_DAMAGED;
        }
    }

    public boolean isReady() {
        return image != null && condition() == Condition.INSTALLED_HEALTHY;
    }

    /** The explicit setup state, for the UI and the log. */
    public SetupState setupState() {
        return SetupState.of(running, condition(), optionalSetup.status());
    }

    /** Optional provisioning after the terminal is ready; listen for failures here. */
    public OptionalSetup optionalSetup() {
        return optionalSetup;
    }

    /** Queues optional provisioning again (the user's retry). Never blocks. */
    public void retryOptionalSetup() {
        if (!isReady()) return;
        scheduleAdminTools("retry", null);
    }

    private List<File> runtimeFiles() {
        List<File> files = new ArrayList<>();
        for (String name : RUNTIME_LIBS) files.add(new File(runtimeLibDir, name));
        return files;
    }

    public void setListener(Listener newListener) {
        this.listener = newListener;
        if (running) {
            notifyStatus();
            return;
        }
        Condition condition = condition();
        if (condition == Condition.INSTALLED_HEALTHY) {
            notifyComplete();
        } else if (failed) {
            notifyError();
        } else if (needsUser(condition)) {
            notifyAttention(condition);
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
        if (embeddedRootfs || image == null) return false;
        Condition condition = condition();
        boolean wantsArchive = condition == Condition.NOT_INSTALLED
                || (condition == Condition.EXPLICIT_RESET_REQUESTED && resetConfirmed);
        return wantsArchive && !new RootfsDownloader(downloadedImage, image).isVerified();
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
        return image == null ? "" : image.distroVersion();
    }

    /** True for the conditions that wait for the user instead of running. */
    private boolean needsUser(Condition condition) {
        return condition == Condition.INSTALLED_DAMAGED
                || (condition == Condition.EXPLICIT_RESET_REQUESTED && !resetConfirmed);
    }

    /**
     * Does whatever the current condition allows without asking: a first
     * install, re-staging app runtime files, finishing an install in place, or
     * a reset the user confirmed. A damaged installation is reported through
     * {@link Listener#onAttentionNeeded} and left exactly as it is.
     */
    public void start() {
        if (running) {
            notifyStatus();
            return;
        }
        Condition condition = condition();
        if (condition == Condition.INSTALLED_HEALTHY) {
            notifyComplete();
            return;
        }
        if (needsUser(condition)) {
            notifyAttention(condition);
            return;
        }
        running = true;
        failed = false;
        percent = 0;
        entries = 0;
        message = "";
        new Thread(this::runPrepare, "ThothTerm-rootfs").start();
    }

    /**
     * The user confirmed "reinstall the system files, keep /home". Recorded on
     * disk first, so an interrupted reset is visible after a restart.
     */
    public void requestReset() throws IOException {
        if (!linuxDir.isDirectory() && !linuxDir.mkdirs()) {
            throw new IOException("Cannot create " + linuxDir);
        }
        if (fileOps.type(layout.resetMarker) == FileOps.Type.NONE) {
            fileOps.createNew(layout.resetMarker, 0600).close();
        }
        resetConfirmed = true;
        ThothLog.w(LogCategory.ROOTFS, "System reset confirmed by the user; /home is preserved");
    }

    /** Withdraws a reset that has not started replacing anything. */
    public void cancelReset() throws IOException {
        if (running) throw new IOException("A reset is running");
        if (fileOps.type(layout.previous) != FileOps.Type.NONE) {
            throw new IOException("A reset is half-way; it must be completed or rolled back");
        }
        if (fileOps.type(layout.resetMarker) != FileOps.Type.NONE) fileOps.unlink(layout.resetMarker);
        resetConfirmed = false;
        SafeFileTree.deleteTree(fileOps, linuxDir, stagingDir);
        ThothLog.i(LogCategory.ROOTFS, "System reset cancelled");
    }

    /**
     * Supplies the image bytes. For a build without an embedded archive this
     * downloads first; {@link RootfsDownloader} verifies the SHA-256 before the
     * file is promoted, so nothing unverified ever reaches the extractor.
     */
    private InputStream openImageStream(SetupTimeline timeline) throws IOException {
        if (embeddedRootfs) {
            return appContext.getAssets().open(ROOTFS_ASSET_DIR + "/" + image.assetName());
        }
        RootfsDownloader downloader = new RootfsDownloader(downloadedImage, image);
        if (!downloader.isVerified()) {
            publish(appContext.getString(R.string.garden_downloading));
            final long expected = image.compressedSize();
            downloader.download((done, total) ->
                    publishDownload(done, expected > 0 ? expected : total));
            ThothLog.i(LogCategory.ROOTFS, timeline.mark(
                    SetupTimeline.Stage.DOWNLOAD_COMPLETE, "bytes=" + expected));
        }
        return new java.io.FileInputStream(downloadedImage);
    }

    private void runPrepare() {
        SetupTimeline timeline = SetupTimeline.start();
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
                throw new IOException(image.editionName() + " requires an arm64 device");
            }
            ThothLog.i(LogCategory.RUNTIME, "ARM64 compatibility verified");

            // A reset interrupted half-way is completed or rolled back first;
            // both keep /home.
            if (RootfsLifecycle.recover(fileOps, layout)) {
                ThothLog.w(LogCategory.ROOTFS, "Completed an interrupted reset; /home kept");
                finishNewSystem(timeline);
            }

            Condition condition = condition();
            ThothLog.i(LogCategory.ROOTFS, "Preparing condition=" + condition);
            switch (condition) {
                case NOT_INSTALLED:
                    publish(appContext.getString(R.string.garden_preparing));
                    copyRuntimeLibraries();
                    installFresh(timeline);
                    break;
                case APP_RUNTIME_DAMAGED:
                    // App-owned files outside the rootfs; the guest is untouched.
                    copyRuntimeLibraries();
                    break;
                case REPAIR_REQUIRED:
                    // A rootfs with its entry points but no completion record:
                    // finish it in place, as every session start does anyway.
                    publish(appContext.getString(R.string.garden_finalizing, image.distroName()));
                    copyRuntimeLibraries();
                    setupRootfs(rootfsDir, false);
                    writeState();
                    ThothLog.i(LogCategory.ROOTFS, timeline.mark(
                            SetupTimeline.Stage.STATE_WRITTEN, "repair-in-place"));
                    break;
                case EXPLICIT_RESET_REQUESTED:
                    if (!resetConfirmed) {
                        running = false;
                        notifyAttention(condition);
                        return;
                    }
                    copyRuntimeLibraries();
                    resetSystemKeepingHome(timeline);
                    break;
                case INSTALLED_DAMAGED:
                    running = false;
                    notifyAttention(condition);
                    return;
                case INSTALLED_HEALTHY:
                default:
                    break;
            }

            if (condition() != Condition.INSTALLED_HEALTHY) {
                throw new IOException("The Linux environment is still not usable: " + condition());
            }
            // The core is complete. Nothing optional -- no package install, no
            // network -- runs before the terminal opens.
            failed = false;
            running = false;
            ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.TERMINAL_READY,
                    "state=" + setupState()));
            notifyComplete();
            ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.TERMINAL_HANDOFF, ""));
            ThothLog.i(LogCategory.ROOTFS, timeline.summary());
            scheduleAdminTools("after setup", timeline);
        } catch (Throwable t) {
            failed = true;
            lastError = t;
            ThothLog.e(LogCategory.ROOTFS, "Preparation failed type="
                    + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
            ThothLog.i(LogCategory.ROOTFS, timeline.summary());
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
     * Staging never holds user data: /home moves into it only as the last
     * step of a reset, which {@link RootfsLifecycle#recover} completes.
     */
    private void cleanupStagingQuietly() {
        try {
            if (fileOps.type(layout.previous) != FileOps.Type.NONE) {
                ThothLog.w(LogCategory.ROOTFS, "Reset in progress; staging kept for recovery");
                return;
            }
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

    /** First run: nothing exists, so nothing can be replaced. */
    private void installFresh(SetupTimeline timeline) throws Exception {
        stageVerifiedArchive(timeline);
        setupRootfs(stagingDir, true);
        if (isPacman() && fileOps.type(new File(stagingDir, "etc/pacman.d/gnupg"))
                != FileOps.Type.NONE) {
            // A keyring in the image would be one every installation shares,
            // private master key included.
            throw new IOException("The image carries a pacman keyring");
        }
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.SETUP_ROOTFS_COMPLETE, ""));
        RootfsLifecycle.markStagingComplete(fileOps, layout);
        RootfsLifecycle.promoteFreshInstall(fileOps, layout);
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.ROOTFS_PROMOTED, ""));
        finishNewSystem(timeline);
    }

    /**
     * The confirmed reset: a new, verified system replaces the old one and
     * the old {@code /home} is moved into it ({@link RootfsLifecycle}).
     */
    private void resetSystemKeepingHome(SetupTimeline timeline) throws Exception {
        int prootCount = GuestProcesses.countProot(new File("/proc"), prootPath(),
                android.os.Process.myPid());
        if (prootCount != 0) {
            // Moving a system out from under running guest processes is not
            // safe; the user closes the windows (and LAN Mode) first.
            throw new IOException("Close every terminal window and turn LAN Mode off"
                    + " before reinstalling (" + prootCount + " Linux process(es) running)");
        }
        ThothLog.w(LogCategory.ROOTFS, "System reset started; /home will be moved, not copied");
        stageVerifiedArchive(timeline);
        setupRootfs(stagingDir, true);
        if (isPacman() && fileOps.type(new File(stagingDir, "etc/pacman.d/gnupg"))
                != FileOps.Type.NONE) {
            throw new IOException("The image carries a pacman keyring");
        }
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.SETUP_ROOTFS_COMPLETE, ""));
        RootfsLifecycle.markStagingComplete(fileOps, layout);
        RootfsLifecycle.replaceSystemKeepingHome(fileOps, layout);
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.ROOTFS_PROMOTED, "reset"));
        finishNewSystem(timeline);
    }

    /**
     * After a new system is in place: managed configuration (again, now that
     * the preserved /home is there), the completion record, and the reset
     * marker is cleared. No guest command runs here: a pacman keyring is
     * created by the optional setup after the terminal opens
     * ({@link #runOptionalSetup}); until it exists and verifies, pacman
     * refuses every package, because signatures stay required.
     */
    private void finishNewSystem(SetupTimeline timeline) throws IOException {
        publish(appContext.getString(R.string.garden_finalizing, image.distroName()));
        setupRootfs(rootfsDir, false);
        writeState();
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.STATE_WRITTEN, ""));
        if (fileOps.type(layout.resetMarker) != FileOps.Type.NONE) fileOps.unlink(layout.resetMarker);
        resetConfirmed = false;
        deleteVerifiedDownload();
    }

    /** Extracts and verifies the pinned archive into a fresh staging tree. */
    private void stageVerifiedArchive(SetupTimeline timeline) throws Exception {
        ThothLog.i(LogCategory.ROOTFS, "Preparing the system image image=" + image.imageId()
                + " version=" + image.distroVersion());

        ThothLog.d(LogCategory.ROOTFS, "Staging cleanup started");
        SafeFileTree.deleteTree(fileOps, linuxDir, stagingDir);
        if (fileOps.type(stagingDir) != FileOps.Type.NONE) {
            throw new IOException("Cannot clear staging directory");
        }
        ThothLog.i(LogCategory.ROOTFS, "Staging cleanup complete");

        long usable = usableSpace();
        if (!StorageSpace.isSufficient(usable, image.uncompressedSize())) {
            ThothLog.w(LogCategory.STORAGE, "Insufficient storage for rootfs extraction");
            throw new IOException("Not enough free storage to prepare "
                    + image.distroName());
        }
        // On a first install nothing below files/linux exists yet.
        if (!linuxDir.isDirectory() && !linuxDir.mkdirs()) {
            throw new IOException("Cannot create " + linuxDir);
        }
        fileOps.mkdir(stagingDir, 0700);

        final long expectedSize = image.compressedSize();
        // Both flavours extract the same bytes: "full" streams them out of the
        // APK, "fdroid" out of the archive it downloaded and verified first.
        InputStream source = openImageStream(timeline);
        // Logged only now: for a build that downloads, the line above did that first.
        ThothLog.i(LogCategory.ROOTFS, "Extraction started image=" + image.imageId());
        percent = 0;
        publish(appContext.getString(R.string.garden_extracting));
        RootfsArchive.Result result;
        try {
            result = RootfsArchive.extract(source, stagingDir, fileOps,
                    (bytes, count) -> publishProgress(bytes, expectedSize, count));
        } finally {
            closeQuietly(source);
        }
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.ARCHIVE_EXTRACTED,
                result.summary()));
        if (result.hardlinkFallbacks > 0) {
            ThothLog.w(LogCategory.ROOTFS,
                    "Hardlink fallback used count=" + result.hardlinkFallbacks
                            + " (Android SELinux forbids untrusted-app hardlinks)");
        }
        try {
            RootfsArchive.verify(result, image.sha256());
        } catch (IOException e) {
            ThothLog.w(LogCategory.SECURITY, "Rootfs archive refused: " + e.getMessage());
            throw e;
        }
        ThothLog.i(LogCategory.ROOTFS, timeline.mark(SetupTimeline.Stage.ARCHIVE_VERIFIED,
                "sha256=" + result.sha256));
        // The extraction stage ends here; the bar reaches 100 before any later stage.
        publishProgress(expectedSize, expectedSize, result.entries);

        for (String entry : GardenRuntime.GUEST_ENTRY_POINTS) {
            File resolved = GuestPaths.resolve(fileOps, stagingDir, entry, true);
            if (resolved == null || fileOps.type(resolved) != FileOps.Type.REGULAR) {
                throw new IOException("Extracted rootfs is incomplete: no " + entry);
            }
        }
        if (fileOps.type(new File(stagingDir, "etc/os-release")) == FileOps.Type.NONE) {
            throw new IOException("Extracted rootfs is incomplete: no /etc/os-release");
        }
        publish(appContext.getString(R.string.garden_finalizing, image.distroName()));
    }

    /** The F-Droid flavour's verified archive is not needed once installed. */
    private void deleteVerifiedDownload() {
        if (embeddedRootfs) return;
        try {
            if (fileOps.type(downloadedImage) == FileOps.Type.REGULAR) {
                fileOps.unlink(downloadedImage);
                ThothLog.i(LogCategory.STORAGE, "Downloaded rootfs archive removed after install");
            }
        } catch (IOException e) {
            ThothLog.w(LogCategory.STORAGE, "Cannot remove the downloaded rootfs archive");
        }
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

    /**
     * Refreshes app-managed integration without re-extracting or replacing
     * user data. Called on the UI thread for every new window, so it only
     * writes managed files: guest provisioning (sudo, keyring) is queued in
     * the background and never delays the terminal.
     */
    public synchronized void prepareSession() throws IOException {
        if (!isReady()) throw new IOException("Linux environment is not ready");
        setupRootfs(rootfsDir, false);
        if (isPacman()) clearStalePacmanLock();
        if (sudoState(rootfsDir) == GuestConfig.PackageState.INSTALLED) {
            forceSudoSetuid(rootfsDir);
        }
        if (needsAdminTools()) scheduleAdminTools("session start", null);
    }

    private boolean isPacman() {
        return DistroInfo.PACMAN.equals(image.packageManager());
    }

    /**
     * pacman marks a running transaction with {@code db.lck} and removes it
     * when done, so a lock left by a killed transaction blocks every later
     * one. It is removed only when it is proven stale: no PRoot of this app is
     * running, and every guest process -- pacman included -- runs under a
     * PRoot that takes it down when it exits (PTRACE_O_EXITKILL). Otherwise
     * it is left alone. Removing the lock does not repair an interrupted
     * transaction; see the edition's PACKAGE_MANAGER.md for that.
     */
    private void clearStalePacmanLock() {
        File lock = new File(rootfsDir, "var/lib/pacman/db.lck");
        if (!fileOps.exists(lock)) return;
        int running = GuestProcesses.countProot(new File("/proc"), prootPath(), android.os.Process.myPid());
        if (running != 0) {
            ThothLog.i(LogCategory.ROOTFS, "pacman lock present while " + running
                    + " PRoot process(es) run; left in place");
            return;
        }
        try {
            fileOps.unlink(lock);
            ThothLog.w(LogCategory.ROOTFS, "Removed a stale pacman lock (no PRoot running);"
                    + " check pacman -Dk and pacman -Qk for an interrupted transaction");
        } catch (IOException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot remove the stale pacman lock");
        }
    }

    private boolean keyringMissing() {
        return isPacman() && !fileOps.isRegularFile(new File(rootfsDir, "etc/pacman.d/gnupg/trustdb.gpg"));
    }

    /** Creates a missing keyring now: optional setup only, on its background thread. */
    private void ensurePacmanKeyringNow() throws IOException {
        if (!keyringMissing()) return;
        runProvisioning(rootfsDir, pacmanKeyringScript(image));
        ThothLog.i(LogCategory.ROOTFS, "pacman keyring recreated");
    }

    /**
     * The installation's own pacman keyring, as the distribution documents
     * it: pacman-key --init (a fresh local master key, from the kernel's
     * random source) and --populate with the distribution's keyring package
     * data. Then two proofs, both offline: every package-signing key is fully
     * valid, and a genuine signed repository package kept in the image for
     * this purpose verifies with it. Any other result fails the script.
     *
     * <p>An edition with one signing identity gets the script it always had.
     * With several (an edition adding a third-party repository to its base
     * distribution) every keyring is populated, every key must be fully
     * valid, every file in the signature-check directory must be signed by
     * one of them, and each of them must have signed at least one.
     */
    static String pacmanKeyringScript(DistroInfo image) {
        java.util.List<String> keys = image.packageSigningKeys();
        StringBuilder script = new StringBuilder()
                .append("set -e\n")
                .append(PATH_EXPORT).append("\n")
                .append("export LANG=C.UTF-8\n")
                .append("cd /\n")
                .append("K=/etc/pacman.d/gnupg\n")
                .append("rm -rf \"$K\"\n")
                .append("pacman-key --init\n")
                .append("pacman-key --populate ").append(String.join(" ", image.pacmanKeyrings())).append("\n")
                // pacman-key's own options: its keyring directory is 0755 by
                // design, which plain gpg would warn about.
                .append("G=\"gpg --homedir $K --no-permission-warning --batch\"\n");
        for (String key : keys) {
            script.append("$G --with-colons --list-keys ").append(key)
                    .append(" | grep -q '^pub:[fu]:' || { echo 'signing key is not fully valid' >&2; exit 1; }\n");
        }
        if (keys.size() == 1) {
            String key = keys.get(0);
            script.append("set -- /usr/share/thothterm/signature-check/*.sig\n")
                    .append("[ -f \"$1\" ] || { echo 'no signature-check package' >&2; exit 1; }\n")
                    .append("$G --status-fd 1 --verify \"$1\" \"${1%.sig}\"")
                    .append(" > /tmp/.thothterm-verify 2>/dev/null || true\n")
                    .append("grep -q '^\\[GNUPG:\\] VALIDSIG ").append(key).append(" ' /tmp/.thothterm-verify")
                    .append(" && grep -qE '^\\[GNUPG:\\] TRUST_(FULLY|ULTIMATE)' /tmp/.thothterm-verify")
                    .append(" || { cat /tmp/.thothterm-verify >&2; rm -f /tmp/.thothterm-verify; exit 1; }\n");
        } else {
            String all = String.join(" ", keys);
            script.append("set -- /usr/share/thothterm/signature-check/*.sig\n")
                    .append("[ -f \"$1\" ] || { echo 'no signature-check package' >&2; exit 1; }\n")
                    .append("SIGNERS=\n")
                    .append("for S in \"$@\"; do\n")
                    .append("$G --status-fd 1 --verify \"$S\" \"${S%.sig}\"")
                    .append(" > /tmp/.thothterm-verify 2>/dev/null || true\n")
                    .append("SIGNER=$(sed -n 's/^\\[GNUPG:\\] VALIDSIG \\([0-9A-F]*\\) .*/\\1/p' /tmp/.thothterm-verify | head -n 1)\n")
                    .append("case \" ").append(all).append(" \" in *\" $SIGNER \"*) ;; *)")
                    .append(" cat /tmp/.thothterm-verify >&2; rm -f /tmp/.thothterm-verify; exit 1 ;; esac\n")
                    .append("grep -qE '^\\[GNUPG:\\] TRUST_(FULLY|ULTIMATE)' /tmp/.thothterm-verify")
                    .append(" || { cat /tmp/.thothterm-verify >&2; rm -f /tmp/.thothterm-verify; exit 1; }\n")
                    .append("SIGNERS=\"$SIGNERS $SIGNER \"\n")
                    .append("done\n");
            for (String key : keys) {
                script.append("case \"$SIGNERS\" in *\" ").append(key).append(" \"*) ;; *)")
                        .append(" echo 'no signature-check package signed by ").append(key)
                        .append("' >&2; rm -f /tmp/.thothterm-verify; exit 1 ;; esac\n");
            }
        }
        return script.append("rm -f /tmp/.thothterm-verify\n")
                .append("echo keyring-verified\n").toString();
    }

    // ---- managed guest configuration ---------------------------------------

    private void setupRootfs(File root, boolean installSkeleton) throws IOException {
        File home = guestDir(root, "/home/thoth");

        File skel = new File(root, "etc/skel");
        if (installSkeleton && fileOps.isDirectory(skel)) {
            copySkeleton(skel, home);
        }

        managed("bash integration", () -> {
            File bashrc = new File(home, ".bashrc");
            writeGuest(root, bashrc,
                    ShellIntegration.updateBashrc(ManagedFiles.read(fileOps, root, bashrc)));
        });

        setupUserAccount(root);
        removeManagedSudoHelper(root);
        setupSudo(root);

        File profileDir = guestDir(root, "/etc/profile.d");
        managed("locale fix", () -> writeGuest(root, new File(profileDir, "01-locale-fix.sh"),
                GuestConfig.localeFixScript()));

        if (!isPacman()) setupDebconfFrontend(root);
        File managedDir = guestDir(root, "/etc/thothterm");
        managed("runtime config version", () -> writeRuntimeConfigVersion(root));

        copyManagedAsset(root, "linux/thothterm-garden.sh",
                new File(profileDir, "thothterm-garden.sh"), -1);

        File binDir = guestDir(root, "/usr/local/bin");
        copyManagedAsset(root, "linux/thothfetch", new File(binDir, "thothfetch"), 0755);
        copyManagedAsset(root, "linux/fastfetch-fit", new File(binDir, "fastfetch-fit"), 0755);

        // Read by thothfetch; the edition name is validated metadata, one line.
        managed("edition", () -> writeGuest(root, new File(managedDir, "edition"),
                image.editionName() + "\n"));
        // Read as data by thothfetch and the managed prompt.
        File paletteFile = new File(managedDir, "palette");
        if (palette != null) {
            managed("palette", () -> writeGuest(root, paletteFile, palette.guestFile()));
        } else if (fileOps.isRegularFile(paletteFile)) {
            fileOps.unlink(paletteFile);
        }
        File welcomeEnabled = new File(managedDir, "welcome-enabled");
        boolean showWelcome = PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("garden_show_welcome", true);
        if (showWelcome) {
            managed("welcome", () -> writeGuest(root, welcomeEnabled, "enabled\n"));
        } else if (fileOps.isRegularFile(welcomeEnabled)) {
            fileOps.unlink(welcomeEnabled);
        }

        managed("hostname", () -> {
            File hostname = new File(root, "etc/hostname");
            String current = ManagedFiles.read(fileOps, root, hostname);
            if (current.isEmpty() || "android".equals(current.trim())) {
                writeGuest(root, hostname, "thothterm\n");
            }
        });
        managed("hosts", () -> {
            File hosts = new File(root, "etc/hosts");
            writeGuest(root, hosts, GuestConfig.ensureHosts(ManagedFiles.read(fileOps, root, hosts)));
        });
        managed("Android groups", () -> {
            File group = new File(root, "etc/group");
            writeGuest(root, group, GuestConfig.ensureGroups(
                    ManagedFiles.read(fileOps, root, group), android.os.Process.myUid()));
        });
        File resolv = new File(root, "etc/resolv.conf");
        if (fileOps.isSymlink(resolv)) {
            // PRoot bind-mounts the Android resolver over this path; it must be
            // a plain file, never a link into the guest or out of it.
            fileOps.unlink(resolv);
            writeGuest(root, resolv, "# Runtime resolver is bind-mounted by ThothTerm.\n");
        }
        File tmp = guestDir(root, "/tmp");
        fileOps.setMode(tmp, 01777);
    }

    private void setupUserAccount(File root) throws IOException {
        File passwd = new File(root, "etc/passwd");
        if (guestIsFile(root, passwd)) {
            managed("/etc/passwd", () -> writeGuest(root, passwd,
                    GuestConfig.ensurePasswd(ManagedFiles.read(fileOps, root, passwd))));
        }

        File group = new File(root, "etc/group");
        if (guestIsFile(root, group)) {
            managed("/etc/group", () -> writeGuest(root, group, GuestConfig.ensureGroup(
                    ManagedFiles.read(fileOps, root, group), image.adminGroup())));
        }

        File shadow = new File(root, "etc/shadow");
        if (guestIsFile(root, shadow)) {
            managed("/etc/shadow", () -> writeGuest(root, shadow,
                    GuestConfig.ensureShadow(ManagedFiles.read(fileOps, root, shadow))));
        }
    }

    private void setupSudo(File root) throws IOException {
        File sudoersDir = guestDir(root, "/etc/sudoers.d");
        File thothSudoers = new File(sudoersDir, "thoth");
        managed("sudoers entry", () -> writeGuest(root, thothSudoers,
                GuestConfig.sudoersEntry(), 0440));

        sudoersReadmeMode(root);

        // The real sudo is the only elevation path; drop the v2 passwordless
        // su customization so su returns to its stock policy.
        File pamSu = new File(root, "etc/pam.d/su");
        if (guestIsFile(root, pamSu)) {
            managed("/etc/pam.d/su", () -> writeGuest(root, pamSu,
                    GuestConfig.removePasswordlessSu(ManagedFiles.read(fileOps, root, pamSu))));
        }
    }

    /**
     * visudo -c requires the default sudoers files to be mode 0440. dpkg runs
     * under PRoot's fake root, which keeps owner write on every file so the
     * fake root can still change it, so sudo-common's README arrives 0640
     * and a chmod inside the guest gets the same treatment. The app sets the
     * real mode from outside, through one no-follow descriptor: after every
     * session start and right after sudo is installed, before it is verified.
     */
    private void sudoersReadmeMode(File root) throws IOException {
        File sudoersReadme = new File(guestDir(root, "/etc/sudoers.d"), "README");
        if (fileOps.isRegularFile(sudoersReadme)) {
            fileOps.setMode(sudoersReadme, 0440);
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
        if (!fileOps.isRegularFile(helper)) return;
        try {
            if (GuestConfig.isManagedSudoHelper(ManagedFiles.read(fileOps, root, helper))) {
                try {
                    fileOps.unlink(helper);
                    ThothLog.i(LogCategory.INSTALLER,
                            "Removed managed su-backed sudo helper");
                } catch (IOException e) {
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
        File debconfDir = guestDir(root, "/var/cache/debconf");

        File configDat = new File(debconfDir, "config.dat");
        if (guestIsFile(root, configDat)) {
            managed("debconf config.dat", () -> writeGuest(root, configDat,
                    GuestConfig.ensureDebconfFrontendConfig(
                            ManagedFiles.read(fileOps, root, configDat))));
        }

        File templatesDat = new File(debconfDir, "templates.dat");
        if (guestIsFile(root, templatesDat)) {
            managed("debconf templates.dat", () -> writeGuest(root, templatesDat,
                    GuestConfig.ensureDebconfFrontendTemplate(
                            ManagedFiles.read(fileOps, root, templatesDat))));
        }
    }

    /** Records the managed guest-configuration schema applied to this rootfs. */
    private void writeRuntimeConfigVersion(File root) throws IOException {
        File managedDir = guestDir(root, "/etc/thothterm");
        writeGuest(root, new File(managedDir, "runtime-config-version"),
                GuestConfig.RUNTIME_CONFIG_VERSION + "\n");
    }

    // ---- administrator tools (sudo) -------------------------------------------

    /** True when sudo still needs work that may need the network. */
    private boolean needsAdminTools() {
        return sudoState(rootfsDir) != GuestConfig.PackageState.INSTALLED || keyringMissing();
    }

    /**
     * Queues optional provisioning -- a missing pacman keyring, and
     * administrator tools: the setuid bit on an installed sudo, finishing an
     * interrupted configuration, installing sudo from the distribution's
     * archive (network) -- on one background thread, after the terminal is
     * ready. Calls while one is queued or running are coalesced. It never
     * blocks a terminal; until it finishes, {@code sudo} may be unavailable. A
     * failure is reported through {@link #optionalSetup()} and retried on the
     * next session or by the user; it never re-extracts and never touches
     * {@code /home}.
     *
     * @param timeline the first-run timeline, or null after a later session
     */
    private void scheduleAdminTools(String reason, SetupTimeline timeline) {
        if (!optionalSetup.queue()) return;
        ThothLog.i(LogCategory.ROOTFS, "Optional setup queued in the background (" + reason
                + ") state=" + SetupState.OPTIONAL_SETUP_PENDING);
        background.execute(() -> runOptionalSetup(reason, timeline));
    }

    private void runOptionalSetup(String reason, SetupTimeline timeline) {
        optionalSetup.started();
        ThothLog.i(LogCategory.ROOTFS, optionalMark(timeline,
                SetupTimeline.Stage.OPTIONAL_SETUP_STARTED, "reason=" + reason));
        long start = System.nanoTime();
        boolean ok = false;
        String why = null;
        try {
            if (keyringMissing()) {
                try {
                    ensurePacmanKeyringNow();
                    ThothLog.i(LogCategory.ROOTFS, optionalMark(timeline,
                            SetupTimeline.Stage.KEYRING_PROVISIONED, ""));
                } catch (Throwable t) {
                    ThothLog.e(LogCategory.ROOTFS, "pacman keyring creation failed type="
                            + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
                }
            }
            ensureRealSudo(rootfsDir);
            GuestConfig.PackageState sudo = sudoState(rootfsDir);
            boolean keyring = !keyringMissing();
            ok = sudo == GuestConfig.PackageState.INSTALLED && keyring;
            if (!ok) why = "sudo=" + sudo + (keyring ? "" : " keyring=missing");
        } catch (Throwable t) {
            why = t.getClass().getSimpleName();
            ThothLog.e(LogCategory.ROOTFS, "Optional setup failed type="
                    + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            optionalSetup.finished(ok, why);
            String detail = "ms=" + ms + " sudo=" + sudoState(rootfsDir)
                    + (ok ? "" : " reason=" + why) + " state=" + setupState();
            if (ok) {
                ThothLog.i(LogCategory.ROOTFS, optionalMark(timeline,
                        SetupTimeline.Stage.OPTIONAL_SETUP_FINISHED, detail));
            } else {
                ThothLog.w(LogCategory.ROOTFS, optionalMark(timeline,
                        SetupTimeline.Stage.OPTIONAL_SETUP_FAILED, detail));
            }
        }
    }

    private static String optionalMark(SetupTimeline timeline, SetupTimeline.Stage stage,
                                       String detail) {
        return timeline != null ? timeline.mark(stage, detail)
                : SetupTimeline.unscheduled(stage, detail);
    }

    /**
     * Ensures the distribution's own {@code sudo} package is installed and that
     * PRoot's setuid-bit elevation can work. A Garden rootfs ships sudo, so this
     * normally only restores the setuid bit that extraction masks; a missing
     * sudo is installed from the distribution's archive. Best effort: any
     * failure is logged and retried on the next session. Runs only on the
     * background thread ({@link #scheduleAdminTools}).
     */
    private void ensureRealSudo(File root) {
        // Not the manager's monitor: prepareSession() takes that on the UI
        // thread and must never wait for a package install.
        synchronized (adminLock) {
            GuestConfig.PackageState state = sudoState(root);
            if (state == GuestConfig.PackageState.INSTALLED) {
                forceSudoSetuid(root);
                return;
            }
            if (state == GuestConfig.PackageState.UNFINISHED) {
                finishInterruptedSudo(root);
                return;
            }
            try {
                // Installed from the distribution's own archive, so apt verifies
                // it with the distribution's signing keys rather than us
                // re-implementing that check.
                runProvisioning(root, isPacman() ? sudoPacmanInstallScript() : sudoAptInstallScript());
                boolean setuid = forceSudoSetuid(root);
                sudoersReadmeMode(root);
                String report = runProvisioning(root, sudoVerifyScript());
                ThothLog.i(LogCategory.ROOTFS, "sudo provisioning complete setuid="
                        + setuid + " report=" + report.replace('\n', ' ').trim());
            } catch (Throwable t) {
                ThothLog.e(LogCategory.ROOTFS, "sudo provisioning failed type="
                        + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
            }
        }
    }

    /**
     * An interrupted dpkg run -- typically an upgrade -- left sudo unpacked
     * but not configured. Finish it in place, offline; installing a copy
     * would downgrade the newer version it unpacked.
     */
    private void finishInterruptedSudo(File root) {
        // Not the manager's monitor: prepareSession() takes that on the UI
        // thread and must never wait for a package install.
        synchronized (adminLock) {
            try {
                runProvisioning(root, dpkgConfigureScript());
                boolean setuid = forceSudoSetuid(root);
                sudoersReadmeMode(root);
                String report = runProvisioning(root, sudoVerifyScript());
                ThothLog.i(LogCategory.ROOTFS, "sudo configuration completed setuid="
                        + setuid + " report=" + report.replace('\n', ' ').trim());
            } catch (Throwable t) {
                ThothLog.e(LogCategory.ROOTFS, "sudo configuration failed type="
                        + t.getClass().getSimpleName() + " message=" + t.getMessage(), t);
            }
        }
    }

    /** Where sudo stands in the guest's package database. */
    private GuestConfig.PackageState sudoState(File root) {
        if (isPacman()) {
            return GuestConfig.pacmanPackageState(
                    new File(root, "var/lib/pacman/local").list(), "sudo");
        }
        File status = new File(root, "var/lib/dpkg/status");
        if (!fileOps.isRegularFile(status)) return GuestConfig.PackageState.ABSENT;
        try {
            return GuestConfig.packageState(ManagedFiles.read(fileOps, root, status), "sudo");
        } catch (IOException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot read dpkg status");
            return GuestConfig.PackageState.ABSENT;
        }
    }

    /**
     * Installs sudo from the configured distribution archive. apt checks the release
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
                + "dpkg --configure -a\n";
    }

    /**
     * Installs sudo with pacman from the repository databases the guest
     * already has. Never -Sy: refreshing the databases without upgrading the
     * rest is a partial upgrade, which Arch does not support. If the guest
     * has never synced, this fails, is logged and retried; the rootfs itself
     * ships sudo, so that only happens after a user removed it.
     */
    private String sudoPacmanInstallScript() {
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + "cd /\n"
                + "pacman -S --noconfirm --needed sudo\n";
    }

    /** Finishes whatever an interrupted dpkg run left unconfigured. */
    private String dpkgConfigureScript() {
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + "export DEBIAN_FRONTEND=noninteractive\n"
                + "cd /\n"
                + "dpkg --configure -a\n";
    }

    private String sudoVerifyScript() {
        return "set -e\n"
                + PATH_EXPORT + "\n"
                + (isPacman() ? "pacman -Q sudo\n" : "dpkg-query -W -f='${Status} ${Version}\\n' sudo\n")
                + "test -x /usr/bin/sudo\n"
                + "visudo -c\n";
    }

    private static final String PATH_EXPORT =
            "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    /**
     * Forces the real setuid bit on the edition's sudo binary from Java,
     * bypassing PRoot's chmod shim. PRoot's fake_id0 extension grants fake euid
     * 0 only when the executed binary carries S_ISUID, and the Android-side
     * extraction masks setuid bits, so this explicit chmod is the required
     * PRoot adjustment. The binary is resolved inside the guest and must be a
     * regular file; a symlink is never chmodded.
     */
    private boolean forceSudoSetuid(File root) {
        File sudo;
        try {
            sudo = GuestPaths.resolve(fileOps, root, "/" + image.sudoBinary(), true);
            if (sudo == null || fileOps.type(sudo) != FileOps.Type.REGULAR) return false;
        } catch (IOException e) {
            return false;
        }
        try {
            // Guest processes run meanwhile: one descriptor, never the path twice.
            return AndroidFileOps.ensureSetuidNoFollow(sudo, SUDO_SETUID_MODE);
        } catch (IOException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot chmod the sudo binary: " + e.getMessage());
            return false;
        }
    }

    /** Runs a one-shot fake-root PRoot command for offline provisioning. */
    private String runProvisioning(File root, String script) throws IOException {
        GardenRuntime runtime = GardenRuntime.from(this, "xterm-256color");
        List<String> argv = runtime.buildProvisioningArgv(
                Arrays.asList("/bin/sh", "-c", script));
        Map<String, String> env = runtime.buildProvisioningEnvironment();

        if (android.os.Looper.getMainLooper().isCurrentThread()) {
            // A guest command can take minutes: on the UI thread that is an ANR.
            throw new IllegalStateException("Provisioning must never run on the main thread");
        }
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(true);
        // Nothing answers a prompt: a command that reads stdin gets EOF at
        // once instead of waiting out the timeout on an open pipe.
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        builder.environment().clear();
        builder.environment().putAll(env);

        ThothLog.i(LogCategory.ROOTFS, "Provisioning command start");
        Process process = builder.start();
        final StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                String text = new String(readAll(process.getInputStream()), "UTF-8");
                synchronized (output) {
                    output.append(text);
                }
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
            killHard(process);
        }
        try {
            reader.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String report;
        synchronized (output) {
            report = output.toString();
        }
        if (!finished) {
            throw new IOException("Provisioning timed out after " + PROVISION_TIMEOUT_SECONDS
                    + " s; last output: " + tail(report));
        }
        int code = process.exitValue();
        ThothLog.d(LogCategory.ROOTFS, "Provisioning exit=" + code);
        if (code != 0) {
            throw new IOException("Provisioning command failed (" + code + "): "
                    + report.replace('\n', ' ').trim());
        }
        return report;
    }

    /**
     * PRoot dies by SIGKILL and PTRACE_O_EXITKILL takes its tracees with it. On
     * Android destroyForcibly() only sends SIGTERM, which PRoot survives while
     * its traced command hangs (measured: the hung guest outlived the timeout),
     * so the kill is sent explicitly once the polite one did not work.
     */
    private static void killHard(Process process) {
        process.destroyForcibly();
        try {
            if (process.waitFor(2, TimeUnit.SECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            java.lang.reflect.Field pid = process.getClass().getDeclaredField("pid");
            pid.setAccessible(true);
            android.system.Os.kill(pid.getInt(process), android.system.OsConstants.SIGKILL);
        } catch (ReflectiveOperationException | android.system.ErrnoException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot SIGKILL the hung provisioning command: " + e);
        }
    }

    /** The end of a command's output, on one line, for the log. */
    private static String tail(String report) {
        String line = report.replace('\n', ' ').trim();
        return line.length() <= 400 ? line : "..." + line.substring(line.length() - 400);
    }

    // ---- state, metadata, notification ----------------------------------------

    private void writeState() throws IOException {
        RootfsState state = new RootfsState();
        state.imageId = image.imageId();
        state.distroVersion = image.distroVersion();
        state.architecture = image.architecture();
        state.imageSha256 = image.sha256();
        state.schemaVersion = image.schemaVersion();
        state.installedAt = System.currentTimeMillis();
        state.complete = true;
        state.write(stateFile);
    }

    private GardenPalette loadPalette() {
        try {
            return GardenPalette.load(appContext.getAssets().open(GardenPalette.ASSET));
        } catch (Exception e) {
            ThothLog.w(LogCategory.ROOTFS, "No usable edition palette; banner and prompt stay plain");
            return null;
        }
    }

    private DistroInfo loadImage() {
        InputStream in = null;
        try {
            in = appContext.getAssets().open(DistroInfo.ASSET);
            return DistroInfo.load(in);
        } catch (Exception e) {
            ThothLog.e(LogCategory.ROOTFS, "Cannot read the edition's distro metadata", e);
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private boolean hasEmbeddedArchive(DistroInfo info) {
        try {
            String[] names = appContext.getAssets().list(ROOTFS_ASSET_DIR);
            return names != null && Arrays.asList(names).contains(info.assetName());
        } catch (IOException e) {
            return false;
        }
    }

    private void publish(String text) {
        message = text;
        Listener current = listener;
        if (current != null) current.onStatus(text);
    }

    private volatile int downloadPercent = -1;

    private void publishDownload(long doneBytes, long totalBytes) {
        int value = ExtractionProgress.percent(doneBytes, totalBytes);
        if (value <= downloadPercent && value != 100) return;
        downloadPercent = value;
        Listener current = listener;
        if (current != null) current.onDownloadProgress(value);
    }

    private void publishProgress(long consumedBytes, long totalBytes, long entryCount) {
        entries = entryCount;
        int value = ExtractionProgress.percent(consumedBytes, totalBytes);
        // 100 is reserved for "archive fully extracted and verified".
        if (value == 100 && consumedBytes < totalBytes) value = 99;
        if (value <= percent && value != 100) return;
        percent = value;
        Listener current = listener;
        if (current != null) current.onProgress(value, entryCount);
    }

    private void notifyStatus() {
        Listener current = listener;
        if (current == null) return;
        if (percent > 0) current.onProgress(percent, entries);
        if (!message.isEmpty()) current.onStatus(message);
    }

    private void notifyComplete() {
        Listener current = listener;
        if (current != null) current.onComplete();
    }

    private void notifyAttention(Condition condition) {
        Listener current = listener;
        if (current != null) current.onAttentionNeeded(condition);
    }

    private void notifyError() {
        Listener current = listener;
        if (current == null) return;
        String text = (lastError != null && lastError.getMessage() != null)
                ? lastError.getMessage()
                : "Linux environment could not be prepared.";
        current.onError(text, lastError);
    }

    // ---- guest file helpers ---------------------------------------------------

    private interface ManagedStep {
        void run() throws IOException;
    }

    /**
     * A file the user made unmanageable (not UTF-8, a link out of the
     * rootfs, a directory) is left as it is and logged; it never blocks a
     * terminal. Any other I/O failure propagates.
     */
    private static void managed(String what, ManagedStep step) throws IOException {
        try {
            step.run();
        } catch (ManagedFiles.UnmanageableException e) {
            ThothLog.w(LogCategory.ROOTFS, "Left " + what + " unmanaged: " + e.getMessage());
        }
    }

    private void writeGuest(File root, File file, String text) throws IOException {
        ManagedFiles.writeIfChanged(fileOps, root, file, text, -1);
    }

    private void writeGuest(File root, File file, String text, int mode) throws IOException {
        ManagedFiles.writeIfChanged(fileOps, root, file, text, mode);
    }

    private boolean guestIsFile(File root, File file) throws IOException {
        File resolved = GuestPaths.resolve(fileOps, root, GuestPaths.guestPath(root, file), true);
        return resolved != null && fileOps.type(resolved) == FileOps.Type.REGULAR;
    }

    /** A guest directory, resolved inside the guest and created if missing. */
    private File guestDir(File root, String guestPath) throws IOException {
        File dir = GuestPaths.resolve(fileOps, root, guestPath, true);
        if (dir == null) throw new IOException("Symlink loop at " + guestPath);
        ManagedFiles.ensureDirectories(fileOps, root, dir);
        return dir;
    }

    private void copyManagedAsset(File root, String assetName, File destination, int mode)
            throws IOException {
        InputStream input = appContext.getAssets().open(assetName);
        String text;
        try {
            byte[] bytes = readAll(input);
            text = ManagedFiles.decode(bytes);
            if (text == null) throw new IOException("Managed asset is not UTF-8: " + assetName);
        } finally {
            input.close();
        }
        managed(assetName, () -> writeGuest(root, destination, text, mode));
    }

    /** Copies the image's skeleton into a new home; nothing is followed. */
    private void copySkeleton(File source, File destination) throws IOException {
        String[] names = source.list();
        if (names == null) return;
        for (String name : names) {
            File from = new File(source, name);
            File to = new File(destination, name);
            FileOps.Type type = fileOps.type(from);
            if (fileOps.type(to) != FileOps.Type.NONE) continue;
            if (type == FileOps.Type.DIRECTORY) {
                fileOps.mkdir(to, fileOps.permissions(from) & 0777);
                copySkeleton(from, to);
            } else if (type == FileOps.Type.SYMLINK) {
                fileOps.symlink(fileOps.readlink(from), to);
            } else if (type == FileOps.Type.REGULAR) {
                InputStream in = fileOps.openNoFollow(from);
                try {
                    OutputStream out = fileOps.createNew(to, fileOps.permissions(from) & 0777);
                    try {
                        copyStream(in, out);
                    } finally {
                        out.close();
                    }
                } finally {
                    in.close();
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        copyStream(in, out);
        return out.toByteArray();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    static String toHex(byte[] bytes) {
        return RootfsArchive.toHex(bytes);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
