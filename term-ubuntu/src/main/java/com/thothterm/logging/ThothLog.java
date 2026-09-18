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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.thothterm.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central diagnostics and logging API for ThothTerm.
 *
 * <p>All application code should log through this class rather than
 * {@code android.util.Log}. Records are mirrored to Logcat, kept in a bounded
 * in-memory ring for the live viewer, and persisted asynchronously to rotating
 * files in application-private storage. Logging failures are swallowed and can
 * never crash the terminal.</p>
 *
 * <p>Privacy: callers must never pass terminal input, terminal output, command
 * text, environment values, clipboard data, credentials, or any other secret.
 * Logs describe system behaviour only.</p>
 */
public final class ThothLog {
    public static final String TAG = "ThothTerm";

    public static final String PREF_LEVEL = "diagnostics_log_level";
    public static final String PREF_DEVELOPER = "diagnostics_developer_logging";

    private static final int BUFFER_CAPACITY = 4000;
    private static final int QUEUE_CAPACITY = 4096;
    private static final int BATCH_SIZE = 256;
    private static final int MAX_MESSAGE_LENGTH = 2048;
    private static final String DEFAULT_LEVEL = LogLevel.INFO.label;

    private static volatile Manager sManager;

    private ThothLog() {
    }

    public static void init(Context context) {
        if (sManager != null) return;
        try {
            Manager manager = new Manager(context.getApplicationContext());
            manager.recomputeFilter();
            manager.start();
            sManager = manager;
        } catch (Throwable ignored) {
        }
    }

    public static boolean isEnabled(LogLevel level) {
        Manager manager = sManager;
        if (manager == null) return true;
        return level.rank <= manager.thresholdRank;
    }

    public static void onPreferenceChanged(String key) {
        Manager manager = sManager;
        if (manager == null) return;
        if (!PREF_LEVEL.equals(key) && !PREF_DEVELOPER.equals(key)) return;
        manager.recomputeFilter();
    }

    public static void v(String category, String message) {
        log(LogLevel.VERBOSE, category, message);
    }

    public static void d(String category, String message) {
        log(LogLevel.DEBUG, category, message);
    }

    public static void i(String category, String message) {
        log(LogLevel.INFO, category, message);
    }

    public static void w(String category, String message) {
        log(LogLevel.WARN, category, message);
    }

    public static void e(String category, String message) {
        log(LogLevel.ERROR, category, message);
    }

    public static void e(String category, String message, Throwable t) {
        log(LogLevel.ERROR, category, message + ": " + describe(t));
    }

    public static void log(LogLevel level, String category, String message) {
        try {
            Manager manager = sManager;
            if (manager != null && level.rank > manager.thresholdRank) return;

            String tag = normalizeCategory(category);
            String text = normalizeMessage(message);

            if (manager != null) {
                LogEntry entry = new LogEntry(System.currentTimeMillis(), level, tag, text);
                manager.buffer.add(entry);
                if (!manager.queue.offer(entry)) {
                    manager.dropped.incrementAndGet();
                }
            }
            logcat(level, tag, text);
        } catch (Throwable ignored) {
        }
    }

    public static List<LogEntry> snapshot() {
        Manager manager = sManager;
        if (manager == null) return Collections.emptyList();
        return manager.buffer.snapshot();
    }

    public static long getChangeCount() {
        Manager manager = sManager;
        return manager == null ? 0 : manager.buffer.changeCount();
    }

    public static int getDroppedCount() {
        Manager manager = sManager;
        return manager == null ? 0 : manager.dropped.get();
    }

    public static String getLevelName() {
        Manager manager = sManager;
        if (manager == null) return DEFAULT_LEVEL;
        return manager.selectedLevel.label;
    }

    public static boolean isDeveloperLoggingEnabled() {
        Manager manager = sManager;
        if (manager == null) return false;
        return manager.developerLogging;
    }

    public static void flush() {
        Manager manager = sManager;
        if (manager == null) return;
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (!manager.queue.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            manager.store.awaitIdle();
        } catch (Throwable ignored) {
        }
    }

