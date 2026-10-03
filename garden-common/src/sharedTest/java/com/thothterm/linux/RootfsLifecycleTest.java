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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.thothterm.linux.RootfsLifecycle.Condition;
import com.thothterm.linux.RootfsLifecycle.Facts;
import com.thothterm.linux.RootfsLifecycle.Layout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The lifecycle never destroys /home: every damaged state is classified, never
 * auto-installed over; a reset moves /home, and a crash at any step of it is
 * recovered without losing it.
 */
public class RootfsLifecycleTest {
    private static final List<String> ENTRY = Arrays.asList("/bin/bash", "/usr/bin/su");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final JvmFileOps ops = new JvmFileOps();

    // ---- assessment --------------------------------------------------------

    @Test
    public void onlyAPristineDirectoryIsNotInstalled() throws Exception {
        Layout layout = new Layout(temporaryFolder.newFolder("pristine"));
        assertEquals(Condition.NOT_INSTALLED, assess(layout, false));
        assertTrue(RootfsLifecycle.mayInstallAutomatically(Condition.NOT_INSTALLED));
    }

    @Test
    public void nothingButNotInstalledMayInstallAutomatically() {
        for (Condition c : Condition.values()) {
            assertEquals(c.name(), c == Condition.NOT_INSTALLED,
                    RootfsLifecycle.mayInstallAutomatically(c));
        }
    }

    @Test
    public void healthyInstallIsHealthyWhateverThePin() throws Exception {
        Layout layout = installed("healthy");
        // The pin is not an input at all: a newer app with a newer pin sees
        // exactly the same facts.
        assertEquals(Condition.INSTALLED_HEALTHY, assess(layout, true));
    }

    @Test
    public void missingBashIsDamagedNotUninstalled() throws Exception {
        Layout layout = installed("nobash");
        Files.delete(new File(layout.rootfs, "usr/bin/bash").toPath());
        assertEquals(Condition.INSTALLED_DAMAGED, assess(layout, true));
        // Also when the completion record is gone as well.
        assertEquals(Condition.INSTALLED_DAMAGED, assess(layout, false));
    }

    @Test
    public void entryPointResolvedInsideTheGuest() throws Exception {
        Layout layout = installed("abslink");
        // An absolute guest symlink is resolved inside the rootfs, not on Android.
        Files.delete(new File(layout.rootfs, "usr/bin/su").toPath());
        Files.write(new File(layout.rootfs, "usr/bin/su-real").toPath(), new byte[]{1});
        Files.createSymbolicLink(new File(layout.rootfs, "usr/bin/su").toPath(),
                Paths.get("/usr/bin/su-real"));
        assertEquals(Condition.INSTALLED_HEALTHY, assess(layout, true));
        // A link that leaves through .. is still resolved inside the rootfs.
        Files.delete(new File(layout.rootfs, "usr/bin/su").toPath());
        Files.createSymbolicLink(new File(layout.rootfs, "usr/bin/su").toPath(),
                Paths.get("../../../../../../../usr/bin/su-real"));
        assertEquals(Condition.INSTALLED_HEALTHY, assess(layout, true));
    }

    @Test
    public void missingAppRuntimeIsRepairableWithoutTouchingTheGuest() throws Exception {
        Layout layout = installed("noruntime");
        File runtime = new File(layout.linuxDir, "runtime-lib");
        assertEquals(Condition.APP_RUNTIME_DAMAGED, RootfsLifecycle.assess(RootfsLifecycle.probe(
                ops, layout, true, ENTRY, Collections.singletonList(runtime))));
    }

    @Test
    public void lostStateRecordIsRepairNotReinstall() throws Exception {
        Layout layout = installed("nostate");
        assertEquals(Condition.REPAIR_REQUIRED, assess(layout, false));
    }

    @Test
    public void stateWithoutRootfsNeedsTheUser() throws Exception {
        Layout layout = new Layout(temporaryFolder.newFolder("staleState"));
        assertEquals(Condition.INSTALLED_DAMAGED, assess(layout, true));
    }

    @Test
    public void resetOnlyWhenRequestedOrInProgress() throws Exception {
        Layout layout = installed("reset");
        Files.write(layout.resetMarker.toPath(), new byte[0]);
        assertEquals(Condition.EXPLICIT_RESET_REQUESTED, assess(layout, true));
        Files.delete(layout.resetMarker.toPath());
        Files.createDirectory(layout.previous.toPath());
        assertEquals(Condition.EXPLICIT_RESET_REQUESTED, assess(layout, true));
    }

