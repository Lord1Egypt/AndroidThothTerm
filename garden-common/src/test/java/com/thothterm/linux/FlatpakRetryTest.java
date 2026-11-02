package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * The flatpak wrapper runs an install again when it ends on a network error:
 * a 2.6 GB LibreOffice install ended on "The connection is closed" and OSTree
 * would have gone on where it stopped. The retry function is run here against
 * a fake flatpak.
 */
public class FlatpakRetryTest {
    private static String retryFunction() throws Exception {
        File here = new File("").getAbsoluteFile();
        File repo = new File(here, "third_party").isDirectory() ? here : here.getParentFile();
        String script = new String(Files.readAllBytes(
                new File(repo, "garden-common/src/main/assets/linux/flatpak").toPath()), StandardCharsets.UTF_8);
        int begin = script.indexOf("# retry-begin");
        int end = script.indexOf("# retry-end");
        assertTrue("markers missing", begin >= 0 && end > begin);
        return script.substring(begin, end);
    }

    /** Runs flatpak_retry against a fake that fails with {@code message} the first {@code failures} times. */
    private static String[] run(int failures, String message) throws Exception {
        File dir = Files.createTempDirectory("flatpak-retry").toFile();
        File fake = new File(dir, "fake-flatpak");
        File count = new File(dir, "count");
        Files.write(fake.toPath(), ("#!/bin/sh\n"
                + "n=$(cat '" + count + "' 2>/dev/null || echo 0); n=$((n + 1)); echo $n > '" + count + "'\n"
                + "echo \"progress $*\"\n"
                + "if [ $n -le " + failures + " ]; then echo '" + message + "' >&2; exit 1; fi\n"
                + "exit 0\n").getBytes(StandardCharsets.UTF_8));
        fake.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", retryFunction()
                + "\nflatpak_retry '" + fake + "' install --user flathub org.example.App\necho \"rc=$?\"");
        pb.environment().put("FLATPAK_RETRY_DELAY", "0");
        pb.environment().put("TMPDIR", dir.getPath());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        byte[] err = p.getErrorStream().readAllBytes();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        String runs = new String(Files.readAllBytes(count.toPath()), StandardCharsets.UTF_8).trim();
        return new String[]{new String(out, StandardCharsets.UTF_8), new String(err, StandardCharsets.UTF_8), runs};
    }

    @Test
    public void aDroppedConnectionIsRunAgainUntilItWorks() throws Exception {
        String[] r = run(2, "Error: Failed to install org.example.App: The connection is closed");
        assertEquals("3", r[2]);
        assertTrue(r[0], r[0].endsWith("rc=0\n"));
        assertTrue(r[1], r[1].contains("run 3 of 5"));
        assertTrue("stdout is passed through each time", r[0].split("progress install --user flathub org.example.App").length == 4);
    }

    @Test
    public void anErrorThatIsNotTheNetworkIsNotRetried() throws Exception {
        String[] r = run(9, "error: Nothing matches org.example.App in remote flathub");
        assertEquals("1", r[2]);
        assertTrue(r[0], r[0].endsWith("rc=1\n"));
        assertTrue(r[1], r[1].contains("Nothing matches"));
    }

    @Test
    public void aNetworkThatStaysDownStopsAfterFiveRuns() throws Exception {
        String[] r = run(99, "Error: Failed to install org.example.App: Timeout was reached");
        assertEquals("5", r[2]);
        assertTrue(r[0], r[0].endsWith("rc=1\n"));
    }

    @Test
    public void aFailedNameLookupIsRunAgain() throws Exception {
        String[] r = run(1, "error: Unable to load summary from remote flathub: Could not resolve hostname");
        assertEquals("2", r[2]);
        assertTrue(r[0], r[0].endsWith("rc=0\n"));
    }
}
