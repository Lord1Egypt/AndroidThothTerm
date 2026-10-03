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

package com.thothterm.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded in-memory ring buffer of the most recent log records. Appends are
 * cheap and never allocate per-call beyond the record itself; the oldest record
 * is overwritten once capacity is reached.
 */
final class LogBuffer {
    private final LogEntry[] ring;
    private int head;
    private int size;
    private long changeCount;

    LogBuffer(int capacity) {
        this.ring = new LogEntry[Math.max(1, capacity)];
    }

    synchronized void add(LogEntry entry) {
        ring[head] = entry;
        head = (head + 1) % ring.length;
        if (size < ring.length) size++;
        changeCount++;
    }

    synchronized void clear() {
        for (int i = 0; i < ring.length; i++) ring[i] = null;
        head = 0;
        size = 0;
        changeCount++;
    }

    synchronized int size() {
        return size;
    }

    synchronized long changeCount() {
        return changeCount;
    }

    synchronized List<LogEntry> snapshot() {
        if (size == 0) return Collections.emptyList();
        List<LogEntry> result = new ArrayList<>(size);
        int start = (head - size + ring.length) % ring.length;
        for (int i = 0; i < size; i++) {
            result.add(ring[(start + i) % ring.length]);
        }
        return result;
    }
}
