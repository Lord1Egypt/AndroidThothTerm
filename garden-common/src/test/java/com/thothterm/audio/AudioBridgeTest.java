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

package com.thothterm.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Linux audio" -- the bridge from the guest's sound server to the device --
 * on the JVM: the sizes, the backoff and the idle rule are what the format
 * and the design say; the loop opens the pipe, opens a track on the first
 * bytes, releases it when the sound stops, plays again when it returns,
 * reopens a pipe that ended, keeps trying without one, and stops cleanly out
 * of a waiting read -- against a pipe stand-in fed by the test and a counting
 * sink in place of the {@code AudioTrack}; and the service owns the bridge
 * the way it owns the CPU wake lock. The FIFO's {@code poll}, the track and
 * the guest's PulseAudio are the device's half ({@code android.system.Os}
 * is not on the JVM); the pipe-sink mechanics were checked on a host with
 * PulseAudio 17 and PRoot.
 */
public class AudioBridgeTest {
    private static final File MAIN = new File("src/main");
    private static final long WAIT_MS = 5000;
    /** The bridge's idle timeout in these tests, so they take milliseconds. */
    private static final long IDLE_MS = 100;

    /** Counts bytes and closes, so a leaked or doubly closed track shows up. */
    private static class FakeSink implements AudioBridge.Sink {
        final AtomicInteger bytes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public void write(byte[] data, int offset, int length) {
            bytes.addAndGet(length);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private static class FakeSinks implements AudioBridge.SinkFactory {
        final FakeSink sink = new FakeSink();
        final AtomicInteger opens = new AtomicInteger();
        /** How many opens fail first, as a track that cannot be created would. */
        int failFirst;

        @Override
        public AudioBridge.Sink open() throws IOException {
            opens.incrementAndGet();
            if (failFirst > 0) {
                failFirst--;
                throw new IOException("no track");
            }
            return sink;
        }
    }

    /**
     * The pipe as the bridge sees it: a read waits for what the test feeds,
     * returns 0 when the time is up or after {@link #wake()}, -1 once the
     * test ends it. Chunks larger than the buffer are read in parts, like a
     * real pipe.
     */
    private static class FakePipe implements AudioBridge.Pipe {
        private static final byte[] WAKE = new byte[0];
        private static final byte[] END = new byte[0];
        final LinkedBlockingDeque<byte[]> chunks = new LinkedBlockingDeque<>();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger wakes = new AtomicInteger();
        final List<Long> timeouts = Collections.synchronizedList(new ArrayList<Long>());

        void feed(int bytes) {
            chunks.add(new byte[bytes]);
        }

        void end() {
            chunks.add(END);
        }

        @Override
        public int read(byte[] buffer, long timeoutMs) {
            timeouts.add(timeoutMs);
            byte[] chunk;
            try {
                chunk = timeoutMs < 0 ? chunks.take() : chunks.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
            if (chunk == null || chunk == WAKE) return 0;
            if (chunk == END) {
                chunks.addFirst(END); // Stays ended.
                return -1;
            }
            int n = Math.min(chunk.length, buffer.length);
            if (n < chunk.length) chunks.addFirst(new byte[chunk.length - n]);
            return n;
        }

        @Override
        public void wake() {
            wakes.incrementAndGet();
            chunks.add(WAKE);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    /** Opens a fresh pipe per call and keeps them all; fails while {@code missing}, as without a FIFO. */
    private static class FakePipes implements AudioBridge.PipeFactory {
        final List<FakePipe> opened = Collections.synchronizedList(new ArrayList<FakePipe>());
        final AtomicInteger opens = new AtomicInteger();
        volatile boolean missing;

        @Override
        public AudioBridge.Pipe open() throws IOException {
            opens.incrementAndGet();
            if (missing) throw new IOException("no pipe");
            FakePipe pipe = new FakePipe();
            opened.add(pipe);
            return pipe;
        }

        FakePipe pipe(int index) {
            return opened.get(index);
        }
    }

    /** Every change, in order. */
    private static class Changes implements AudioBridge.Listener {
        final List<Boolean> seen = Collections.synchronizedList(new ArrayList<Boolean>());

        @Override
        public void onAudioActiveChanged(boolean active) {
            seen.add(active);
        }
    }

    private static AudioBridge bridge(FakePipes pipes, FakeSinks sinks) {
        // A fast schedule, so the tests take milliseconds; the real one is below.
        return new AudioBridge(pipes, sinks, IDLE_MS, 20, 100);
    }

    private interface Condition {
        boolean holds();
    }

    private static void waitFor(String what, Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.holds()) {
            assertTrue("timed out waiting for " + what, System.currentTimeMillis() < deadline);
            Thread.sleep(5);
        }
    }

    @Test
    public void offByDefaultAndTheFormatAndThePipeAreWhatTheGuestWrites() {
        assertFalse(AudioBridge.PREF_DEFAULT);
        assertEquals("audio_bridge", AudioBridge.PREF_KEY);
        // The one pipe, at a fixed guest path the edition's helper points module-pipe-sink at.
        assertEquals("/run/thothterm/audio", AudioBridge.GUEST_DIR);
        assertEquals("pulse.fifo", AudioBridge.FIFO_NAME);
        assertEquals("/run/thothterm/audio/pulse.fifo", AudioBridge.GUEST_FIFO);
        // In the app's private storage, under the same tree as the rootfs.
        assertEquals("linux/audio", AudioBridge.HOST_DIR);
        // s16le, 48 kHz, stereo: module-pipe-sink's format=s16le rate=48000 channels=2.
        assertEquals(48000, AudioBridge.SAMPLE_RATE);
        assertEquals(2, AudioBridge.CHANNELS);
        assertEquals(2, AudioBridge.BYTES_PER_SAMPLE);
        assertEquals(4, AudioBridge.FRAME_BYTES);
        // About a second without data and the track goes.
        assertEquals(1000, AudioBridge.IDLE_TIMEOUT_MS);
        assertEquals("thothterm-audio-setup", AudioBridge.GUEST_SETUP_NAME);
        assertTrue(AudioBridge.GUEST_SETUP_ASSET.endsWith("/" + AudioBridge.GUEST_SETUP_NAME));
    }

    @Test
    public void bufferSizesFollowTheFormat() {
        assertEquals(192000, AudioBridge.bytesForMillis(1000));
        assertEquals(3840, AudioBridge.bytesForMillis(20));
        assertEquals(48000, AudioBridge.bytesForMillis(AudioBridge.TRACK_BUFFER_MS));
        // Never less than a frame, never a negative or fractional frame.
        assertEquals(AudioBridge.FRAME_BYTES, AudioBridge.bytesForMillis(0));
        assertEquals(AudioBridge.FRAME_BYTES, AudioBridge.bytesForMillis(-5));
        assertEquals(0, AudioBridge.bytesForMillis(7) % AudioBridge.FRAME_BYTES);
        // One read is about 20 ms and a whole number of frames.
        assertEquals(0, AudioBridge.READ_CHUNK_BYTES % AudioBridge.FRAME_BYTES);
        assertEquals(AudioBridge.bytesForMillis(20), AudioBridge.READ_CHUNK_BYTES);
    }

    @Test
    public void theTrackBufferIsTheLargerOfTheDevicesMinimumAndTheDesignedLatency() {
        int designed = AudioBridge.bytesForMillis(AudioBridge.TRACK_BUFFER_MS);
        assertEquals(designed, AudioBridge.trackBufferBytes(1024));
        assertEquals(designed, AudioBridge.trackBufferBytes(designed));
        assertEquals(designed + 4, AudioBridge.trackBufferBytes(designed + 1));
        assertEquals(200000, AudioBridge.trackBufferBytes(200000));
        assertEquals(200004, AudioBridge.trackBufferBytes(200001));
        // getMinBufferSize's error values (ERROR, ERROR_BAD_VALUE) fall back to the design.
        assertEquals(designed, AudioBridge.trackBufferBytes(0));
        assertEquals(designed, AudioBridge.trackBufferBytes(-1));
        assertEquals(designed, AudioBridge.trackBufferBytes(-2));
    }

    @Test
    public void theBackoffDoublesFromOneSecondAndStopsAtTen() {
        assertEquals(1000, AudioBridge.backoffMillis(0));
        assertEquals(2000, AudioBridge.backoffMillis(1));
        assertEquals(4000, AudioBridge.backoffMillis(2));
        assertEquals(8000, AudioBridge.backoffMillis(3));
        assertEquals(10000, AudioBridge.backoffMillis(4));
        assertEquals(10000, AudioBridge.backoffMillis(5));
        assertEquals(10000, AudioBridge.backoffMillis(1000));
        assertEquals(10000, AudioBridge.backoffMillis(Integer.MAX_VALUE));
        assertEquals(1000, AudioBridge.backoffMillis(-1));
        // The parameters are honoured and never exceed the cap.
        assertEquals(20, AudioBridge.backoffMillis(0, 20, 100));
        assertEquals(80, AudioBridge.backoffMillis(2, 20, 100));
        assertEquals(100, AudioBridge.backoffMillis(3, 20, 100));
        assertEquals(50, AudioBridge.backoffMillis(0, 200, 50));
        assertEquals(0, AudioBridge.backoffMillis(3, 0, 100));
    }

    @Test
    public void aReadWaitsForeverWhileIdleAndForTheRestOfTheTimeoutWhileATrackIsOpen() {
        // No track: nothing to time out; the wait costs nothing.
        assertEquals(-1, AudioBridge.readTimeoutMillis(false, 0, 1000000, 1000));
        assertEquals(-1, AudioBridge.readTimeoutMillis(false, 500, 500, 1000));
        // A track: what remains of the timeout since the last data, never negative.
        assertEquals(1000, AudioBridge.readTimeoutMillis(true, 500, 500, 1000));
        assertEquals(700, AudioBridge.readTimeoutMillis(true, 500, 800, 1000));
        assertEquals(0, AudioBridge.readTimeoutMillis(true, 500, 1500, 1000));
        assertEquals(0, AudioBridge.readTimeoutMillis(true, 500, 9000, 1000));
        // Idle exactly at the timeout, not before.
        assertFalse(AudioBridge.idle(500, 1499, 1000));
        assertTrue(AudioBridge.idle(500, 1500, 1000));
        assertTrue(AudioBridge.idle(500, 99999, 1000));
    }

    @Test
    public void theBridgePlaysWhatArrivesReleasesTheTrackWhenItStopsAndPlaysAgain() throws Exception {
        FakePipes pipes = new FakePipes();
        FakeSinks sinks = new FakeSinks();
        Changes changes = new Changes();
        AudioBridge bridge = bridge(pipes, sinks);
        bridge.setListener(changes);
        assertFalse(bridge.isRunning());
        assertFalse(bridge.isActive());

        bridge.start();
        assertTrue(bridge.isRunning());
        waitFor("pipe opened", () -> pipes.opened.size() == 1);
        FakePipe pipe = pipes.pipe(0);
        // Nothing plays: no track, and the read waits without a timeout.
        waitFor("first read", () -> !pipe.timeouts.isEmpty());
        Thread.sleep(3 * IDLE_MS);
        assertEquals(0, sinks.opens.get());
        assertFalse(bridge.isActive());
        assertEquals(-1L, (long) pipe.timeouts.get(0));

        // Sound: the track opens on the first bytes and gets them all, in reads of a chunk at most.
        pipe.feed(8000);
        waitFor("8000 bytes", () -> sinks.sink.bytes.get() == 8000);
        assertTrue(bridge.isActive());
        assertEquals(1, sinks.opens.get());
        assertEquals(Collections.singletonList(true), new ArrayList<>(changes.seen));
        // While the track is open the reads are bounded by the idle timeout.
        waitFor("a bounded read", () -> {
            synchronized (pipe.timeouts) {
                for (long t : pipe.timeouts) if (t >= 0 && t <= IDLE_MS) return true;
            }
            return false;
        });

        // The sound stops: after the idle timeout the track is released and the bridge is idle.
        waitFor("track released", () -> sinks.sink.closes.get() == 1);
        waitFor("idle", () -> !bridge.isActive());
        assertEquals(2, changes.seen.size());
        assertFalse(changes.seen.get(1));
        assertTrue(bridge.isRunning());
        assertEquals(1, pipes.opens.get());

        // Sound again: a new track, the old pipe.
        pipe.feed(400);
        waitFor("8400 bytes", () -> sinks.sink.bytes.get() == 8400);
        assertTrue(bridge.isActive());
        assertEquals(2, sinks.opens.get());
        assertEquals(1, pipes.opens.get());

        // Stop while playing: the read is woken, the track and the pipe are closed, once each.
        bridge.stop();
        assertFalse(bridge.isRunning());
        waitFor("track released", () -> sinks.sink.closes.get() == 2);
        waitFor("pipe closed", () -> pipe.closes.get() == 1);
        waitFor("idle", () -> !bridge.isActive());
        assertEquals(1, pipe.wakes.get());
        assertFalse(changes.seen.get(changes.seen.size() - 1));
        // Stop again: nothing to do, nothing thrown.
        bridge.stop();
        assertEquals(2, sinks.sink.closes.get());
        assertEquals(1, pipe.closes.get());
    }

    @Test
    public void stopOutOfAnIdleWaitClosesThePipeAtOnce() throws Exception {
        FakePipes pipes = new FakePipes();
        FakeSinks sinks = new FakeSinks();
        AudioBridge bridge = bridge(pipes, sinks);
        bridge.start();
        waitFor("pipe opened", () -> pipes.opened.size() == 1);
        FakePipe pipe = pipes.pipe(0);
        waitFor("waiting", () -> !pipe.timeouts.isEmpty());
        bridge.stop();
        waitFor("pipe closed", () -> pipe.closes.get() == 1);
        waitFor("stopped", () -> !bridge.isRunning());
        assertEquals(0, sinks.opens.get());
        assertEquals(0, sinks.sink.closes.get());
    }

    @Test
    public void aPipeThatEndsIsReopenedAndNoTrackIsOpenedMeanwhile() throws Exception {
        FakePipes pipes = new FakePipes();
        FakeSinks sinks = new FakeSinks();
        AudioBridge bridge = bridge(pipes, sinks);
        bridge.start();
        waitFor("pipe opened", () -> pipes.opened.size() == 1);
        pipes.pipe(0).feed(1000);
        waitFor("1000 bytes", () -> sinks.sink.bytes.get() == 1000);
        // The guest's side went away for good (the pipe was removed): EOF.
        pipes.pipe(0).end();
        waitFor("pipe closed", () -> pipes.pipe(0).closes.get() == 1);
        waitFor("track released", () -> sinks.sink.closes.get() == 1);
        waitFor("idle", () -> !bridge.isActive());
        waitFor("pipe reopened", () -> pipes.opened.size() == 2);
        assertTrue(bridge.isRunning());
        pipes.pipe(1).feed(400);
        waitFor("1400 bytes", () -> sinks.sink.bytes.get() == 1400);
        assertEquals(2, sinks.opens.get());
        bridge.stop();
        waitFor("stopped", () -> !bridge.isRunning());
        waitFor("second pipe closed", () -> pipes.pipe(1).closes.get() == 1);
        assertEquals(2, sinks.sink.closes.get());
    }

    @Test
    public void withoutAPipeTheBridgeKeepsTryingAndNeverOpensATrack() throws Exception {
        FakePipes pipes = new FakePipes();
        pipes.missing = true;
        FakeSinks sinks = new FakeSinks();
        AudioBridge bridge = bridge(pipes, sinks);
        bridge.start();
        waitFor("retries", () -> pipes.opens.get() >= 3);
        assertTrue(bridge.isRunning());
        assertFalse(bridge.isActive());
        assertEquals(0, sinks.opens.get());
        // The pipe appears (a terminal opened, the app created it): the bridge is reading it.
        pipes.missing = false;
        waitFor("pipe opened", () -> pipes.opened.size() == 1);
        pipes.pipe(0).feed(100);
        waitFor("100 bytes", () -> sinks.sink.bytes.get() == 100);
        bridge.stop();
        waitFor("stopped", () -> !bridge.isRunning());
        waitFor("track released", () -> sinks.sink.closes.get() == 1);
    }

    @Test
    public void aTrackThatCannotBeOpenedDropsTheSoundAndIsRetriedAfterTheBackoff() throws Exception {
        FakePipes pipes = new FakePipes();
        FakeSinks sinks = new FakeSinks();
        sinks.failFirst = 1;
        Changes changes = new Changes();
        AudioBridge bridge = bridge(pipes, sinks);
        bridge.setListener(changes);
        bridge.start();
        waitFor("pipe opened", () -> pipes.opened.size() == 1);
        FakePipe pipe = pipes.pipe(0);
        pipe.feed(1000);
        waitFor("track tried", () -> sinks.opens.get() == 1);
        // The bytes are dropped, the pipe is kept, nothing is active.
        Thread.sleep(10);
        assertEquals(0, sinks.sink.bytes.get());
        assertFalse(bridge.isActive());
        assertTrue(changes.seen.isEmpty());
        assertEquals(0, pipe.closes.get());
        // Within the backoff (20 ms) more sound is dropped without a new attempt ...
        pipe.feed(1000);
        waitFor("dropped", () -> pipe.chunks.isEmpty());
        assertEquals(1, sinks.opens.get());
        // ... after it the track is opened and plays.
        Thread.sleep(40);
        pipe.feed(1000);
        waitFor("1000 bytes", () -> sinks.sink.bytes.get() == 1000);
        assertEquals(2, sinks.opens.get());
        assertTrue(bridge.isActive());
        bridge.stop();
        waitFor("stopped", () -> !bridge.isRunning());
    }

    @Test
    public void startIsIdempotentOneThreadHoweverOftenItIsCalled() throws Exception {
        FakePipes pipes = new FakePipes();
        pipes.missing = true;
        AudioBridge bridge = bridge(pipes, new FakeSinks());
        int before = bridgeThreads();
        bridge.start();
        bridge.start();
        bridge.start();
        assertEquals(before + 1, bridgeThreads());
        bridge.stop();
        waitFor("stopped", () -> bridgeThreads() == before);
        // Restart after a stop is a fresh thread.
        bridge.start();
        assertTrue(bridge.isRunning());
        bridge.stop();
        waitFor("stopped", () -> !bridge.isRunning());
    }

    private static int bridgeThreads() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && "ThothTerm-AudioBridge".equals(t.getName())) n++;
        }
        return n;
    }