    public static void clear() {
        Manager manager = sManager;
        if (manager == null) return;
        try {
            synchronized (manager.ioLock) {
                manager.generation.incrementAndGet();
                manager.queue.clear();
                manager.buffer.clear();
                manager.dropped.set(0);
                manager.store.deleteAll();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void exportTo(OutputStream out) throws Exception {
        Manager manager = sManager;
        flush();

        Writer writer = new OutputStreamWriter(out, "UTF-8");
        writeHeader(writer, manager);

        boolean wrote = false;
        if (manager != null) {
            for (File file : manager.store.filesOldestFirst()) {
                copyFile(file, writer);
                wrote = true;
            }
        }
        if (!wrote) {
            for (LogEntry entry : snapshot()) writer.write(entry.toLine());
        }
        writer.flush();
    }

    public static String suggestedExportName() {
        return "thothterm-logs-" + Formats.fileStamp(System.currentTimeMillis()) + ".txt";
    }

    private static void writeHeader(Writer writer, Manager manager) throws Exception {
        String flavor = BuildConfig.FLAVOR + "-" + BuildConfig.BUILD_TYPE;
        String abi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_ABIS;
            abi = (abis != null && abis.length > 0) ? abis[0] : "unknown";
        } else {
            abi = Build.CPU_ABI;
        }

        writer.write("ThothTerm diagnostics log\n");
        writer.write("----------------------------------------\n");
        writer.write("App version: " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")\n");
        writer.write("Android: " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n");
        writer.write("Device architecture: " + abi + "\n");
        writer.write("App flavor: " + flavor + "\n");
        writer.write("Log level: " + getLevelName()
                + " (developer logging: " + (isDeveloperLoggingEnabled() ? "on" : "off") + ")\n");
        writer.write("Exported: " + Formats.full(System.currentTimeMillis()) + "\n");
        writer.write("----------------------------------------\n\n");
    }

    private static void copyFile(File file, Writer writer) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                writer.write(buffer, 0, read);
            }
        } finally {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void logcat(LogLevel level, String category, String message) {
        String text = category + ": " + message;
        switch (level) {
            case ERROR:
                android.util.Log.e(TAG, text);
                break;
            case WARN:
                android.util.Log.w(TAG, text);
                break;
            case DEBUG:
                android.util.Log.d(TAG, text);
                break;
            case VERBOSE:
                android.util.Log.v(TAG, text);
                break;
            case INFO:
            default:
                android.util.Log.i(TAG, text);
                break;
        }
    }

    private static String normalizeCategory(String category) {
        if (TextUtils.isEmpty(category)) return LogCategory.APP;
        return category.trim().toUpperCase(java.util.Locale.US);
    }

    private static String normalizeMessage(String message) {
        if (message == null) return "(null)";
        StringBuilder builder = new StringBuilder(Math.min(message.length(), MAX_MESSAGE_LENGTH));
        for (int i = 0; i < message.length() && builder.length() < MAX_MESSAGE_LENGTH; i++) {
            char c = message.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                builder.append(' ');
            } else if (c < 0x20 || c == 0x7F) {
                builder.append(' ');
            } else {
                builder.append(c);
            }
        }
        String result = builder.toString().trim();
        return result.length() == 0 ? "(empty)" : result;
    }

    private static String describe(Throwable t) {
        if (t == null) return "unknown";
        String message = t.getMessage();
        String name = t.getClass().getSimpleName();
        return TextUtils.isEmpty(message) ? name : name + ": " + message;
    }

    private static final class Manager {
        final Context appContext;
        final LogBuffer buffer = new LogBuffer(BUFFER_CAPACITY);
        final LinkedBlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicInteger dropped = new AtomicInteger();
        final AtomicLong generation = new AtomicLong();
        final Object ioLock = new Object();
        final LogFileStore store;
        final ExecutorService writer;

        volatile int thresholdRank = LogLevel.INFO.rank;
        volatile LogLevel selectedLevel = LogLevel.INFO;
        volatile boolean developerLogging;

        Manager(Context appContext) {
            this.appContext = appContext;
            File dir = new File(appContext.getFilesDir(), "logs");
            this.store = new LogFileStore(dir);
            this.writer = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ThothLog-writer");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }

        void start() {
            try {
                writer.execute(new WriterLoop(this));
            } catch (Throwable ignored) {
            }
        }

        void recomputeFilter() {
            try {
                SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(appContext);
                String name = prefs.getString(PREF_LEVEL, DEFAULT_LEVEL);
                selectedLevel = LogLevel.fromName(name);
                developerLogging = prefs.getBoolean(PREF_DEVELOPER, false);
                thresholdRank = developerLogging
                        ? selectedLevel.rank
                        : Math.min(selectedLevel.rank, LogLevel.INFO.rank);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class WriterLoop implements Runnable {
        private final Manager manager;

        WriterLoop(Manager manager) {
            this.manager = manager;
        }

        @Override
        public void run() {
            List<LogEntry> batch = new ArrayList<>(BATCH_SIZE);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    LogEntry first = manager.queue.poll(1, TimeUnit.SECONDS);
                    if (first == null) continue;

                    long generation = manager.generation.get();

                    batch.clear();
                    batch.add(first);
                    manager.queue.drainTo(batch, BATCH_SIZE - 1);

                    synchronized (manager.ioLock) {
                        if (generation == manager.generation.get()) {
                            manager.store.appendBatch(batch);
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
