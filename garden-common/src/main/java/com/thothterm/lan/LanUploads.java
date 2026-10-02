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

import com.thothterm.upload.StagingJournal;
import com.thothterm.upload.UploadBatch;
import com.thothterm.upload.UploadError;
import com.thothterm.upload.UploadFs;
import com.thothterm.upload.UploadTarget;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The browser uploads of one LAN Mode run. Each belongs to the browser that
 * began it and to one of that browser's terminals, whose current directory
 * the server read when it began; a browser can name neither a directory nor
 * another browser's upload. Signing out, the terminal ending, LAN Mode going
 * off and a browser that stops sending all abandon an upload, which removes
 * whatever it had not finished.
 */
final class LanUploads {
    static final int MAX_UPLOADS = 8;
    /** An upload that receives nothing for this long is abandoned. */
    static final long IDLE_MS = 120_000;

    /** What LAN Mode needs from the app to upload into a terminal's directory. */
    interface Host {
        /** The current directory of the browser terminal on {@code pty}, now. */
        UploadTarget target(Pty pty) throws UploadError;

        UploadFs fs();

        StagingJournal journal();
    }

    final class Upload {
        final String id;
        final int browser;
        final RemoteTerminals.Terminal terminal;
        final UploadBatch batch;
        private long lastUsedMs;

        private Upload(String id, int browser, RemoteTerminals.Terminal terminal, UploadBatch batch,
                       long now) {
            this.id = id;
            this.browser = browser;
            this.terminal = terminal;
            this.batch = batch;
            this.lastUsedMs = now;
        }
    }

    private final Host host;
    private final LanAuth.Clock clock;
    private final SecureRandom random;
    private final LanLog log;
    private final Map<String, Upload> uploads = new HashMap<>();
    /** Uploads with a file streaming in right now; never idle. */
    private final Map<Upload, Boolean> active = new HashMap<>();
    /** Terminals whose upload is being opened right now (see {@link #begin}). */
    private final java.util.Set<RemoteTerminals.Terminal> opening = new java.util.HashSet<>();
    private boolean closed;

    LanUploads(Host host, LanAuth.Clock clock, SecureRandom random, LanLog log) {
        this.host = host;
        this.clock = clock;
        this.random = random;
        this.log = log;
    }

    /** Snapshot the terminal's directory and open an upload into it. */
    Upload begin(int browser, RemoteTerminals.Terminal terminal, UploadBatch.Kind kind,
                 String folderName, long expectedBytes) throws UploadError {
        // The checks and a reservation happen under one lock, so two begins
        // racing for the same terminal (or the last slot) cannot both pass
        // while the slow part -- the terminal's directory, the batch -- runs
        // unlocked.
        synchronized (this) {
            if (closed) throw new UploadError(UploadError.Code.CANCELLED, "LAN Mode is off");
            if (uploads.size() + opening.size() >= MAX_UPLOADS) {
                throw new UploadError(UploadError.Code.BUSY, "too many uploads");
            }
            if (opening.contains(terminal)) throw new UploadError(UploadError.Code.BUSY, "terminal busy");
            for (Upload u : uploads.values()) {
                if (u.terminal == terminal) throw new UploadError(UploadError.Code.BUSY, "terminal busy");
            }
            opening.add(terminal);
        }
        try {
            UploadTarget target = host.target(terminal.pty);
            UploadBatch batch = UploadBatch.begin(host.fs(), host.journal(), target, kind, folderName,
                    expectedBytes);
            byte[] raw = new byte[16];
            random.nextBytes(raw);
            Upload upload = new Upload(Base64.getUrlEncoder().withoutPadding().encodeToString(raw),
                    browser, terminal, batch, clock.nowMs());
            synchronized (this) {
                if (closed) {
                    batch.cancel();
                    throw new UploadError(UploadError.Code.CANCELLED, "LAN Mode is off");
                }
                uploads.put(upload.id, upload);
            }
            return upload;
        } finally {
            synchronized (this) {
                opening.remove(terminal);
            }
        }
    }

    /** Browser {@code browser}'s open upload {@code id}, or null. */
    synchronized Upload find(String id, int browser) {
        if (id == null) return null;
        Upload u = uploads.get(id);
        if (u == null || u.browser != browser) return null;
        u.lastUsedMs = clock.nowMs();
        return u;
    }

    /** The upload finished or failed; forget it (abandoning it if still open). */
    void end(Upload upload) {
        synchronized (this) {
            uploads.remove(upload.id);
            active.remove(upload);
        }
        upload.batch.cancel();
    }

    void cancelBrowser(int browser) {
        for (Upload u : remove(u -> u.browser == browser)) u.batch.cancel();
    }

    /** Abandon uploads whose terminal is gone or that went quiet. */
    void sweep(RemoteTerminals terminals) {
        final long cutoff = clock.nowMs() - IDLE_MS;
        List<Upload> gone = remove(u -> !terminals.isLive(u.terminal)
                || (u.lastUsedMs < cutoff && !receiving(u)));
        for (Upload u : gone) {
            u.batch.cancel();
            log.info("Browser upload abandoned");
        }
    }

    /** LAN Mode is off: abandon everything and accept nothing more. */
    void closeAll() {
        List<Upload> all;
        synchronized (this) {
            closed = true;
            all = new ArrayList<>(uploads.values());
            uploads.clear();
        }
        for (Upload u : all) u.batch.cancel();
        if (!all.isEmpty()) log.info("Abandoned " + all.size() + " browser upload(s)");
    }

    synchronized int count() {
        return uploads.size();
    }

    /** A file of {@code upload} is streaming in. */
    synchronized void receiving(Upload upload, boolean on) {
        if (on) active.put(upload, Boolean.TRUE);
        else active.remove(upload);
        upload.lastUsedMs = clock.nowMs();
    }

    private synchronized boolean receiving(Upload upload) {
        return active.containsKey(upload);
    }

    private interface Match {
        boolean test(Upload u);
    }

    private synchronized List<Upload> remove(Match match) {
        List<Upload> removed = new ArrayList<>();
        Iterator<Upload> it = uploads.values().iterator();
        while (it.hasNext()) {
            Upload u = it.next();
            if (match.test(u)) {
                removed.add(u);
                it.remove();
            }
        }
        return removed;
    }
}
