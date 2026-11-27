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

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * "Linux audio": plays what the guest's own sound server writes into a FIFO.
 * <p>
 * The guest runs its ordinary PulseAudio with {@code module-pipe-sink} as the
 * default sink, writing raw PCM (signed 16-bit little-endian, 48 kHz, stereo)
 * into the named pipe {@link #GUEST_FIFO}. That path is a directory of the
 * app's private storage ({@link #hostDir}) bound into the guest by PRoot, so
 * the same pipe is {@link #hostFifo} on this side. Programs in the guest just
 * use PulseAudio; this class reads the pipe and writes the bytes, unparsed, to
 * an {@link AudioTrack}.
 * <p>
 * It costs nothing while nothing plays. PulseAudio's {@code module-suspend-on-idle}
 * suspends the sink a few seconds after the last stream ends, and a suspended
 * pipe sink writes nothing: no mixing in the guest, no bytes through PRoot.
 * On this side a read that waits for data is a {@code poll} the kernel wakes
 * only when bytes arrive, and the {@link AudioTrack} -- the thing that keeps
 * the audio hardware powered -- exists only while sound flows: it is opened
 * on the first bytes and paused and released after {@link #IDLE_TIMEOUT_MS}
 * without any. {@link #isActive()} says whether a track is open right now,
 * and a {@link Listener} hears every change, so the service can show it and
 * hold the CPU awake only while it matters.
 * <ul>
 * <li>The <b>FIFO</b> is the app's: {@link #ensureFifo} creates it (and its
 * directory) before the guest starts and whenever the bridge opens it, so the
 * app never waits for a writer, and the guest's sound server only ever finds
 * it there. PulseAudio 17's {@code module-pipe-sink} keeps a pipe that
 * already exists ({@code mkfifo} failing with {@code EEXIST} is fine) and
 * then never unlinks it, whereas one it created itself it removes on exit:
 * with the app as the owner the inode is stable across PulseAudio restarts,
 * and the bridge's open descriptor stays the right one.</li>
 * <li>The bridge opens the FIFO <b>read-write</b>. A read-only open would
 * block until a writer appears and every PulseAudio exit would end the stream
 * ({@code EOF} once the last writer closes), which would mean reopen loops and
 * a writer/reader race at every restart. Holding a write end too, the open
 * returns at once, a read with no writer simply waits, and a restarted
 * PulseAudio just opens the same pipe and writes again. Nothing is ever
 * written through the bridge's write end.</li>
 * <li>{@link #stop()} <b>interrupts</b> a waiting read from another thread
 * through a self-pipe the read polls alongside the FIFO; it never closes a
 * descriptor another thread is polling, the worker closes its own.</li>
 * <li>The <b>preference</b> {@link #PREF_KEY} (default off) is the record of
 * what the user wants. The menu and the Settings switch both flip it. The
 * bridge is owned by the terminal service and follows the preference;
 * {@link #start()} and {@link #stop()} are idempotent.</li>
 * </ul>
 * The guest-side setup is the edition's: garden-common installs the helper an
 * edition ships at {@link #GUEST_SETUP_ASSET} as {@code /usr/local/bin/}
 * {@link #GUEST_SETUP_NAME}, and knows nothing else about it.
 */
public final class AudioBridge {
    /** SharedPreferences key: whether the user wants the guest's audio played. */
    public static final String PREF_KEY = "audio_bridge";
    public static final boolean PREF_DEFAULT = false;

    /**
     * Where the guest's PulseAudio writes its default sink: the app's audio
     * directory, bound at {@link #GUEST_DIR}, holding the one named pipe. The
     * edition's setup helper points {@code module-pipe-sink} at
     * {@link #GUEST_FIFO}.
     */
    public static final String GUEST_DIR = "/run/thothterm/audio";
    public static final String FIFO_NAME = "pulse.fifo";
    public static final String GUEST_FIFO = GUEST_DIR + "/" + FIFO_NAME;
    /** Under the app's files directory: {@code linux/audio}. */
    static final String HOST_DIR = "linux/audio";

    /** The stream format both sides agree on: s16le, 48 kHz, stereo. */
    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 2;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;

    /** How much audio the track buffers at least: jitter on the pipe never starves it. */
    static final int TRACK_BUFFER_MS = 250;
    /** One read from the pipe, about 20 ms of audio. */
    static final int READ_CHUNK_BYTES = 3840;

    /**
     * Without data for this long the track is paused and released and the
     * bridge turns inactive. PulseAudio suspends an idle sink after its own
     * timeout (5 s in the edition's configuration) and writes nothing from
     * then on, so this follows a few seconds after the last sound.
     */
    public static final long IDLE_TIMEOUT_MS = 1000;

    static final long BACKOFF_FIRST_MS = 1000;
    static final long BACKOFF_MAX_MS = 10000;

    /** The edition's guest-side setup helper, if it ships one, and the name it gets in the guest. */
    public static final String GUEST_SETUP_ASSET = "garden/audio/thothterm-audio-setup";
    public static final String GUEST_SETUP_NAME = "thothterm-audio-setup";

    /** Where the PCM goes; the real one is an {@link AudioTrack}, tests count bytes. */
    interface Sink {
        void write(byte[] data, int offset, int length) throws IOException;

        void close();
    }

    /** Opens a sink per burst of sound, so a format or device failure is retried later. */
    interface SinkFactory {
        Sink open() throws IOException;
    }

    /** The read side of the FIFO; the real one polls a descriptor, tests hand over bytes. */
    interface Pipe {
        /**
         * Wait up to {@code timeoutMs} (negative: without limit) for data and
         * read what is there, at most {@code buffer.length}: the count read,
         * 0 when the time is up or {@link #wake()} was called, -1 when the
         * pipe ended or broke.
         */
        int read(byte[] buffer, long timeoutMs) throws IOException;

        /** From any thread: make a waiting {@link #read} return 0 now. */
        void wake();

        /** Called by the reading thread only, once it has stopped reading. */
        void close();
    }

    /** Opens the FIFO; a failure is retried with the backoff. */
    interface PipeFactory {
        Pipe open() throws IOException;
    }

    /** Told on the bridge's thread whenever sound starts or stops flowing. */
    public interface Listener {
        void onAudioActiveChanged(boolean active);
    }

    private final PipeFactory pipes;
    private final SinkFactory sinks;
    private final long idleTimeoutMs;
    private final long backoffFirstMs;
    private final long backoffMaxMs;

    private final Object lock = new Object();
    private volatile boolean running;
    private volatile boolean active;
    @Nullable
    private volatile Listener listener;
    @Nullable
    private Thread worker;
    /** The pipe the worker is reading, for {@link #stop()} to wake. */
    @Nullable
    private Pipe pipe;

    /** The real bridge: the app's FIFO, the system's AudioTrack. */
    public AudioBridge(@NonNull File fifo) {
        this(new FifoPipeFactory(fifo), new AudioTrackSinkFactory(), IDLE_TIMEOUT_MS,
                BACKOFF_FIRST_MS, BACKOFF_MAX_MS);
    }

    AudioBridge(PipeFactory pipes, SinkFactory sinks, long idleTimeoutMs,
                long backoffFirstMs, long backoffMaxMs) {
        this.pipes = pipes;
        this.sinks = sinks;
        this.idleTimeoutMs = idleTimeoutMs;
        this.backoffFirstMs = backoffFirstMs;
        this.backoffMaxMs = backoffMaxMs;
    }

    /* ---- the pure parts ---- */

    /**
     * Bytes of audio for {@code millis} at the stream format, a whole number
     * of frames; at least one frame.
     */
    public static int bytesForMillis(int millis) {
        long bytes = (long) SAMPLE_RATE * FRAME_BYTES * Math.max(0, millis) / 1000L;
        return Math.max(FRAME_BYTES, (int) Math.min(Integer.MAX_VALUE, bytes));
    }

    /**
     * The track's buffer: what the device needs at least ({@code minBufferSize}
     * from {@link AudioTrack#getMinBufferSize}, or an error value at or below
     * zero) or {@link #TRACK_BUFFER_MS} of audio, whichever is larger, rounded
     * up to whole frames.
     */
    public static int trackBufferBytes(int minBufferSize) {
        int wanted = Math.max(Math.max(0, minBufferSize), bytesForMillis(TRACK_BUFFER_MS));
        int remainder = wanted % FRAME_BYTES;
        return remainder == 0 ? wanted : wanted + (FRAME_BYTES - remainder);
    }

    /**
     * The delay before retry {@code attempt} (0 for the first retry):
     * {@code first}, doubling, never above {@code max}.
     */
    public static long backoffMillis(int attempt, long first, long max) {
        if (first <= 0) return 0;
        if (attempt <= 0) return Math.min(first, max);
        long delay = first;
        for (int i = 0; i < attempt && delay < max; i++) {
            delay = delay * 2;
        }
        return Math.min(delay, max);
    }

    /** The real schedule: 1 s, 2 s, 4 s, 8 s, then 10 s. */
    public static long backoffMillis(int attempt) {
        return backoffMillis(attempt, BACKOFF_FIRST_MS, BACKOFF_MAX_MS);
    }

    /**
     * How long a read may wait: without limit while no track is open (idle
     * costs nothing), otherwise what is left of the idle timeout since the
     * last data, never negative.
     */
    public static long readTimeoutMillis(boolean trackOpen, long lastDataMs, long nowMs,
                                         long idleTimeoutMs) {
        if (!trackOpen) return -1;
        return Math.max(0, idleTimeoutMs - (nowMs - lastDataMs));
    }

    /** Whether a track open since the last data at {@code lastDataMs} is idle at {@code nowMs}. */
    public static boolean idle(long lastDataMs, long nowMs, long idleTimeoutMs) {
        return nowMs - lastDataMs >= idleTimeoutMs;
    }

    /** The app-side directory bound at {@link #GUEST_DIR}. */
    public static File hostDir(@NonNull Context context) {
        return new File(context.getApplicationContext().getFilesDir(), HOST_DIR);
    }

    /** The app-side path of {@link #GUEST_FIFO}. */
    public static File hostFifo(@NonNull Context context) {
        return new File(hostDir(context), FIFO_NAME);
    }

    /* ---- the bridge ---- */

    /** Whether the bridge is wanted and its thread alive; not whether sound flows. */
    public boolean isRunning() {
        synchronized (lock) {
            return running && worker != null && worker.isAlive();
        }
    }

    /** Whether sound is flowing: a track is open, data arrived within the idle timeout. */
    public boolean isActive() {
        return active;
    }

    /** One listener, told on the bridge's thread; null to clear. */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Start reading; a no-op while already running. */
    public void start() {
        synchronized (lock) {
            if (running && worker != null && worker.isAlive()) return;
            running = true;
            Thread thread = new Thread(this::run, "ThothTerm-AudioBridge");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        }
    }

    /**
     * Stop; safe to repeat and to call while stopped. Returns at once: the
     * thread is woken out of its read, finds it is no longer wanted, and
     * releases the track and the pipe itself.
     */
    public void stop() {
        Thread thread;
        Pipe p;
        synchronized (lock) {
            running = false;
            thread = worker;
            worker = null;
            p = pipe;
        }
        if (p != null) p.wake();
        if (thread != null) thread.interrupt();
    }

    /**
     * Whether the calling thread is still the bridge's worker. A stop()
     * followed at once by start() replaces the worker; the old thread must
     * then end rather than keep reading beside the new one, so every loop
     * checks this, not the shared running flag alone.
     */
    private boolean current() {
        synchronized (lock) {
            return running && worker == Thread.currentThread();
        }
    }

    private void setActive(boolean now) {
        if (active == now) return;
        active = now;
        Listener l = listener;
        if (l != null) l.onAudioActiveChanged(now);
    }

    private void run() {
        int attempt = 0;
        while (current() && !Thread.currentThread().isInterrupted()) {
            Pipe p;
            try {
                p = pipes.open();
            } catch (IOException e) {
                ThothLog.w(LogCategory.APP, "Audio bridge cannot open the pipe: " + e.getMessage());
                if (!sleep(backoffMillis(attempt++, backoffFirstMs, backoffMaxMs))) break;
                continue;
            }
            synchronized (lock) {
                if (!running || worker != Thread.currentThread()) {
                    p.close();
                    return;
                }
                pipe = p;
            }
            boolean streamed = false;
            try {
                streamed = pump(p);
            } catch (IOException e) {
                if (current()) ThothLog.w(LogCategory.APP, "Audio bridge lost the pipe: " + e.getMessage());
            } finally {
                synchronized (lock) {
                    if (pipe == p) pipe = null;
                }
                p.close();
                // A replaced worker leaves the flag to its successor.
                if (current() || !running) setActive(false);
            }
            if (!current()) break;
            // A pipe that carried sound starts the schedule again; one that broke at once climbs it.
            if (streamed) attempt = 0;
            if (!sleep(backoffMillis(attempt++, backoffFirstMs, backoffMaxMs))) break;
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Read the pipe until it ends or the bridge is stopped: a track is opened
     * on the first bytes and released once {@link #idleTimeoutMs} pass
     * without any. Returns whether any sound was played.
     */
    private boolean pump(Pipe p) throws IOException {
        byte[] buffer = new byte[READ_CHUNK_BYTES];
        Sink sink = null;
        boolean streamed = false;
        long lastData = 0;
        int trackFailures = 0;
        long trackRetryAt = 0;
        try {
            while (current()) {
                long now = System.nanoTime() / 1000000L;
                if (sink != null && idle(lastData, now, idleTimeoutMs)) {
                    ThothLog.d(LogCategory.APP, "Audio bridge: idle, track released");
                    sink.close();
                    sink = null;
                    setActive(false);
                }
                int n = p.read(buffer, readTimeoutMillis(sink != null, lastData, now, idleTimeoutMs));
                if (n < 0) {
                    if (current()) ThothLog.i(LogCategory.APP, "Audio bridge: the pipe ended");
                    return streamed;
                }
                if (n == 0 || !current()) continue;
                now = System.nanoTime() / 1000000L;
                if (sink == null) {
                    if (now < trackRetryAt) continue; // The track failed a moment ago: discard until the retry.
                    try {
                        sink = sinks.open();
                    } catch (IOException e) {
                        ThothLog.w(LogCategory.APP, "Audio bridge cannot play: " + e.getMessage());
                        trackRetryAt = now + backoffMillis(trackFailures++, backoffFirstMs, backoffMaxMs);
                        continue;
                    }
                    trackFailures = 0;
                    ThothLog.d(LogCategory.APP, "Audio bridge: sound, track opened");
                    streamed = true;
                    setActive(true);
                }
                sink.write(buffer, 0, n);
                lastData = now;
            }
            return streamed;
        } finally {
            if (sink != null) sink.close();
        }
    }

    /* ---- the preference ---- */

    public static boolean isWanted(@NonNull Context context) {
        return prefs(context).getBoolean(PREF_KEY, PREF_DEFAULT);
    }

    public static void setWanted(@NonNull Context context, boolean wanted) {
        prefs(context).edit().putBoolean(PREF_KEY, wanted).apply();
    }

    /** Flip the preference; returns the new value. */
    public static boolean toggleWanted(@NonNull Context context) {
        boolean wanted = !isWanted(context);
        setWanted(context, wanted);
        return wanted;
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    /* ---- the device's half ---- */

    /**
     * Make sure {@code fifo} is a named pipe, creating its directory and
     * itself when missing. The app owns the pipe (see the class comment):
     * called before a guest session starts and whenever the bridge opens it.
     * Fails when something else sits at that path.
     */
    public static void ensureFifo(@NonNull File fifo) throws IOException {
        File dir = fifo.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        try {
            StructStat st = null;
            try {
                st = Os.lstat(fifo.getAbsolutePath());
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.ENOENT) throw e;
            }
            if (st == null) {
                Os.mkfifo(fifo.getAbsolutePath(), 0600);
            } else if (!OsConstants.S_ISFIFO(st.st_mode)) {
                throw new IOException(fifo + " is not a named pipe");
            }
        } catch (ErrnoException e) {
            throw new IOException("Cannot create the audio pipe " + fifo + ": " + e.getMessage(), e);
        }
    }

    /** The FIFO opened read-write and polled together with a self-pipe for {@link Pipe#wake()}. */
    private static final class FifoPipeFactory implements PipeFactory {
        private final File fifo;

        FifoPipeFactory(File fifo) {
            this.fifo = fifo;
        }

        @Override
        public Pipe open() throws IOException {
            ensureFifo(fifo);
            // Read-write: returns at once with no writer, and never EOF when one leaves.
            final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(fifo,
                    ParcelFileDescriptor.MODE_READ_WRITE);
            final FileDescriptor[] wake;
            try {
                wake = Os.pipe();
                Os.fcntlInt(wake[1], OsConstants.F_SETFL, OsConstants.O_NONBLOCK);
            } catch (ErrnoException e) {
                closeQuietly(pfd);
                throw new IOException("Cannot create the wake pipe: " + e.getMessage(), e);
            }
            return new Pipe() {
                @Override
                public int read(byte[] buffer, long timeoutMs) throws IOException {
                    StructPollfd data = new StructPollfd();
                    data.fd = pfd.getFileDescriptor();
                    data.events = (short) OsConstants.POLLIN;
                    StructPollfd woken = new StructPollfd();
                    woken.fd = wake[0];
                    woken.events = (short) OsConstants.POLLIN;
                    StructPollfd[] fds = {data, woken};
                    int timeout = timeoutMs < 0 ? -1 : (int) Math.min(timeoutMs, Integer.MAX_VALUE);
                    try {
                        int ready = Os.poll(fds, timeout);
                        if (ready <= 0 || woken.revents != 0) return 0;
                        if ((data.revents & OsConstants.POLLIN) != 0) {
                            int n = Os.read(pfd.getFileDescriptor(), buffer, 0, buffer.length);
                            return n == 0 ? -1 : n;
                        }
                        // POLLERR, POLLHUP or POLLNVAL without data: the pipe is gone.
                        return data.revents != 0 ? -1 : 0;
                    } catch (ErrnoException e) {
                        if (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN) return 0;
                        throw new IOException("Audio pipe: " + e.getMessage(), e);
                    }
                }

                @Override
                public void wake() {
                    try {
                        Os.write(wake[1], new byte[]{1}, 0, 1);
                    } catch (ErrnoException | InterruptedIOException e) {
                        // Full (already woken) or closed: the reader is leaving anyway.
                    }
                }

                @Override
                public void close() {
                    closeQuietly(pfd);
                    for (FileDescriptor fd : wake) {
                        try {
                            Os.close(fd);
                        } catch (ErrnoException ignored) {
                            // Already closed.
                        }
                    }
                }
            };
        }

        private static void closeQuietly(ParcelFileDescriptor pfd) {
            try {
                pfd.close();
            } catch (IOException ignored) {
                // Closing is best effort.
            }
        }
    }

    /** A streaming media {@link AudioTrack} in the stream format. */
    private static final class AudioTrackSinkFactory implements SinkFactory {
        @Override
        public Sink open() throws IOException {
            final AudioTrack track;
            try {
                int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(trackBufferBytes(minBuffer))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } catch (RuntimeException e) {
                // UnsupportedOperationException or IllegalArgumentException from the builder.
                throw new IOException("Cannot open an audio track: " + e.getMessage(), e);
            }
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                throw new IOException("Audio track not initialized");
            }
            track.play();
            return new Sink() {
                @Override
                public void write(byte[] data, int offset, int length) throws IOException {
                    int done = 0;
                    while (done < length) {
                        // Blocking: paces the pipe read to the device's clock.
                        int written = track.write(data, offset + done, length - done);
                        if (written < 0) throw new IOException("Audio track write failed: " + written);
                        if (written == 0 && track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                            throw new IOException("Audio track stopped");
                        }
                        done += written;
                    }
                }

                @Override
                public void close() {
                    try {
                        track.pause();
                        track.flush();
                    } catch (IllegalStateException ignored) {
                        // Never initialized that far; release is all that matters.
                    }
                    track.release();
                }
            };
        }
    }
}
