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
 * /proc/self/exe must name the hard link a program was started through, not
 * link2symlink's hidden ".l2s.*" backing file: Ubuntu's rust-coreutils refuses
 * to run otherwise, which broke every coreutils command after
 * `apt full-upgrade` (docs/garden/HARDLINK_EXECUTABLES.md).
 */
public class ProotHardlinkIdentityTest {
    private static final String PATCH =
            "term-ubuntu/patches/0003-link2symlink-name-proc-self-exe-after-the-faked-hard-link.patch";

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
    }

    /**
     * Builds PRoot natively from the pinned sources plus our patches and checks
     * the kernel's hard-link semantics under --link2symlink. Runs on Linux build
     * hosts with a C toolchain; skipped elsewhere.
     */
    @Test
    public void procSelfExeNamesTheHardLinkUnderLink2symlink() throws Exception {
        Assume.assumeTrue("needs a Linux host", new File("/proc/self/exe").exists());
        File script = new File(repoRoot(), "tests/proot-hardlink-identity/host-test.sh");
        File work = new File(repoRoot(), "term-ubuntu/build/proot-hardlink-identity");

        Process process = new ProcessBuilder("sh", script.getPath(), work.getPath())
                .redirectErrorStream(true)
                .start();
        String output = readAll(process.getInputStream());
        assertTrue("host test timed out", process.waitFor(10, TimeUnit.MINUTES));
        int status = process.exitValue();

        Assume.assumeTrue("cannot run here: " + output.trim(), status != 2);
        assertEquals(output, 0, status);
        assertTrue(output, output.contains("PASS relative symlink to a hard link"));
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
