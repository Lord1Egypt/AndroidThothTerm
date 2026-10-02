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

import java.util.ArrayList;
import java.util.List;

/**
 * Wall-clock timings of the first-run stages, for the log: where a slow or
 * stuck setup spent its time on a device nobody can attach a debugger to.
 * Thread-safe; times are milliseconds since {@link #start}.
 */
public final class SetupTimeline {
    /** The stages, in the order they normally happen. */
    public enum Stage {
        DOWNLOAD_COMPLETE,
        ARCHIVE_EXTRACTED,
        ARCHIVE_VERIFIED,
        SETUP_ROOTFS_COMPLETE,
        ROOTFS_PROMOTED,
        KEYRING_PROVISIONED,
        STATE_WRITTEN,
        /** The core is complete: the terminal may open ({@link SetupState#TERMINAL_READY}). */
        TERMINAL_READY,
        /** The setup screen was told to open the terminal. */
        TERMINAL_HANDOFF,
        /** Optional provisioning (sudo, keyring), after the handoff, in the background. */
        OPTIONAL_SETUP_STARTED,
        OPTIONAL_SETUP_FINISHED,
        OPTIONAL_SETUP_FAILED
    }

    private final long origin;
    private final List<String> marks = new ArrayList<>();

    private SetupTimeline(long origin) {
        this.origin = origin;
    }

    public static SetupTimeline start() {
        return new SetupTimeline(System.nanoTime());
    }

    /** Records a stage and returns the log line for it. */
    public synchronized String mark(Stage stage, String detail) {
        long ms = (System.nanoTime() - origin) / 1_000_000L;
        marks.add(stage.name() + "=" + ms);
        return line(stage, "t=" + ms + "ms", detail);
    }

    /**
     * The log line for a stage outside a first-run setup (optional
     * provisioning queued by a later session), where there is no origin.
     */
    public static String unscheduled(Stage stage, String detail) {
        return line(stage, "t=-", detail);
    }

    private static String line(Stage stage, String time, String detail) {
        return "Setup stage " + stage.name() + " " + time
                + (detail == null || detail.isEmpty() ? "" : " " + detail);
    }

    public synchronized String summary() {
        return "Setup timeline " + String.join(" ", marks);
    }
}