    // ---- first install -----------------------------------------------------

    @Test
    public void firstInstallNeverReplacesAnExistingRootfs() throws Exception {
        Layout layout = installed("noreplace");
        Map<String, String> home = snapshot(new File(layout.rootfs, "home"));
        stage(layout, "new");
        try {
            RootfsLifecycle.promoteFreshInstall(ops, layout);
            fail("a first install must not replace a rootfs");
        } catch (IOException expected) {
            // refused
        }
        assertEquals(home, snapshot(new File(layout.rootfs, "home")));
    }

    @Test
    public void firstInstallRequiresACompleteStaging() throws Exception {
        Layout layout = new Layout(temporaryFolder.newFolder("incomplete"));
        Files.createDirectories(new File(layout.staging, "usr/bin").toPath());
        try {
            RootfsLifecycle.promoteFreshInstall(ops, layout);
            fail("incomplete staging must not be promoted");
        } catch (IOException expected) {
            // refused
        }
        assertFalse(layout.rootfs.exists());
    }

    @Test
    public void firstInstallPromotesStaging() throws Exception {
        Layout layout = new Layout(temporaryFolder.newFolder("fresh"));
        stage(layout, "new");
        RootfsLifecycle.promoteFreshInstall(ops, layout);
        assertEquals(Condition.INSTALLED_HEALTHY, assess(layout, true));
        assertFalse(layout.staging.exists());
        assertFalse(new File(layout.rootfs, RootfsLifecycle.STAGING_COMPLETE).exists());
    }

    // ---- explicit reset ----------------------------------------------------

    @Test
    public void resetMovesHomeIntoTheNewSystem() throws Exception {
        Layout layout = installed("move");
        File home = new File(layout.rootfs, "home");
        Object inode = Files.getAttribute(home.toPath(), "unix:ino");
        Map<String, String> before = snapshot(home);
        stage(layout, "new");

        RootfsLifecycle.replaceSystemKeepingHome(ops, layout);

        assertEquals(before, snapshot(new File(layout.rootfs, "home")));
        assertEquals("the same directory, moved, not copied", inode,
                Files.getAttribute(new File(layout.rootfs, "home").toPath(), "unix:ino"));
        assertEquals("new", read(new File(layout.rootfs, "etc/system-version")));
        assertFalse(new File(layout.rootfs, "etc/old-only").exists());
        assertFalse(layout.previous.exists());
        assertFalse(layout.staging.exists());
        assertEquals(Condition.INSTALLED_HEALTHY, assess(layout, true));
    }

    /**
     * A crash after every single filesystem step of a reset: recovery must
     * leave exactly one complete system holding the user's /home, unchanged.
     */
    @Test
    public void crashAtAnyResetStepNeverLosesHome() throws Exception {
        boolean finished = false;
        for (int failAt = 0; failAt < 200 && !finished; failAt++) {
            Layout layout = installed("crash" + failAt);
            Map<String, String> before = snapshot(new File(layout.rootfs, "home"));
            stage(layout, "new");
            CrashingOps crashing = new CrashingOps(ops, failAt);
            try {
                RootfsLifecycle.replaceSystemKeepingHome(crashing, layout);
                finished = true;
            } catch (CrashingOps.Crash crash) {
                // The process died here. Next start: recovery.
                boolean completed = RootfsLifecycle.recover(ops, layout);
                String version = read(new File(layout.rootfs, "etc/system-version"));
                assertEquals("step " + failAt, completed ? "new" : "old", version);
            }
            assertEquals("home after a crash at step " + failAt,
                    before, snapshot(new File(layout.rootfs, "home")));
            assertFalse("previous resolved at step " + failAt, layout.previous.exists());
            // A staging tree may remain (the reset marker re-runs the reset);
            // it never holds the user's files.
            if (layout.staging.exists()) {
                assertFalse("user files left in staging at step " + failAt,
                        new File(layout.staging, "home/thoth/projects").exists());
            }
        }
        assertTrue("the reset eventually ran to completion", finished);
    }

    @Test
    public void recoverWithNothingPendingDoesNothing() throws Exception {
        Layout layout = installed("idle");
        Map<String, String> before = snapshot(layout.rootfs);
        assertFalse(RootfsLifecycle.recover(ops, layout));
        assertEquals(before, snapshot(layout.rootfs));
    }

