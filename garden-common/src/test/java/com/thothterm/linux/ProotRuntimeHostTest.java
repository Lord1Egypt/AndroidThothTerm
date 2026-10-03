/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * The PRoot runtime's contract with the guest, checked on a native build:
 * /proc/self/exe names the hard link a program was started through
 * (docs/garden/HARDLINK_EXECUTABLES.md), and a window's session hangs up like
 * a terminal while tracees never outlive proot (docs/garden/SESSION_LIFECYCLE.md),
 * and a guest process's /proc/[pid]/cwd names its working directory, which
 * uploads rely on (docs/garden/UPLOADS.md).
 */
public class ProotRuntimeHostTest {
    private static final String PATCH =
            "garden-common/patches/0003-link2symlink-name-proc-self-exe-after-the-faked-hard-link.patch";
    private static final String HANGUP_PATCH =
            "garden-common/patches/0004-hang-up-the-session-on-command-exit-and-never-outlive-proot.patch";
    private static final String CWD_PATCH =
            "garden-common/patches/0005-keep-the-kernel-working-directory-in-step-with-the-guest.patch";
    private static final String OWNER_PATCH =
            "garden-common/patches/0008-keep-the-guest-owner-on-link2symlink-hard-links.patch";
    private static final String CHILD_PATCH =
            "garden-common/patches/0007-recover-a-fork-child-the-kernel-did-not-name.patch";