    @Test
    public void theLabelsNameLinuxAudioAndItsState() throws IOException {
        String strings = read(new File(MAIN, "res/values/strings.xml"));
        assertEquals("Linux audio", string(strings, "audio_bridge"));
        assertEquals("Linux audio", string(strings, "title_audio_bridge_preference"));
        assertEquals("%1$s, Linux audio: playing", string(strings, "service_notify_text_audio_playing"));
        assertEquals("%1$s, Linux audio: idle", string(strings, "service_notify_text_audio_idle"));
        // The guest-side helper is named so the user can find it; the edition ships it.
        assertTrue(string(strings, "summary_audio_bridge_preference").contains(AudioBridge.GUEST_SETUP_NAME));
        assertTrue(string(strings, "audio_bridge_toast_on").contains(AudioBridge.GUEST_SETUP_NAME));
        // No port anywhere: the stream is a pipe now.
        assertFalse(strings.contains("4713"));
        assertFalse(strings.contains("127.0.0.1"));

        String prefs = read(new File(MAIN, "res/xml/preferences.xml"));
        Matcher m = Pattern.compile("<androidx.preference.SwitchPreferenceCompat\\s+android:defaultValue=\"(\\w+)\"\\s+android:key=\""
                + AudioBridge.PREF_KEY + "\"").matcher(prefs);
        assertTrue("no switch for " + AudioBridge.PREF_KEY, m.find());
        assertEquals("false", m.group(1));
    }

