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

package com.thothterm.arch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.thothterm.audio.AudioBridge;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * The edition's guest-side half of "Linux audio": the helper garden-common
 * installs as {@code /usr/local/bin/thothterm-audio-setup} writes the
 * PulseAudio configuration the app's bridge expects -- a pipe sink into the
 * app's FIFO that suspends when idle -- installs pulseaudio with this
 * edition's package manager when it is missing, and starts (or restarts) the
 * daemon. Run here with stand-ins for pulseaudio and pacman on PATH.
 */
public class ArchAudioSetupTest {
    /** The module line the app's bridge relies on, with the shell variables the script uses. */
    private static final String PIPE_SINK = "load-module module-pipe-sink file=$fifo sink_name=$sink "
            + "format=s16le rate=48000 channels=2 use_system_clock_for_timing=yes "
            + "sink_properties=device.description=ThothTerm";

    private static File script() {
        return new File(ArchEditionTest.moduleDir(), "src/main/assets/" + AudioBridge.GUEST_SETUP_ASSET);
    }

    /** A bash on PATH: /bin/bash on most hosts, elsewhere on NixOS. */
    private static boolean haveBash() {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && new File(dir, "bash").canExecute()) return true;
        }
        return false;
    }

    @Test
    public void theHelperShipsUnderTheNameGardenCommonLooksFor() throws Exception {
        File script = script();
        assertTrue(script + " missing", script.isFile());
        assertEquals(AudioBridge.GUEST_SETUP_NAME, script.getName());
        String text = ArchEditionTest.read(script);
        assertTrue(text.startsWith("#!/bin/bash\n"));
        // The pipe the app reads: the exact module line, s16le 48 kHz stereo into the app's FIFO.
        assertTrue(text.contains(PIPE_SINK));
        assertTrue(text.contains("\nfifo=" + AudioBridge.GUEST_FIFO + "\n"));
        assertTrue(text.contains("set-default-sink $sink"));
        // Idle costs nothing: the sink suspends 5 s after the last stream.
        assertTrue(text.contains("load-module module-suspend-on-idle timeout=5"));
        // The old design is gone: no null sink, no TCP stream, no port.
        assertFalse(text.contains("module-null-sink"));
        assertFalse(text.contains("module-simple-protocol-tcp"));
        assertFalse(text.contains("4713"));
        assertTrue(text.contains("autospawn = yes"));
        assertTrue(text.contains("daemon-binary = /usr/bin/pulseaudio"));
        assertTrue(text.contains("pulseaudio --start --exit-idle-time=-1"));
        // The app owns the pipe; the script only checks and explains.
        assertFalse("the script must not create the pipe", text.contains("mkfifo"));
        assertTrue(text.contains("[ ! -p \"$fifo\" ]"));
        // This edition's package manager, the app's own flags, never -Sy.
        assertTrue(text.contains("pacman -S --noconfirm --needed pulseaudio"));
        assertTrue(text.contains("sudo -n pacman"));
        assertFalse(text.contains("pacman -Sy "));
        assertTrue(text.contains("paplay"));
        assertTrue(text.contains("pactl info"));
        assertTrue(text.contains("mpv"));
    }

    @Test
    public void theHelperParses() throws Exception {
        assumeTrue("bash is required", haveBash());
        Result r = run(new String[]{"bash", "-n", script().getAbsolutePath()}, null, null);
        assertEquals(r.output, 0, r.exit);
    }

    @Test
    public void withPulseaudioInstalledItWritesTheConfigurationAndStartsTheDaemon() throws Exception {
        assumeTrue("bash is required", haveBash());
        File home = Files.createTempDirectory("thothterm-audio-home").toFile();
        File bin = stubs(home, true, false);
        Result r = run(new String[]{"bash", script().getAbsolutePath()}, home, bin);
        assertEquals(r.output, 0, r.exit);

        File pulse = new File(home, ".config/pulse");
        String defaultPa = ArchEditionTest.read(new File(pulse, "default.pa"));
        assertTrue(defaultPa.contains("\nload-module module-pipe-sink file=" + AudioBridge.GUEST_FIFO
                + " sink_name=thothterm format=s16le rate=48000 channels=2 use_system_clock_for_timing=yes "
                + "sink_properties=device.description=ThothTerm\n"));
        assertTrue(defaultPa.contains("\nset-default-sink thothterm\n"));
        assertTrue(defaultPa.contains("\nload-module module-suspend-on-idle timeout=5\n"));
        assertTrue(defaultPa.contains("load-module module-native-protocol-unix"));
        assertFalse(defaultPa.contains("module-null-sink"));
        assertFalse(defaultPa.contains("module-simple-protocol-tcp"));
        // Self-contained: the distribution's default.pa probes hardware PRoot does not have.
        assertFalse(defaultPa.contains(".include"));
        assertFalse(defaultPa.contains("module-udev-detect"));
        String client = ArchEditionTest.read(new File(pulse, "client.conf"));
        assertTrue(client.contains("autospawn = yes\n"));
        assertTrue(client.contains("daemon-binary = /usr/bin/pulseaudio\n"));
        String daemon = ArchEditionTest.read(new File(pulse, "daemon.conf"));
        assertTrue(daemon.contains("exit-idle-time = -1\n"));

        String calls = ArchEditionTest.read(new File(home, "calls"));
        assertTrue(calls, calls.contains("pulseaudio --check\n"));
        assertTrue(calls, calls.contains("pulseaudio --start --exit-idle-time=-1 --disable-shm=true\n"));
        assertFalse("no daemon was running: nothing to kill", calls.contains("pulseaudio --kill"));
        assertFalse("no daemon was running: nothing to kill by name", calls.contains("pkill"));
        assertFalse("pacman must not run when pulseaudio is installed", calls.contains("pacman"));
        assertTrue(r.output, r.output.contains(AudioBridge.GUEST_FIFO));
        // No pipe on this host: the script says so and still succeeds.
        assertTrue(r.output, r.output.contains("warning: " + AudioBridge.GUEST_FIFO + " is not a named pipe"));
        assertTrue(r.output, r.output.contains("pactl info") || r.output.contains("paplay "));
        assertTrue(r.output, r.output.contains("Linux audio"));

        // Running again rewrites the same files and succeeds.
        Result again = run(new String[]{"bash", script().getAbsolutePath()}, home, bin);
        assertEquals(again.output, 0, again.exit);
        assertEquals(defaultPa, ArchEditionTest.read(new File(pulse, "default.pa")));
    }

    @Test
    public void aRunningDaemonIsRestartedSoTheNewConfigurationApplies() throws Exception {
        assumeTrue("bash is required", haveBash());
        File home = Files.createTempDirectory("thothterm-audio-home").toFile();
        File bin = stubs(home, true, true);
        Result r = run(new String[]{"bash", script().getAbsolutePath()}, home, bin);
        assertEquals(r.output, 0, r.exit);
        String calls = ArchEditionTest.read(new File(home, "calls"));
        int check = calls.indexOf("pulseaudio --check\n");
        int kill = calls.indexOf("pulseaudio --kill\n");
        int start = calls.indexOf("pulseaudio --start --exit-idle-time=-1 --disable-shm=true\n");
        assertTrue(calls, check >= 0 && kill > check && start > kill);
        assertTrue(r.output, r.output.contains("Restarting pulseaudio"));
    }

    /**
     * {@code pulseaudio --check}/{@code --kill} only reach the daemon behind
     * the current runtime directory, which PulseAudio names after the host
     * name when the guest has no machine id. A daemon started under another
     * host name is still this user's process: it must go before a new one
     * starts, or both would write the pipe.
     */
    @Test
    public void aDaemonTheSocketCannotReachIsStoppedByName() throws Exception {
        assumeTrue("bash is required", haveBash());
        File home = Files.createTempDirectory("thothterm-audio-home").toFile();
        File bin = stubs(home, true, true, false);
        Result r = run(new String[]{"bash", script().getAbsolutePath()}, home, bin);
        assertEquals(r.output, 0, r.exit);
        String calls = ArchEditionTest.read(new File(home, "calls"));
        int pkill = calls.indexOf("pkill -x -u ");
        int start = calls.indexOf("pulseaudio --start --exit-idle-time=-1 --disable-shm=true\n");
        assertTrue(calls, calls.contains("pgrep -x -u "));
        assertTrue(calls, pkill >= 0 && start > pkill);
        assertTrue(r.output, r.output.contains("Restarting pulseaudio"));
        assertFalse("the stand-in daemon must be gone", new File(home, "running").exists());
    }

    @Test
    public void withoutPulseaudioItInstallsItThroughSudoAndPacman() throws Exception {
        assumeTrue("bash is required", haveBash());
        File home = Files.createTempDirectory("thothterm-audio-home").toFile();
        File bin = stubs(home, false, false);
        Result r = run(new String[]{"bash", script().getAbsolutePath()}, home, bin);
        assertEquals(r.output, 0, r.exit);
        String calls = ArchEditionTest.read(new File(home, "calls"));
        assertTrue(calls, calls.contains("sudo -n pacman -S --noconfirm --needed pulseaudio\n"));
        assertTrue(calls, calls.contains("pulseaudio --start --exit-idle-time=-1 --disable-shm=true\n"));
        // Installed before started.
        assertTrue(calls.indexOf("pacman") < calls.indexOf("pulseaudio --start"));
    }

    /**
     * A PATH with stand-ins that log their arguments to {@code $HOME/calls}:
     * pactl, sudo (which runs its argument), pacman (which "installs" by
     * making the pulseaudio stub executable) and, when {@code installed},
     * pulseaudio itself, whose {@code --check} reports a running daemon once
     * when {@code running} and never after a {@code --kill}.
     */
    private static File stubs(File home, boolean installed, boolean running) throws Exception {
        return stubs(home, installed, running, true);
    }

    /**
     * As above; a running daemon that is not {@code reachable} is one behind
     * another runtime directory: {@code --check} denies it and {@code --kill}
     * misses it, while pgrep/pkill (stand-ins too, never the host's) see it.
     */
    private static File stubs(File home, boolean installed, boolean running, boolean reachable)
            throws Exception {
        File bin = new File(home, "bin");
        assertTrue(bin.mkdirs());
        String log = "printf '%s\\n' \"$(basename \"$0\") $*\" >> \"" + new File(home, "calls").getAbsolutePath() + "\"\n";
        File pid = new File(home, "running");
        if (running) Files.write(pid.toPath(), new byte[0]);
        String socket = reachable ? "" : "false && ";
        stub(new File(bin, "pulseaudio"), "#!/usr/bin/env bash\n" + log
                + "case \"$1\" in\n"
                + "  --check) " + socket + "[ -e \"" + pid.getAbsolutePath() + "\" ]; exit $? ;;\n"
                + "  --kill) " + socket + "rm -f \"" + pid.getAbsolutePath() + "\" ;;\n"
                + "esac\nexit 0\n", installed);
        stub(new File(bin, "pgrep"), "#!/usr/bin/env bash\n" + log
                + "[ -e \"" + pid.getAbsolutePath() + "\" ]\n", true);
        stub(new File(bin, "pkill"), "#!/usr/bin/env bash\n" + log
                + "rm -f \"" + pid.getAbsolutePath() + "\"\n", true);
        stub(new File(bin, "pactl"), "#!/usr/bin/env bash\n" + log + "exit 0\n", true);
        stub(new File(bin, "sudo"), "#!/usr/bin/env bash\n" + log + "shift\nexec \"$@\"\n", true);
        stub(new File(bin, "pacman"), "#!/usr/bin/env bash\n" + log + "chmod 755 \"" + new File(bin, "pulseaudio").getAbsolutePath()
                + "\"\nexit 0\n", true);
        // id, mkdir, cat, printf, basename, sleep: the real ones, found after the stubs.
        return bin;
    }

    private static void stub(File file, String text, boolean executable) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        EnumSet<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        if (executable) perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(file.toPath(), perms);
    }

    private static class Result {
        int exit;
        String output;
    }

    private static Result run(String[] command, File home, File bin) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        if (home != null) {
            pb.environment().put("HOME", home.getAbsolutePath());
            pb.environment().remove("XDG_CONFIG_HOME");
            pb.environment().put("PATH", bin.getAbsolutePath() + ":" + pb.environment().get("PATH"));
        }
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        Result r = new Result();
        r.exit = p.waitFor();
        r.output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        return r;
    }
}