    private static File repoRoot() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "third_party").isDirectory()) return here;
        return here.getParentFile();
    }

    @Test
    public void thePatchKeepsItsHooks() throws Exception {
        String patch = new String(Files.readAllBytes(new File(repoRoot(), PATCH).toPath()),
                StandardCharsets.UTF_8);
        assertTrue("execve must rename /proc/self/exe after the faked hard link",
                patch.contains("+		case PR_execve:") && patch.contains("name_exe_after_faked_link(tracee);"));
        assertTrue("the walk must translate only the directory, or translated_path() hides the link",
                patch.contains("Translate only the directory"));
        String hangup = new String(Files.readAllBytes(new File(repoRoot(), HANGUP_PATCH).toPath()),
                StandardCharsets.UTF_8);
        assertTrue("--hangup-on-exit must exist", hangup.contains("\"--hangup-on-exit\""));
        assertTrue("tracees must die with proot", hangup.contains("PTRACE_O_EXITKILL);"));
        String cwd = new String(Files.readAllBytes(new File(repoRoot(), CWD_PATCH).toPath()),
                StandardCharsets.UTF_8);
        assertTrue("chdir must reach the kernel with the host path",
                cwd.contains("+			status = set_sysarg_path(tracee, host_path, SYSARG_1);"));
        assertTrue("chdir must no longer be voided",
                cwd.contains("-		set_sysnum(tracee, PR_void);"));
        assertTrue("the guest view changes only when the kernel's did",
                cwd.contains("+		if ((int) syscall_result == 0) {"));
        assertTrue("the first tracee must start behind --cwd",
                cwd.contains("+			    || chdir(host_cwd) < 0)"));
        String child = new String(Files.readAllBytes(new File(repoRoot(), CHILD_PATCH).toPath()),
                StandardCharsets.UTF_8);
        assertTrue("a child that cannot be identified stops the session, never resumes",
                child.contains("+			exit(EXIT_FAILURE);"));
        assertTrue("only an unknown or not yet initialized traced task is a candidate",
                child.contains("+		    || tracer != getpid() || state == 'Z' || state == 'X')"));
        String owner = new String(Files.readAllBytes(new File(repoRoot(), OWNER_PATCH).toPath()),
                StandardCharsets.UTF_8);
        assertTrue("the owner is taken from the buffer as it stands",
                owner.contains("finalStat.st_uid = current_uid;") && owner.contains("finalStat.st_gid = current_gid;"));
        for (String script : new String[]{"garden-common/tools/build-proot.sh",
                "term-ubuntu/tools/build-proot.sh"}) {
            String text = new String(Files.readAllBytes(new File(repoRoot(), script).toPath()),
                    StandardCharsets.UTF_8);
            assertFalse("the test-only pid override must never be built into the app: " + script,
                    text.contains("THOTHTERM_TEST_NO_EVENTMSG"));
        }
    }

    /**
     * Patch 0007, the F-Droid reviewer's hang: without it a child is left
     * stopped when PTRACE_GETEVENTMSG gives no pid; with it every child of
     * the sequential workloads is recovered and concurrent ones either run
     * or stop cleanly. tests/garden-common/proot/fork-recovery-check.sh.
     */
    @Test
    public void aChildTheKernelDidNotNameIsRecoveredOrTheSessionStops() throws Exception {
        Assume.assumeTrue("needs a Linux host", new File("/proc/self/exe").exists());
        File script = new File(repoRoot(), "tests/garden-common/proot/fork-recovery-check.sh");
        File work = new File(repoRoot(), "garden-common/build/proot-fork-recovery-check");
        deleteRecursively(work);
        ProcessBuilder builder = new ProcessBuilder("sh", script.getPath()).redirectErrorStream(true);
        builder.environment().put("WORK", work.getPath());
        Process process = builder.start();
        String output = readAll(process.getInputStream());
        assertTrue("fork recovery check timed out", process.waitFor(20, TimeUnit.MINUTES));
        int status = process.exitValue();
        Assume.assumeTrue("cannot run here: " + output.trim(), status != 2);
        assertEquals(output, 0, status);
        assertTrue(output, output.contains("PASS stuck: a child is left stopped under ptrace (t)"));
        assertTrue(output, output.contains("PASS forced vfork: every child recovered, no hang"));
        assertFalse(output, output.contains("FAIL"));
    }

    /**
     * Patch 0008: a link2symlink fake hard link, a symlink, an ordinary file
     * and the hidden backing files all show the guest's owner through every
     * stat flavour and find(1), as root and as a fake non-root id; without
     * it 45 of 106 observations showed the app's uid (pacman -Qkk "UID
     * mismatch"). tests/garden-common/proot/owner-check.sh.
     */
    @Test
    public void fakeHardLinksShowTheGuestOwner() throws Exception {
        Assume.assumeTrue("needs a Linux host", new File("/proc/self/exe").exists());
        Assume.assumeFalse("needs an ordinary user", "root".equals(System.getProperty("user.name")));
        File script = new File(repoRoot(), "tests/garden-common/proot/owner-check.sh");
        File work = new File(repoRoot(), "garden-common/build/proot-owner-check");
        deleteRecursively(work);
        ProcessBuilder builder = new ProcessBuilder("sh", script.getPath()).redirectErrorStream(true);
        builder.environment().put("WORK", work.getPath());
        Process process = builder.start();
        String output = readAll(process.getInputStream());
        assertTrue("owner check timed out", process.waitFor(20, TimeUnit.MINUTES));
        int status = process.exitValue();
        Assume.assumeTrue("cannot run here: " + output.trim(), status != 2);
        assertEquals(output, 0, status);
        assertTrue(output, output.contains("PASS with 0008 every flavour sees 0 0 on every object (-0)"));
        assertTrue(output, output.contains("PASS with 0008 every flavour sees 1234 1234"));
        assertFalse(output, output.contains("FAIL"));
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!java.nio.file.Files.isSymbolicLink(child.toPath())) deleteRecursively(child);
                else child.delete();
            }
        }
        file.delete();
    }

    /**
     * Builds PRoot natively from the pinned sources plus our patches and runs
     * tests/proot-runtime/host-test.sh. Runs on Linux build hosts with a C
     * toolchain; skipped elsewhere.
     */
    @Test
    public void nativeRuntimeKeepsKernelSemantics() throws Exception {
        Assume.assumeTrue("needs a Linux host", new File("/proc/self/exe").exists());
        File script = new File(repoRoot(), "tests/proot-runtime/host-test.sh");
        File work = new File(repoRoot(), "garden-common/build/proot-runtime-host-test");

        ProcessBuilder builder = new ProcessBuilder("sh", script.getPath(), work.getPath())
                .redirectErrorStream(true);
        builder.environment().put("THOTHTERM_PATCH_MODULE", "garden-common");
        Process process = builder.start();
        String output = readAll(process.getInputStream());
        assertTrue("host test timed out", process.waitFor(10, TimeUnit.MINUTES));
        int status = process.exitValue();

        Assume.assumeTrue("cannot run here: " + output.trim(), status != 2);
        assertEquals(output, 0, status);
        assertTrue(output, output.contains("PASS relative symlink to a hard link"));
        assertTrue(output, output.contains("PASS hangup-on-exit: a nohup'd job keeps running"));
        assertTrue(output, output.contains("PASS cwd: cd through a symlink lands on its target"));
        assertTrue(output, output.contains("PASS cwd: a failed cd changes neither view"));
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