    @Test
    public void recoverKeepsAnOldSystemThatStillHoldsHome() throws Exception {
        Layout layout = installed("ambiguous");
        // An impossible-by-construction state: both systems hold a /home.
        Files.createDirectories(new File(layout.previous, "home/thoth").toPath());
        Files.write(new File(layout.previous, "home/thoth/precious").toPath(),
                "keep".getBytes(StandardCharsets.UTF_8));
        try {
            RootfsLifecycle.recover(ops, layout);
            fail("must not silently pick one");
        } catch (IOException expected) {
            // kept aside
        }
        File[] kept = layout.linuxDir.listFiles((d, n) -> n.startsWith("rootfs.previous-"));
        assertEquals(1, kept.length);
        assertEquals("keep", read(new File(kept[0], "home/thoth/precious")));
    }

    // ---- fixtures ----------------------------------------------------------

    private Condition assess(Layout layout, boolean stateInstalled) throws IOException {
        return RootfsLifecycle.assess(RootfsLifecycle.probe(ops, layout, stateInstalled, ENTRY,
                Collections.<File>emptyList()));
    }

    /** An installed old system with a populated /home. */
    private Layout installed(String name) throws IOException {
        Layout layout = new Layout(temporaryFolder.newFolder(name));
        File root = layout.rootfs;
        system(root, "old");
        Files.write(new File(root, "etc/old-only").toPath(), new byte[]{1});
        File home = new File(root, "home/thoth");
        Files.createDirectories(new File(home, "projects/ثوث").toPath());
        Files.write(new File(home, ".bashrc").toPath(), "alias ll='ls -l'\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(home, "projects/ثوث/notes.txt").toPath(), "مرحبا".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(home, "link-to-notes").toPath(),
                Paths.get("projects/ثوث/notes.txt"));
        Files.createDirectories(new File(root, "home/other").toPath());
        return layout;
    }

    private void stage(Layout layout, String version) throws IOException {
        system(layout.staging, version);
        Files.createDirectories(new File(layout.staging, "home/thoth").toPath());
        Files.write(new File(layout.staging, "home/thoth/.bashrc").toPath(), "skeleton".getBytes(StandardCharsets.UTF_8));
        RootfsLifecycle.markStagingComplete(ops, layout);
    }

    private static void system(File root, String version) throws IOException {
        Files.createDirectories(new File(root, "usr/bin").toPath());
        Files.createDirectories(new File(root, "etc").toPath());
        Files.write(new File(root, "usr/bin/bash").toPath(), new byte[]{1});
        Files.write(new File(root, "usr/bin/su").toPath(), new byte[]{1});
        Files.createSymbolicLink(new File(root, "bin").toPath(), Paths.get("usr/bin"));
        Files.write(new File(root, "etc/system-version").toPath(),
                version.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Map<String, String> snapshot(File top) throws IOException {
        Map<String, String> out = new TreeMap<>();
        walk(top, "", out);
        return out;
    }

    private static void walk(File node, String rel, Map<String, String> out) throws IOException {
        if (Files.isSymbolicLink(node.toPath())) {
            out.put(rel, "link " + Files.readSymbolicLink(node.toPath()));
        } else if (Files.isDirectory(node.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            out.put(rel, "dir");
            String[] names = node.list();
            Arrays.sort(names);
            for (String n : names) walk(new File(node, n), rel + "/" + n, out);
        } else {
            out.put(rel, "file " + ExtractorSecurityCases.digest(Files.readAllBytes(node.toPath())));
        }
    }

    /** Dies (throws Crash) at the N-th mutating operation. */
    static final class CrashingOps extends TarballExtractorTest.DelegatingOps {
        static final class Crash extends IOException {
            Crash() {
                super("simulated crash");
            }
        }

        private int remaining;

        CrashingOps(FileOps delegate, int failAt) {
            super(delegate);
            this.remaining = failAt;
        }

        private void step() throws Crash {
            if (remaining-- == 0) throw new Crash();
        }

        @Override public void rename(File a, File b) throws IOException { step(); super.rename(a, b); }
        @Override public void unlink(File f) throws IOException { step(); super.unlink(f); }
        @Override public void rmdir(File f) throws IOException { step(); super.rmdir(f); }
        @Override public void mkdir(File f, int m) throws IOException { step(); super.mkdir(f, m); }
        @Override public OutputStream createNew(File f, int m) throws IOException {
            step();
            return super.createNew(f, m);
        }
    }
}
