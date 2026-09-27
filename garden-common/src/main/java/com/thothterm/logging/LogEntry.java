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

/**
 * One immutable log record. All user-visible fields are plain system
 * descriptions; terminal input, output, and secrets are never placed here.
 */
public final class LogEntry {
    public final long timestamp;
    public final LogLevel level;
    public final String category;
    public final String message;

    private final String timeText;

    LogEntry(long timestamp, LogLevel level, String category, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.category = category;
        this.message = message;
        this.timeText = Formats.time(timestamp);
    }

    public String timeText() {
        return timeText;
    }

    String toLine() {
        return Formats.full(timestamp)
                + "  " + level.paddedLabel()
                + "  " + category
                + "  " + message
                + "\n";
    }
}
