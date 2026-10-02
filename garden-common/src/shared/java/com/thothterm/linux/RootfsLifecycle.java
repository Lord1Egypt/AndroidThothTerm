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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The lifecycle of an installed Linux environment, shared by every edition.
 *
 * <p>The rule it enforces: <b>only a genuinely never-installed environment is
 * initialized automatically</b>. Anything that finds an existing rootfs --
 * a missing shell, missing app runtime files, a lost state file, a moved
 * rootfs pin -- is classified, and at most repaired without touching guest
 * data. Replacing the system requires an explicit, user-confirmed reset, and
 * even that moves the existing {@code /home} into the new system with
 * {@code rename(2)} instead of deleting it.</p>
 *
 * <p>Pure Java so that every transition, including a crash at each step of a
 * reset, is unit-tested.</p>
 */
public final class RootfsLifecycle {

    /** What the app finds when it looks at its Linux environment. */
    public enum Condition {
        /** No rootfs, no state, no interrupted reset: first run. Auto-install. */
        NOT_INSTALLED,
        /** Installed, its entry points present, app runtime present. */
        INSTALLED_HEALTHY,
        /**
         * A rootfs exists but guest files the terminal needs are missing (for
         * example after a broken package transaction). Never repaired
         * automatically; the user decides.
         */
        INSTALLED_DAMAGED,
        /**
         * The guest is fine but app-owned runtime files outside the rootfs are
         * missing. Re-staged from the APK automatically; no guest data is
         * touched.
         */
        APP_RUNTIME_DAMAGED,
        /**
         * A rootfs with its entry points exists but the completion record is
         * missing or unreadable (a crash right after first extraction, or a
         * lost state file). Finished in place without re-extracting.
         */
        REPAIR_REQUIRED,
        /**
         * The user confirmed "reinstall the system, keep /home". Runs only
         * because of that confirmation; the marker survives a restart.
         */
        EXPLICIT_RESET_REQUESTED
    }

    /** Where everything lives, below one per-edition directory. */
    public static final class Layout {
        public final File linuxDir;
        public final File rootfs;
        public final File staging;
        /** The old system during a reset, until /home has moved out of it. */
        public final File previous;
        public final File stateFile;
        public final File resetMarker;

        public Layout(File linuxDir) {
            this.linuxDir = linuxDir;
            this.rootfs = new File(linuxDir, "rootfs");
            this.staging = new File(linuxDir, "rootfs.staging");
            this.previous = new File(linuxDir, "rootfs.previous");
            this.stateFile = new File(linuxDir, "state.properties");
            this.resetMarker = new File(linuxDir, "reset.requested");
        }

        /** Written into staging only once staging is complete and configured. */
        public File stagingCompleteMarker() {
            return new File(staging, STAGING_COMPLETE);
        }
    }

    /** Name of the marker inside a fully prepared staging tree. */
    public static final String STAGING_COMPLETE = ".thothterm-staging-complete";
    /** What a reset carries from the old system into the new one. */
    public static final String PRESERVED_DIR = "home";

    /** Observations the decision is made from; gathered by {@link #probe}. */
    public static final class Facts {
        public boolean stateInstalled;
        public boolean rootfsPresent;
        public boolean resetRequested;
        public boolean resetInProgress;
        public final List<String> missingGuestFiles = new ArrayList<>();
        public final List<String> missingRuntimeFiles = new ArrayList<>();

        @Override
        public String toString() {
            return "stateInstalled=" + stateInstalled + " rootfsPresent=" + rootfsPresent
                    + " resetRequested=" + resetRequested + " resetInProgress=" + resetInProgress
                    + " missingGuest=" + missingGuestFiles + " missingRuntime=" + missingRuntimeFiles;
        }
    }

    private RootfsLifecycle() {
    }