    @Test
    public void theServiceOwnsTheBridgeAndTheTerminalOnlyFlipsThePreference() throws IOException {
        String service = read(new File(MAIN, "java/jackpal/androidterm/TermService.java"));
        assertTrue(service.contains("audioBridge = new AudioBridge(AudioBridge.hostFifo(this))"));
        assertTrue(service.contains("public void applyAudioBridge()"));
        assertTrue(service.contains("AudioBridge.PREF_KEY.equals(key)) applyAudioBridge()"));
        // The bridge's state reaches the notification and the lock on the main thread.
        assertTrue(service.contains("audioBridge.setListener(active -> mainHandler.post("));
        assertTrue(service.contains("R.string.service_notify_text_audio_playing"));
        assertTrue(service.contains("R.string.service_notify_text_audio_idle"));
        // Stopped on every way out: destroy, timeout and Exit.
        int onDestroy = service.indexOf("public void onDestroy()");
        int onTimeout = service.indexOf("public void onTimeout(");
        int shutdownAll = service.indexOf("public void shutdownAll()");
        for (int start : new int[]{onDestroy, onTimeout, shutdownAll}) {
            assertTrue(start >= 0);
            int end = service.indexOf("\n    }\n", start);
            assertTrue(service.substring(start, end).contains("stopAudioBridge()"));
        }
        // The service plays media in the background: both types, matching the manifest.
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE\n"
                + "                                | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK"));
        // No second notification button: the keep-awake one is the only action.
        assertEquals(1, count(service, "builder.addAction("));

