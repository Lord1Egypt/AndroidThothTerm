/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
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
            "term-ubuntu/patches/0003-link2symlink-name-proc-self-exe-after-the-faked-hard-link.patch";
    private static final String HANGUP_PATCH =
            "term-ubuntu/patches/0004-hang-up-the-session-on-command-exit-and-never-outlive-proot.patch";
    private static final String CWD_PATCH =
            "term-ubuntu/patches/0005-keep-the-kernel-working-directory-in-step-with-the-guest.patch";

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
        File work = new File(repoRoot(), "term-ubuntu/build/proot-runtime-host-test");

        Process process = new ProcessBuilder("sh", script.getPath(), work.getPath())
                .redirectErrorStream(true)
                .start();
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
