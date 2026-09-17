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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotating plain-text persistence in application-private storage. At most
 * {@link #MAX_FILES} files of {@link #MAX_FILE_BYTES} bytes are kept, so the
 * total on-disk footprint is bounded to about 16 MB. All methods are
 * synchronized; the only caller is the logging writer thread, except for
 * {@link #deleteAll()} which is also called from the UI thread on user request.
 */
final class LogFileStore {
    static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    static final int MAX_FILES = 4;

    private static final String BASE_NAME = "thotterm";
    private static final String ENCODING = "UTF-8";

    private final File directory;

    LogFileStore(File directory) {
        this.directory = directory;
    }

    File getDirectory() {
        return directory;
    }

    private File fileAt(int index) {
        String name = (index == 0) ? BASE_NAME + ".log"
                : BASE_NAME + "." + index + ".log";
        return new File(directory, name);
    }

    synchronized void appendBatch(List<LogEntry> batch) {
        if (batch.isEmpty()) return;
        try {
            if (!(directory.exists() || directory.mkdirs())) return;

            File current = fileAt(0);
            if (current.length() >= MAX_FILE_BYTES) rotate();

            StringBuilder text = new StringBuilder(batch.size() * 96);
            for (LogEntry entry : batch) text.append(entry.toLine());

            FileOutputStream out = new FileOutputStream(current, true);
            try {
                out.write(text.toString().getBytes(ENCODING));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void rotate() {
        File oldest = fileAt(MAX_FILES - 1);
        if (oldest.exists()) oldest.delete();

        for (int i = MAX_FILES - 2; i >= 1; i--) {
            File source = fileAt(i);
            if (source.exists()) source.renameTo(fileAt(i + 1));
        }
        fileAt(0).renameTo(fileAt(1));
    }

    synchronized void deleteAll() {
        for (int i = 0; i < MAX_FILES; i++) {
            File file = fileAt(i);
            if (file.exists()) file.delete();
        }
    }

    synchronized List<File> filesOldestFirst() {
        List<File> files = new ArrayList<>(MAX_FILES);
        for (int i = MAX_FILES - 1; i >= 0; i--) {
            File file = fileAt(i);
            if (file.exists() && file.length() > 0) files.add(file);
        }
        return files;
    }

    synchronized void awaitIdle() {
        // Synchronizing is sufficient: appendBatch holds this monitor.
    }
}
