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

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where the optional provisioning of an installed environment stands
 * (administrator tools: sudo, and on Arch a missing pacman keyring). It runs
 * on a background thread after the terminal is ready and never gates it: a
 * failure leaves the terminal usable, is reported, and can be retried. It
 * never extracts anything and never touches {@code /home}. Thread-safe.
 */
public final class OptionalSetup {
    public enum Status {
        /** Nothing queued yet in this process. */
        NOT_STARTED,
        /** Queued behind the terminal handoff. */
        PENDING,
        RUNNING,
        /** The last run failed; the terminal is unaffected and a retry may be queued. */
        FAILED,
        DONE
    }

    public interface Listener {
        /** Called on the thread that changed the status. */
        void onOptionalSetup(Status status);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private Status status = Status.NOT_STARTED;
    private String failure;

    /**
     * Queue a run. False when one is already queued or running: calls are
     * coalesced, so at most one run is ever in flight.
     */
    public boolean queue() {
        synchronized (this) {
            if (status == Status.PENDING || status == Status.RUNNING) return false;
            status = Status.PENDING;
        }
        fire(Status.PENDING);
        return true;
    }

    /** The queued run started. */
    public void started() {
        synchronized (this) {
            if (status != Status.PENDING) throw new IllegalStateException("not queued: " + status);
            status = Status.RUNNING;
        }
        fire(Status.RUNNING);
    }

    /** The run ended; {@code why} says what failed (no paths, no secrets). */
    public void finished(boolean ok, String why) {
        Status now = ok ? Status.DONE : Status.FAILED;
        synchronized (this) {
            if (status != Status.RUNNING) throw new IllegalStateException("not running: " + status);
            status = now;
            failure = ok ? null : (why == null ? "unknown" : why);
        }
        fire(now);
    }

    public synchronized Status status() {
        return status;
    }

    /** What the last failed run reported, or null. */
    public synchronized String failure() {
        return failure;
    }

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void fire(Status now) {
        for (Listener l : listeners) l.onOptionalSetup(now);
    }
}
