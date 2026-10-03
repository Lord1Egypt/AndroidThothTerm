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
 * Severity of a log record. {@link #rank} is ordered so that a lower value is a
 * higher severity; a record is emitted when its rank is less than or equal to
 * the active threshold rank.
 */
public enum LogLevel {
    ERROR(0, "ERROR"),
    WARN(1, "WARN"),
    INFO(2, "INFO"),
    DEBUG(3, "DEBUG"),
    VERBOSE(4, "VERBOSE");

    public final int rank;
    public final String label;

    LogLevel(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public String paddedLabel() {
        StringBuilder b = new StringBuilder(label);
        while (b.length() < VERBOSE.label.length()) b.append(' ');
        return b.toString();
    }

    public static LogLevel fromName(String name) {
        if (name != null) {
            for (LogLevel level : values()) {
                if (level.label.equalsIgnoreCase(name)) return level;
            }
        }
        return INFO;
    }

    public static String[] names() {
        LogLevel[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].label;
        return names;
    }
}