        String term = read(new File(MAIN, "java/jackpal/androidterm/Term.java"));
        assertTrue(term.contains("AudioBridge.toggleWanted(this)"));
        assertTrue(term.contains("mTermService.applyAudioBridge()"));
        assertTrue(term.contains("R.id.menu_toggle_audio_bridge"));
        assertTrue(term.contains("AudioBridge.PREF_KEY.equals(key)"));
        assertFalse(term.contains("new AudioBridge("));

        String menu = read(new File(MAIN, "res/menu/main.xml"));
        Matcher m = Pattern.compile("<item android:id=\"@\\+id/menu_toggle_audio_bridge\"\\s+android:checkable=\"true\"")
                .matcher(menu);
        assertTrue("the menu item is not checkable", m.find());
        assertTrue("after Keep CPU awake", menu.indexOf("menu_toggle_keep_cpu_awake") < menu.indexOf("menu_toggle_audio_bridge"));

        String manifest = read(new File(MAIN, "AndroidManifest.xml"));
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"));
        assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse|mediaPlayback\""));
        // No microphone: the bridge only plays.
        assertFalse(manifest.contains("RECORD_AUDIO"));
        assertFalse(manifest.contains("FOREGROUND_SERVICE_MICROPHONE"));
    }

    /**
     * The pipe is the app's and reaches the guest through PRoot: the rootfs
     * manager creates it before a session and the mount point inside the
     * guest, and the runtime binds the directory; the bridge itself opens it
     * read-write and polls it, and never touches a socket.
     */
    @Test
    public void theAppOwnsThePipeAndBindsItsDirectoryIntoTheGuest() throws IOException {
        String rootfs = read(new File(MAIN, "java/com/thothterm/linux/RootfsManager.java"));
        assertTrue(rootfs.contains("AudioBridge.ensureFifo(com.thothterm.audio.AudioBridge.hostFifo(appContext))"));
        assertTrue(rootfs.contains("guestDir(root, com.thothterm.audio.AudioBridge.GUEST_DIR)"));
        assertTrue(rootfs.contains("\"--bind=\" + dir.getAbsolutePath() + \":\" + com.thothterm.audio.AudioBridge.GUEST_DIR"));
        String runtime = read(new File(MAIN, "java/com/thothterm/linux/GardenRuntime.java"));
        assertTrue(runtime.contains("binds.addAll(manager.audioBinds());"));

        String bridge = read(new File(MAIN, "java/com/thothterm/audio/AudioBridge.java"));
        assertTrue(bridge.contains("ParcelFileDescriptor.MODE_READ_WRITE"));
        assertTrue(bridge.contains("Os.poll("));
        assertTrue(bridge.contains("Os.mkfifo("));
        assertFalse(bridge.contains("java.net."));
        assertFalse(bridge.contains("Socket"));
    }

    /** The guest-side helper is the edition's; garden-common only copies the asset. */
    @Test
    public void gardenCommonInstallsTheEditionsHelperAndKnowsNoPackageManager() throws IOException {
        String rootfs = read(new File(MAIN, "java/com/thothterm/linux/RootfsManager.java"));
        assertTrue(rootfs.contains("copyEditionAsset(root, com.thothterm.audio.AudioBridge.GUEST_SETUP_ASSET"));
        assertTrue(rootfs.contains("com.thothterm.audio.AudioBridge.GUEST_SETUP_NAME), 0755)"));
        assertFalse(new File(MAIN, "assets/" + AudioBridge.GUEST_SETUP_ASSET).exists());
        String bridge = read(new File(MAIN, "java/com/thothterm/audio/AudioBridge.java"));
        for (String word : new String[]{"pacman", "apt-get", "apt ", "dpkg"}) {
            assertFalse("garden-common's bridge names " + word, bridge.contains(word));
        }
    }

    private static int count(String text, String token) {
        int n = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) n++;
        return n;
    }

    private static String string(String xml, String name) {
        Matcher m = Pattern.compile("<string name=\"" + name + "\">([^<]*)</string>")
                .matcher(xml);
        assertTrue("no string " + name, m.find());
        return m.group(1).replace("\\'", "'");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