    /**
     * Looks without changing anything.
     *
     * @param guestEntryPoints guest paths the terminal executes, e.g.
     *                         {@code /bin/bash}; resolved inside the rootfs
     * @param runtimeFiles     app-owned files outside the rootfs
     */
    public static Facts probe(FileOps ops, Layout layout, boolean stateInstalled,
                              List<String> guestEntryPoints, List<File> runtimeFiles)
            throws IOException {
        Facts facts = new Facts();
        facts.stateInstalled = stateInstalled;
        facts.rootfsPresent = ops.type(layout.rootfs) == FileOps.Type.DIRECTORY;
        facts.resetRequested = ops.type(layout.resetMarker) == FileOps.Type.REGULAR;
        facts.resetInProgress = ops.type(layout.previous) != FileOps.Type.NONE;
        if (facts.rootfsPresent) {
            for (String entry : guestEntryPoints) {
                File resolved = GuestPaths.resolve(ops, layout.rootfs, entry, true);
                if (resolved == null || ops.type(resolved) != FileOps.Type.REGULAR) {
                    facts.missingGuestFiles.add(entry);
                }
            }
        }
        for (File file : runtimeFiles) {
            if (ops.type(file) != FileOps.Type.REGULAR) {
                facts.missingRuntimeFiles.add(file.getName());
            }
        }
        return facts;
    }

    /** The decision. Pure; see the class comment for the rule. */
    public static Condition assess(Facts f) {
        if (f.resetRequested || f.resetInProgress) return Condition.EXPLICIT_RESET_REQUESTED;
        if (!f.rootfsPresent) {
            // A state file without a rootfs is not "never installed": the
            // user's data is gone already, but an automatic reinstall would
            // hide that. Let the user decide.
            return f.stateInstalled ? Condition.INSTALLED_DAMAGED : Condition.NOT_INSTALLED;
        }
        if (!f.missingGuestFiles.isEmpty()) return Condition.INSTALLED_DAMAGED;
        if (!f.stateInstalled) return Condition.REPAIR_REQUIRED;
        if (!f.missingRuntimeFiles.isEmpty()) return Condition.APP_RUNTIME_DAMAGED;
        return Condition.INSTALLED_HEALTHY;
    }

    /** True for the only condition that may extract without the user deciding. */
    public static boolean mayInstallAutomatically(Condition condition) {
        return condition == Condition.NOT_INSTALLED;
    }

    /**
     * First install: staging becomes the rootfs. Refuses if anything already
     * exists at the rootfs path -- a first install never replaces anything.
     */
    public static void promoteFreshInstall(FileOps ops, Layout layout) throws IOException {
        requireCompleteStaging(ops, layout);
        if (ops.type(layout.rootfs) != FileOps.Type.NONE) {
            throw new IOException("Refusing to replace an existing rootfs during a first install");
        }
        if (ops.type(layout.previous) != FileOps.Type.NONE) {
            throw new IOException("An interrupted reset must be resolved first");
        }
        ops.rename(layout.staging, layout.rootfs);
        removeMarkerFromRootfs(ops, layout);
    }

    /**
     * Explicit reset, after the user confirmed it and staging holds a complete,
     * verified, configured new system. Each step is one {@code rename(2)}, and
     * {@link #recover} completes or rolls back from a crash at any point:
     *
     * <ol>
     *   <li>rootfs -> rootfs.previous</li>
     *   <li>staging/home (fresh skeleton) removed; rootfs.previous/home ->
     *       staging/home</li>
     *   <li>staging -> rootfs</li>
     *   <li>the old system, which no longer contains /home, is deleted</li>
     * </ol>
     *
     * <p>The existing /home is never copied and never deleted: it is moved.</p>
     */
    public static void replaceSystemKeepingHome(FileOps ops, Layout layout) throws IOException {
        requireCompleteStaging(ops, layout);
        if (ops.type(layout.previous) != FileOps.Type.NONE) {
            throw new IOException("An earlier reset is unresolved: " + layout.previous);
        }
        if (ops.type(layout.rootfs) == FileOps.Type.DIRECTORY) {
            ops.rename(layout.rootfs, layout.previous);
        } else if (ops.type(layout.rootfs) != FileOps.Type.NONE) {
            throw new IOException("The rootfs path is not a directory: " + layout.rootfs);
        }
        finishReplace(ops, layout);
    }

