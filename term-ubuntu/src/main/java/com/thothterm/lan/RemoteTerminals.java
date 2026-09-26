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

package com.thothterm.lan;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The browser terminals of one LAN Mode run. Each is a dedicated PTY of its
 * own -- never one of the phone's windows -- owned by the browser that
 * opened it. A terminal whose browser went away is kept for
 * {@link #GRACE_MS} so a reload or a brief Wi-Fi drop can reattach, then
 * hung up. Its output is kept meanwhile, up to {@link #REPLAY_BYTES}, and
 * replayed on reattach.
 */
final class RemoteTerminals {
    static final long GRACE_MS = 30_000;
    static final int MAX_TERMINALS = 8;
    static final int MAX_PER_BROWSER = 4;
    static final int REPLAY_BYTES = 64 * 1024;

    /** Where an attached terminal's output goes: one WebSocket. */
    interface Sink {
        void output(byte[] data, int offset, int length) throws IOException;

        /** The shell has exited; the terminal is gone. */
        void exited();
    }

    static final class LimitReached extends Exception {
        LimitReached(String reason) {
            super(reason);
        }
    }

    final class Terminal {
        final String id;
        final int browser;
        final Pty pty;
        private Sink sink;
        /** Removed from the collection; nothing may attach any more. */
        private boolean gone;
        private long detachedAtMs;
        private final byte[] replay = new byte[REPLAY_BYTES];
        private int replayStart;
        private int replayLength;

        private Terminal(String id, int browser, Pty pty) {
            this.id = id;
            this.browser = browser;
            this.pty = pty;
        }

        /** Keyboard input from the attached browser. */
        void input(byte[] data) throws IOException {
            pty.output().write(data);
            pty.output().flush();
        }

        void resize(int columns, int rows) throws IOException {
            pty.resize(columns, rows);
        }

        private void pump() {
            byte[] buffer = new byte[8192];
            try {
                int n;
                while ((n = pty.input().read(buffer)) > 0) {
                    deliver(buffer, n);
                }
            } catch (IOException e) {
                // EIO once the slave side is gone: the shell exited or was hung up.
            }
        }

        private synchronized void deliver(byte[] buffer, int n) {
            if (sink != null) {
                try {
                    sink.output(buffer, 0, n);
                    return;
                } catch (IOException e) {
                    // The socket died under us; the reader side detaches it.
                }
            }
            keep(buffer, n);
        }

        private void keep(byte[] data, int n) {
            for (int i = 0; i < n; ++i) {
                int slot = (replayStart + replayLength) % REPLAY_BYTES;
                replay[slot] = data[i];
                if (replayLength < REPLAY_BYTES) {
                    ++replayLength;
                } else {
                    replayStart = (replayStart + 1) % REPLAY_BYTES;
                }
            }
        }

        /** Attach unless another socket holds the terminal or it is gone. */
        private synchronized boolean tryAttach(Sink newSink) throws IOException {
            if (gone || sink != null) return false;
            sink = newSink;
            if (replayLength > 0) {
                byte[] kept = new byte[replayLength];
                for (int i = 0; i < replayLength; ++i) kept[i] = replay[(replayStart + i) % REPLAY_BYTES];
                replayStart = 0;
                replayLength = 0;
                newSink.output(kept, 0, kept.length);
            }
            return true;
        }

        private synchronized boolean detach(Sink oldSink, long now) {
            if (sink != oldSink) return false;
            sink = null;
            detachedAtMs = now;
            return true;
        }

        private synchronized boolean expireIfDetachedSince(long cutoff) {
            if (sink != null || detachedAtMs > cutoff) return false;
            gone = true;
            return true;
        }

        private synchronized boolean isAttached() {
            return sink != null;
        }

        /** Mark the terminal gone and hand back whatever socket was attached. */
        private synchronized Sink retire() {
            gone = true;
            Sink s = sink;
            sink = null;
            return s;
        }
    }

    private final Pty.Factory factory;
    private final LanAuth.Clock clock;
    private final LanLog log;
    private final SecureRandom random;
    private final Map<String, Terminal> terminals = new HashMap<>();
    private int opening;
    private boolean closed;

    RemoteTerminals(Pty.Factory factory, LanAuth.Clock clock, LanLog log, SecureRandom random) {
        this.factory = factory;
        this.clock = clock;
        this.log = log;
        this.random = random;
    }

    /**
     * Reattach {@code browser} to its detached terminal {@code id}, or open a
     * new one. Returns the terminal and whether it is new.
     */
    Attachment attach(int browser, String id, int columns, int rows, Sink sink)
            throws IOException, LimitReached {
        Terminal existing;
        synchronized (this) {
            if (closed) throw new IOException("LAN Mode is off");
            existing = id == null ? null : terminals.get(id);
        }
        // A terminal belongs to the browser that opened it; another browser
        // presenting its id simply gets a new terminal.
        if (existing != null && existing.browser == browser && existing.tryAttach(sink)) {
            existing.resize(columns, rows);
            log.info("Browser terminal reattached");
            return new Attachment(existing, false);
        }
        synchronized (this) {
            if (closed) throw new IOException("LAN Mode is off");
            if (terminals.size() + opening >= MAX_TERMINALS) throw new LimitReached("terminal limit");
            int mine = 0;
            for (Terminal t : terminals.values()) if (t.browser == browser) ++mine;
            if (mine >= MAX_PER_BROWSER) throw new LimitReached("per-browser terminal limit");
            ++opening;
        }
        Pty pty = null;
        try {
            pty = factory.open(columns, rows);
        } finally {
            synchronized (this) {
                --opening;
                if (closed && pty != null) {
                    pty.hangUp();
                    pty = null;
                }
            }
        }
        if (pty == null) throw new IOException("LAN Mode is off");
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        Terminal terminal = new Terminal(Base64.getUrlEncoder().withoutPadding().encodeToString(raw),
                browser, pty);
        terminal.tryAttach(sink);
        synchronized (this) {
            if (closed) {
                terminal.retire();
                pty.hangUp();
                throw new IOException("LAN Mode is off");
            }
            terminals.put(terminal.id, terminal);
            log.info("Browser terminal opened; terminals=" + terminals.size());
        }
        Thread pump = new Thread(terminal::pump, "LAN terminal output");
        pump.setDaemon(true);
        pump.start();
        Thread waiter = new Thread(() -> {
            terminal.pty.waitFor();
            onExited(terminal);
        }, "LAN terminal watcher");
        waiter.setDaemon(true);
        waiter.start();
        return new Attachment(terminal, true);
    }

    static final class Attachment {
        final Terminal terminal;
        final boolean created;

        Attachment(Terminal terminal, boolean created) {
            this.terminal = terminal;
            this.created = created;
        }
    }

    /** The browser's socket went away; start the grace period. */
    void detach(Terminal terminal, Sink sink) {
        if (terminal.detach(sink, clock.nowMs())) {
            log.info("Browser terminal detached; grace " + (GRACE_MS / 1000) + " s");
        }
    }

    /** Hang up terminals whose browser has been gone longer than the grace period. */
    void sweep() {
        List<Terminal> expired = new ArrayList<>();
        synchronized (this) {
            long cutoff = clock.nowMs() - GRACE_MS;
            Iterator<Terminal> it = terminals.values().iterator();
            while (it.hasNext()) {
                Terminal t = it.next();
                if (t.expireIfDetachedSince(cutoff)) {
                    expired.add(t);
                    it.remove();
                }
            }
        }
        for (Terminal t : expired) {
            t.pty.hangUp();
            log.info("Browser terminal closed after grace period");
        }
    }

    private void onExited(Terminal terminal) {
        synchronized (this) {
            if (terminals.remove(terminal.id) == null) return;
        }
        Sink sink = terminal.retire();
        if (sink != null) sink.exited();
        terminal.pty.hangUp();
        log.info("Browser terminal shell exited");
    }

    /** Hang up every terminal and refuse new ones. */
    void closeAll() {
        List<Terminal> all;
        synchronized (this) {
            closed = true;
            all = new ArrayList<>(terminals.values());
            terminals.clear();
        }
        for (Terminal t : all) {
            t.retire();
            t.pty.hangUp();
        }
        if (!all.isEmpty()) log.info("Closed " + all.size() + " browser terminal(s)");
    }

    synchronized int count() {
        return terminals.size();
    }

    synchronized int attachedCount() {
        int n = 0;
        for (Terminal t : terminals.values()) if (t.isAttached()) ++n;
        return n;
    }

    synchronized int[] pids() {
        int[] result = new int[terminals.size()];
        int i = 0;
        for (Terminal t : terminals.values()) result[i++] = t.pty.pid();
        return result;
    }
}
