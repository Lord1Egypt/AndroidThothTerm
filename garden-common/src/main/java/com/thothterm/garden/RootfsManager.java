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

package com.thothterm.garden;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.thothterm.R;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * Owns the distro root filesystem: first-run installation, and the managed
 * guest configuration refreshed before every session.
 *
 * <p>Installation is transactional. The archive is extracted into
 * {@code rootfs.staging} while its SHA-256 is computed from the same bytes; the
 * guest is configured there; the directory is then renamed to {@code rootfs};
 * and only after that is the state file written. {@link #isReady()} requires the
 * state file to match the pinned image, so an interrupted or failed install is
 * never mistaken for a healthy one, and the next attempt starts from a clean
 * staging directory.
 *
 * <p>Which distro, which archive and which guest policy all come from
 * {@link GardenDistro}. The archive is embedded when this build packages
 * {@link GardenDistro#rootfsAsset()}; otherwise it is downloaded from the
 * pinned URL only after the user has agreed, and verified before extraction.
 */
public final class RootfsManager {
    public interface Listener {
        void onStatus(String message);

        void onProgress(int percent, long entries);

        void onComplete();

        void onError(String message, Throwable cause);
    }

    private static final String GARDEN_ROOT = "garden";
    /** Must match RUNTIME_DIR in garden-common/tools/build-proot.sh. */
    private static final String RUNTIME_DIR = "runtime";
    private static final String RUNTIME_ASSET_DIR = "runtime/arm64-v8a";
    private static final String PROFILE_ASSET = "garden/thothterm-garden.sh";
    /** PRoot's fake root elevates only on the setuid bit; keep it on sudo. */
    private static final int SUDO_SETUID_MODE = 04755;

    /**
     * Shared objects PRoot links against, staged next to it so the dynamic
     * loader finds them. Must match what {@code tools/build-proot.sh} produces
     * and what {@code readelf -d libproot.so} reports as NEEDED.
     */
    private static final String[] RUNTIME_LIBS = {
            "libtalloc.so.2",
            "libandroid-shmem.so"
    };

    private static volatile RootfsManager sInstance;

    private final Context appContext;
    private final GardenDistro distro;
    private final boolean embedded;
    private final File distroDir;
    private final File rootfsDir;
    private final File stagingDir;
    /** Verified archive for builds that do not embed one; unused otherwise. */
    private final File downloadedImage;
    private final File stateFile;
    private final String prootRootfsPath;
    private final File runtimeLibDir;
    private final File prootTmpDir;
    private final File nativeLibDir;
    private final FileOps fileOps = new AndroidFileOps();

    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean failed;
    private volatile String message = "";
    private volatile int percent;
    private volatile long entries;
    private volatile Throwable lastError;

    private RootfsManager(Context context, GardenDistro distro) {
        this.appContext = context.getApplicationContext();
        this.distro = distro;
        this.embedded = hasAsset(appContext, distro.rootfsAsset());
        File gardenDir = new File(appContext.getFilesDir(), GARDEN_ROOT);
        this.distroDir = new File(gardenDir, distro.id());
        this.rootfsDir = new File(distroDir, "rootfs");
        this.prootRootfsPath = canonicalPath(rootfsDir);
        this.stagingDir = new File(distroDir, "rootfs.staging");
        this.downloadedImage = new File(distroDir, "rootfs-download.tar.gz");
        this.stateFile = new File(distroDir, "state.properties");
        File runtimeDir = new File(gardenDir, RUNTIME_DIR);
        this.runtimeLibDir = new File(runtimeDir, "lib");
        this.prootTmpDir = new File(runtimeDir, "tmp");
        this.nativeLibDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
    }

    /** Loads the edition's distro data; failure here means a broken build. */
    public static void init(Context context) {
        if (sInstance != null) return;
        synchronized (RootfsManager.class) {
            if (sInstance != null) return;
            try {
                sInstance = new RootfsManager(context,
                        GardenDistro.load(context.getAssets().open(GardenDistro.ASSET)));
            } catch (IOException e) {
                throw new IllegalStateException("Garden distro data is missing or invalid", e);
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

    private static boolean hasAsset(Context context, String asset) {
        if (asset == null || asset.isEmpty()) return false;
        try {
            context.getAssets().open(asset).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public GardenDistro distro() {
        return distro;
    }

    public boolean isSupportedDevice() {
        return DeviceArchitecture.isArm64Supported(android.os.Build.SUPPORTED_ABIS);
    }

    /**
     * The rootfs path to put on the PRoot command line. Inside an app's mount
     * namespace Android exposes {@code /data/user/0} as a symlink to
     * {@code /data/data}, so this is not the representation
     * {@link android.content.Context#getFilesDir()} reports. PRoot writes the
     * canonical form into every {@code --link2symlink} target and later compares
     * those targets against it as a string prefix, so the canonical form is what
     * must be passed, or links made in one session break in the next.
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
                            + " across sessions", e);
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

    public boolean isReady() {
        RootfsState state = RootfsState.read(stateFile);
        if (!state.matches(distro)) return false;
        if (!new File(rootfsDir, distro.shell().substring(1)).exists()) return false;
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
     * True when this build has no embedded rootfs and no verified download yet,
     * so the user must be asked before anything is downloaded.
     */
    public boolean needsImageDownload() {
        if (embedded || isReady()) return false;
        return !new RootfsDownloader(downloadedImage, distro).isVerified();
    }

    /** Megabytes to download, for the consent screen. */
    public int downloadSizeMb() {
        return (int) Math.max(1, (distro.compressedSize() + 524_288L) / 1_048_576L);
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
        new Thread(this::runPrepare, "ThothTerm-garden-rootfs").start();
    }

    /**
     * Supplies the archive bytes. Without an embedded archive this downloads
     * first; {@link RootfsDownloader} verifies the SHA-256 before the file is
     * promoted, so nothing unverified reaches the extractor.
     */
    private InputStream openImageStream() throws IOException {
        if (embedded) {
            return appContext.getAssets().open(distro.rootfsAsset());
        }
        RootfsDownloader downloader = new RootfsDownloader(downloadedImage, distro);
        if (!downloader.isVerified()) {
            publish(appContext.getString(R.string.garden_downloading, distro.displayName()));
            final long expected = distro.compressedSize();
            downloader.download((done, total) -> publishProgress(done, expected, 0));
            // Extraction reports its own 0..100; without this the monotonic
            // guard in publishProgress would swallow all of it.
            percent = 0;
        }
        return new FileInputStream(downloadedImage);
    }

    private void runPrepare() {
        try {
            String[] supportedAbis = android.os.Build.SUPPORTED_ABIS;
            if (!DeviceArchitecture.isArm64Supported(supportedAbis)) {
                ThothLog.e(LogCategory.RUNTIME, "ARM64 compatibility check failed supportedAbis="
                        + DeviceArchitecture.describe(supportedAbis));
                throw new IOException(appContext.getString(R.string.garden_requires_arm64,
                        distro.editionName()));
            }
            ThothLog.i(LogCategory.RUNTIME, "ARM64 compatibility verified");
            publish(appContext.getString(R.string.garden_preparing, distro.displayName()));
            copyRuntimeLibraries();
            extractRootfs();
            if (!embedded) deleteQuietly(downloadedImage);
            failed = false;
            notifyComplete();
        } catch (Throwable t) {
            failed = true;
            lastError = t;
            ThothLog.e(LogCategory.ROOTFS, "Installation failed type="
                    + t.getClass().getSimpleName(), t);
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
            SafeFileTree.deleteTree(fileOps, distroDir, stagingDir);
            ThothLog.i(LogCategory.ROOTFS, "Staging cleanup complete");
        } catch (Throwable cleanup) {
            ThothLog.e(LogCategory.ROOTFS, "Staging cleanup failed type="
                    + cleanup.getClass().getSimpleName(), cleanup);
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
            try (InputStream in = appContext.getAssets().open(RUNTIME_ASSET_DIR + "/" + name);
                 OutputStream out = new FileOutputStream(new File(runtimeLibDir, name))) {
                copyStream(in, out);
            }
        }
        ThothLog.d(LogCategory.ROOTFS, "Runtime libraries staged");
    }

    private void extractRootfs() throws Exception {
        ThothLog.i(LogCategory.ROOTFS, "Extraction started image=" + distro.rootfsId()
                + " embedded=" + embedded);
        SafeFileTree.deleteTree(fileOps, distroDir, stagingDir);
        if (stagingDir.exists()) {
            throw new IOException("Cannot clear staging directory");
        }
        if (!StorageSpace.isSufficient(usableSpace(), distro.uncompressedSize())) {
            ThothLog.w(LogCategory.STORAGE, "Insufficient storage for extraction");
            throw new IOException(appContext.getString(R.string.garden_no_space,
                    distro.displayName()));
        }
        if (!stagingDir.mkdirs()) {
            throw new IOException("Cannot create staging directory");
        }

        final long expectedSize = distro.compressedSize();
        InputStream source = openImageStream();
        publish(appContext.getString(R.string.garden_preparing, distro.displayName()));
        CountingInputStream counting = new CountingInputStream(source);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        java.security.DigestInputStream digesting =
                new java.security.DigestInputStream(counting, digest);
        TarballExtractor extractor = new TarballExtractor(
                fileOps, stagingDir,
                count -> publishProgress(counting.getCount(), expectedSize, count));
        try (GZIPInputStream gzip = new GZIPInputStream(digesting, 64 * 1024)) {
            extractor.extract(gzip);
            // Drain what the tar reader left unread so the digest covers the
            // whole file, trailing padding included.
            byte[] rest = new byte[64 * 1024];
            while (digesting.read(rest) > 0) {
                // digest only
            }
        }
        String actualSha = toHex(digest.digest());
        if (!actualSha.equals(distro.rootfsSha256())) {
            ThothLog.w(LogCategory.SECURITY, "Rootfs checksum mismatch");
            throw new IOException("The " + distro.displayName()
                    + " archive failed verification.");
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
        if (!new File(stagingDir, distro.shell().substring(1)).exists()
                || !new File(stagingDir, "etc/os-release").exists()) {
            throw new IOException("The extracted " + distro.displayName() + " is incomplete.");
        }

        setupRootfs(stagingDir, true);

        SafeFileTree.deleteTree(fileOps, distroDir, rootfsDir);
        if (!stagingDir.renameTo(rootfsDir)) {
            throw new IOException("Cannot finalize extracted rootfs");
        }
        writeState();
        ThothLog.i(LogCategory.ROOTFS, "Extraction complete");
    }

    @SuppressLint("UsableSpace") // Fallback when StorageManager cannot report a quota.
    private long usableSpace() {
        File volume = distroDir.getParentFile();
        if (volume != null && !volume.exists()) volume.mkdirs();
        StorageManager storage = (StorageManager) appContext.getSystemService(
                Context.STORAGE_SERVICE);
        if (storage != null && volume != null) {
            try {
                return storage.getAllocatableBytes(storage.getUuidForPath(volume));
            } catch (IOException | RuntimeException ignored) {
                // Fall through to the conservative filesystem value.
            }
        }
        return volume == null ? 0L : volume.getUsableSpace();
    }

    /** Refreshes app-managed integration without re-extracting or replacing user data. */
    public synchronized void prepareSession() throws IOException {
        if (!isReady()) throw new IOException("The distro is not installed");
        setupRootfs(rootfsDir, false);
    }

    private void setupRootfs(File root, boolean installSkeleton) throws IOException {
        File home = new File(root, GardenDistro.USER_HOME.substring(1));
        if (!home.exists() && !home.mkdirs()) {
            throw new IOException("Cannot create the guest home directory");
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
        copyManagedAsset(PROFILE_ASSET,
                new File(root, ShellIntegration.PROFILE_SCRIPT.substring(1)));
        setupDebconfFrontend(root);
        writeRuntimeConfigVersion(root);

        writeTextIfChanged(new File(root, "etc/hostname"), distro.hostname() + "\n");
        File hosts = new File(root, "etc/hosts");
        writeTextIfChanged(hosts, GuestConfig.ensureHosts(
                hosts.isFile() ? readText(hosts) : "", distro.hostname()));
        File group = new File(root, "etc/group");
        writeTextIfChanged(group, GuestConfig.ensureGroups(
                group.isFile() ? readText(group) : "", android.os.Process.myUid()));

        File resolv = new File(root, "etc/resolv.conf");
        if (fileOps.isSymlink(resolv)) {
            if (!resolv.delete()) throw new IOException("Cannot prepare resolver mount point");
        }
        if (!resolv.isFile()) {
            writeText(resolv, "# The runtime resolver is bind-mounted by ThothTerm.\n");
        }
        File tmp = new File(root, "tmp");
        if (!tmp.exists() && !tmp.mkdirs()) throw new IOException("Cannot create /tmp");
        fileOps.setMode(tmp, 01777);
    }

    private void setupUserAccount(File root) throws IOException {
        File passwd = new File(root, "etc/passwd");
        if (passwd.isFile()) {
            writeTextIfChanged(passwd, GuestConfig.ensurePasswd(readText(passwd), distro.shell()));
        }
        File group = new File(root, "etc/group");
        if (group.isFile()) {
            writeTextIfChanged(group, GuestConfig.ensureGroup(readText(group),
                    distro.sudoGroup(), distro.sudoGroupGid()));
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
        File thothSudoers = new File(sudoersDir, GardenDistro.USER);
        writeTextIfChanged(thothSudoers, GuestConfig.sudoersEntry());
        fileOps.setMode(thothSudoers, 0440);
        // sudo/visudo require every sudoers.d file to be mode 0440; the package
        // README is not a conffile and can arrive with a laxer mode.
        File sudoersReadme = new File(sudoersDir, "README");
        if (sudoersReadme.isFile()) {
            fileOps.setMode(sudoersReadme, 0440);
        }
        forceSudoSetuid(root);
    }

    private void forceSudoSetuid(File root) {
        File sudo = new File(root, distro.sudoPath().substring(1));
        if (!sudo.isFile()) return;
        try {
            Os.chmod(sudo.getAbsolutePath(), SUDO_SETUID_MODE);
            if ((Os.stat(sudo.getAbsolutePath()).st_mode & OsConstants.S_ISUID) == 0) {
                ThothLog.w(LogCategory.ROOTFS, "sudo setuid bit did not stick");
            }
        } catch (ErrnoException e) {
            ThothLog.w(LogCategory.ROOTFS, "Cannot chmod sudo errno=" + e.errno);
        }
    }

    private void setupDebconfFrontend(File root) throws IOException {
        File debconfDir = new File(root, "var/cache/debconf");
        if (!debconfDir.isDirectory()) return; // not a Debian-family guest
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
        writeTextIfChanged(new File(root, "etc/thothterm/runtime-config-version"),
                GuestConfig.RUNTIME_CONFIG_VERSION + "\n");
    }

    private void installBashIntegration(File bashrc) throws IOException {
        String original = bashrc.isFile() ? readText(bashrc) : "";
        writeTextIfChanged(bashrc, ShellIntegration.updateBashrc(original));
    }

    private void writeState() throws IOException {
        RootfsState state = new RootfsState();
        state.imageId = distro.rootfsId();
        state.distroVersion = distro.version();
        state.architecture = distro.architecture();
        state.imageSha256 = distro.rootfsSha256();
        state.schemaVersion = distro.schemaVersion();
        state.installedAt = System.currentTimeMillis();
        state.complete = true;
        state.write(stateFile);
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
                : appContext.getString(R.string.garden_failed, distro.displayName());
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
                try (InputStream in = new FileInputStream(child);
                     OutputStream out = new FileOutputStream(target)) {
                    copyStream(in, out);
                }
            }
        }
    }

    private void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
    }

    private void writeTextIfChanged(File file, String text) throws IOException {
        if (file.isFile() && text.equals(readText(file))) return;
        writeText(file, text);
    }

    private void copyManagedAsset(String assetName, File destination) throws IOException {
        try (InputStream input = appContext.getAssets().open(assetName)) {
            writeTextIfChanged(destination, readText(input));
        }
    }

    private String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readText(in);
        }
    }

    /** Whole-stream UTF-8 decode, so a multi-byte character is never split. */
    private static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copyStream(in, bytes);
        return bytes.toString("UTF-8");
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

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            ThothLog.w(LogCategory.STORAGE, "Cannot delete " + file.getName());
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