    /**
     * Completes or rolls back an interrupted {@link #replaceSystemKeepingHome}.
     * Never deletes a /home. Returns true when a reset was completed (the
     * caller then records the new state), false when nothing was pending or
     * the old system was restored.
     */
    public static boolean recover(FileOps ops, Layout layout) throws IOException {
        FileOps.Type previousType = ops.type(layout.previous);
        if (previousType == FileOps.Type.NONE) return false;
        if (previousType != FileOps.Type.DIRECTORY) {
            throw new IOException("Unexpected entry at " + layout.previous);
        }
        boolean rootfsPresent = ops.type(layout.rootfs) == FileOps.Type.DIRECTORY;
        File oldHome = new File(layout.previous, PRESERVED_DIR);
        if (rootfsPresent) {
            // Step 3 happened. The new system is in place; the old one must
            // no longer hold /home before it may be deleted.
            if (ops.type(oldHome) != FileOps.Type.NONE) {
                File kept = new File(layout.linuxDir,
                        "rootfs.previous-" + System.currentTimeMillis());
                ops.rename(layout.previous, kept);
                throw new IOException("An old system still holding /home was kept at " + kept);
            }
            removeMarkerFromRootfs(ops, layout);
            SafeFileTree.deleteTree(ops, layout.previous, layout.previous);
            return true;
        }
        boolean stagingComplete =
                ops.type(layout.stagingCompleteMarker()) == FileOps.Type.REGULAR;
        if (stagingComplete) {
            finishReplace(ops, layout);
            return true;
        }
        // The new system never finished: put the old one back as it was.
        if (ops.type(oldHome) == FileOps.Type.NONE
                && ops.type(new File(layout.staging, PRESERVED_DIR)) == FileOps.Type.DIRECTORY) {
            // Only possible if staging lost its marker after home moved in:
            // move home back before restoring the old system.
            ops.rename(new File(layout.staging, PRESERVED_DIR), oldHome);
        }
        ops.rename(layout.previous, layout.rootfs);
        if (ops.type(layout.staging) != FileOps.Type.NONE) {
            SafeFileTree.deleteTree(ops, layout.linuxDir, layout.staging);
        }
        return false;
    }

    private static void finishReplace(FileOps ops, Layout layout) throws IOException {
        File oldHome = new File(layout.previous, PRESERVED_DIR);
        File newHome = new File(layout.staging, PRESERVED_DIR);
        if (ops.type(oldHome) == FileOps.Type.DIRECTORY) {
            if (ops.type(newHome) != FileOps.Type.NONE) {
                // The fresh skeleton home of the new image; the user's moves in.
                SafeFileTree.deleteTree(ops, layout.staging, newHome);
            }
            ops.rename(oldHome, newHome);
        }
        ops.rename(layout.staging, layout.rootfs);
        removeMarkerFromRootfs(ops, layout);
        if (ops.type(oldHome) != FileOps.Type.NONE) {
            throw new IOException("/home did not move out of the old system");
        }
        SafeFileTree.deleteTree(ops, layout.previous, layout.previous);
    }

    /** The staging marker travels with the rename; it does not belong in the guest. */
    private static void removeMarkerFromRootfs(FileOps ops, Layout layout) throws IOException {
        File marker = new File(layout.rootfs, STAGING_COMPLETE);
        if (ops.type(marker) == FileOps.Type.REGULAR) ops.unlink(marker);
    }

    private static void requireCompleteStaging(FileOps ops, Layout layout) throws IOException {
        if (ops.type(layout.staging) != FileOps.Type.DIRECTORY
                || ops.type(layout.stagingCompleteMarker()) != FileOps.Type.REGULAR) {
            throw new IOException("Staging is not a complete, verified system");
        }
    }

    /** Marks staging complete; call only after verification and configuration. */
    public static void markStagingComplete(FileOps ops, Layout layout) throws IOException {
        if (ops.type(layout.stagingCompleteMarker()) == FileOps.Type.NONE) {
            ops.createNew(layout.stagingCompleteMarker(), 0600).close();
        }
    }
}
