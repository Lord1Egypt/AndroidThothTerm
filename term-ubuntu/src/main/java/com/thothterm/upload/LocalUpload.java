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

package com.thothterm.upload;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.RequiresApi;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * "Upload files" and "Upload folder" on the phone: documents picked through
 * the Storage Access Framework, copied into the current directory of the
 * terminal window that was in front when the action was chosen.
 * <p>
 * Only the documents the user picked are readable -- no storage permission is
 * involved -- and their names are as untrusted as a browser's. One upload runs
 * at a time; it outlives the activity (rotation, recreation) and is cancelled
 * by Exit.
 */
@RequiresApi(21)
public final class LocalUpload {
    /** Enough to show "12.4 MB of 80 MB", not a flood of redraws. */
    private static final long PROGRESS_INTERVAL_MS = 200;
    /** A folder with more entries than this is refused rather than listed forever. */
    static final int MAX_ENTRIES = 100_000;

    public enum Phase { PREPARING, CONFIRM, RUNNING, DONE, FAILED, CANCELLED }

    public interface Listener {
        void onUploadChanged(LocalUpload upload);
    }

    /** One document to copy, and where it goes relative to the upload. */
    static final class Item {
        final Uri uri;
        final List<String> path;
        final boolean directory;
        final long size;

        Item(Uri uri, List<String> path, boolean directory, long size) {
            this.uri = uri;
            this.path = path;
            this.directory = directory;
            this.size = size;
        }
    }

    private static LocalUpload current;
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private final Context context;
    private final boolean folder;
    private volatile Phase phase = Phase.PREPARING;
    private volatile UploadTarget target;
    private volatile UploadError.Code error;
    private volatile String badName;
    private volatile String folderName;
    private volatile String resultName;
    private volatile int itemCount;
    private volatile int filesDone;
    private volatile long totalBytes = -1;
    private volatile long bytesDone;
    private volatile String currentName;
    private volatile boolean acknowledged;
    private List<Item> items = Collections.emptyList();
    private UploadBatch batch;
    private boolean cancelRequested;
    private long lastReport;

    private LocalUpload(Context context, boolean folder) {
        this.context = context.getApplicationContext();
        this.folder = folder;
    }

    /** The upload of this process, running or waiting to be acknowledged; null if none. */
    public static synchronized LocalUpload current() {
        return current;
    }

    public static synchronized void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** The shared record of staging directories; its first use removes a dead run's. */
    public static StagingJournal journal(Context context) {
        synchronized (LocalUpload.class) {
            if (sharedJournal == null) {
                sharedJournal = new StagingJournal(
                        new File(context.getApplicationContext().getFilesDir(), "upload-staging"));
                int removed = sharedJournal.sweep(new AndroidUploadFs());
                if (removed > 0) {
                    ThothLog.i(LogCategory.STORAGE, "Removed unfinished uploads from a previous run count=" + removed);
                }
            }
            return sharedJournal;
        }
    }

    private static StagingJournal sharedJournal;

    /**
     * Start preparing: fix the target from the session's current directory
     * now, then list the picked documents in the background. Refused (null)
     * while another upload is running.
     *
     * @param sources the picked documents, or the one picked tree for a folder
     */
    public static synchronized LocalUpload prepare(Context context, boolean folder, List<Uri> sources,
                                                   SessionDirectory.View view, int leaderPid,
                                                   boolean leaderIsShell) {
        if (current != null && !current.isSettled()) return null;
        LocalUpload upload = new LocalUpload(context, folder);
        current = upload;
        Thread worker = new Thread(() -> upload.prepareInBackground(sources, view, leaderPid, leaderIsShell),
                "Upload");
        worker.setDaemon(true);
        worker.start();
        return upload;
    }

    private void prepareInBackground(List<Uri> sources, SessionDirectory.View view, int leaderPid,
                                     boolean leaderIsShell) {
        try {
            target = SessionDirectory.resolve(ProcFiles.SYSTEM, view, leaderPid, leaderIsShell);
            List<Item> listed = folder ? listTree(sources.get(0)) : listDocuments(sources);
            long total = 0;
            int files = 0;
            for (Item item : listed) {
                if (item.directory) continue;
                ++files;
                total = item.size < 0 || total < 0 ? -1 : total + item.size;
            }
            synchronized (this) {
                items = listed;
                itemCount = files;
                totalBytes = total;
                if (cancelRequested) {
                    phase = Phase.CANCELLED;
                } else {
                    phase = Phase.CONFIRM;
                }
            }
        } catch (UploadError e) {
            fail(e);
            return;
        } catch (RuntimeException e) {
            // A provider that throws (revoked access, bad cursor).
            fail(new UploadError(UploadError.Code.IO, "listing failed", e));
            return;
        }
        notifyListeners();
    }

    /** The user confirmed the target: copy. */
    public void start() {
        synchronized (this) {
            if (phase != Phase.CONFIRM) return;
            phase = Phase.RUNNING;
        }
        notifyListeners();
        Thread worker = new Thread(this::run, "Upload");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        long started = System.currentTimeMillis();
        ThothLog.i(LogCategory.STORAGE, "Upload started kind=" + (folder ? "folder" : "files")
                + " files=" + itemCount + " bytes=" + totalBytes);
        StagingJournal journal = journal(context);
        ContentResolver resolver = context.getContentResolver();
        try {
            UploadBatch b = UploadBatch.begin(new AndroidUploadFs(), journal, target,
                    folder ? UploadBatch.Kind.FOLDER : UploadBatch.Kind.FILES, folderName, totalBytes);
            synchronized (this) {
                batch = b;
                if (cancelRequested) b.cancel();
            }
            for (Item item : items) {
                if (item.directory) {
                    b.makeDirectory(item.path);
                    continue;
                }
                currentName = item.path.get(item.path.size() - 1);
                report(true);
                InputStream in;
                try {
                    in = resolver.openInputStream(item.uri);
                } catch (IOException | SecurityException e) {
                    throw new UploadError(UploadError.Code.IO, "cannot open document", e);
                }
                if (in == null) throw new UploadError(UploadError.Code.IO, "no document stream");
                final long before = bytesDone;
                try {
                    b.receive(item.path, item.size, in, (itemBytes, batchBytes) -> {
                        bytesDone = before + itemBytes;
                        report(false);
                    });
                } finally {
                    try {
                        in.close();
                    } catch (IOException ignore) {
                        // Read to the end already, or abandoned.
                    }
                }
                ++filesDone;
            }
            resultName = b.finish();
            phase = Phase.DONE;
            ThothLog.i(LogCategory.STORAGE, "Upload finished files=" + filesDone + " bytes=" + bytesDone
                    + " ms=" + (System.currentTimeMillis() - started));
        } catch (UploadError e) {
            UploadBatch b;
            synchronized (this) {
                b = batch;
            }
            if (b != null) b.cancel();
            if (e.code == UploadError.Code.CANCELLED || cancelRequested) {
                phase = Phase.CANCELLED;
                ThothLog.i(LogCategory.STORAGE, "Upload cancelled bytes=" + bytesDone);
            } else {
                error = e.code;
                phase = Phase.FAILED;
                ThothLog.w(LogCategory.STORAGE, "Upload failed code=" + e.code.wire() + " bytes=" + bytesDone);
            }
        } catch (RuntimeException e) {
            UploadBatch b;
            synchronized (this) {
                b = batch;
            }
            if (b != null) b.cancel();
            error = UploadError.Code.IO;
            phase = Phase.FAILED;
            ThothLog.e(LogCategory.STORAGE, "Upload failed unexpectedly", e);
        }
        notifyListeners();
    }

    /** Stop now: the file in progress and the staging directory are removed. */
    public void cancel() {
        UploadBatch b;
        boolean settleNow = false;
        synchronized (this) {
            cancelRequested = true;
            b = batch;
            if (phase == Phase.CONFIRM) {
                phase = Phase.CANCELLED;
                settleNow = true;
            }
        }
        if (b != null) b.cancel();
        if (settleNow) notifyListeners();
    }

    /** Cancel whatever upload this process is running (Exit). */
    public static void cancelCurrent() {
        LocalUpload upload = current();
        if (upload != null) upload.cancel();
    }

    /** The user has seen the outcome; the next upload may start. */
    public void acknowledge() {
        synchronized (LocalUpload.class) {
            acknowledged = true;
            if (current == this && isSettled()) current = null;
        }
    }

    public boolean isSettled() {
        Phase p = phase;
        return p == Phase.DONE || p == Phase.FAILED || p == Phase.CANCELLED;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public boolean isFolder() {
        return folder;
    }

    public Phase phase() {
        return phase;
    }

    public UploadTarget target() {
        return target;
    }

    public UploadError.Code error() {
        return error;
    }

    /** The name that was refused, for a BAD_NAME error while listing. */
    public String badName() {
        return badName;
    }

    public String folderName() {
        return folderName;
    }

    /** The uploaded folder's final name, e.g. "project (1)". */
    public String resultName() {
        return resultName;
    }

    public int itemCount() {
        return itemCount;
    }

    public int filesDone() {
        return filesDone;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long bytesDone() {
        return bytesDone;
    }

    public String currentName() {
        return currentName;
    }

    private void fail(UploadError e) {
        synchronized (this) {
            if (cancelRequested) {
                phase = Phase.CANCELLED;
            } else {
                error = e.code;
                phase = Phase.FAILED;
            }
        }
        ThothLog.w(LogCategory.STORAGE, "Upload not started code=" + e.code.wire());
        notifyListeners();
    }

    private void report(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastReport < PROGRESS_INTERVAL_MS) return;
        lastReport = now;
        notifyListeners();
    }

    private void notifyListeners() {
        main.post(() -> {
            List<Listener> copy;
            synchronized (LocalUpload.class) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) l.onUploadChanged(this);
        });
    }

    // ------------------------------------------------------------ listing

    private List<Item> listDocuments(List<Uri> uris) throws UploadError {
        List<Item> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        for (Uri uri : uris) {
            String name = null;
            long size = -1;
            try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    name = c.isNull(0) ? null : c.getString(0);
                    size = c.isNull(1) ? -1 : c.getLong(1);
                }
            }
            result.add(new Item(uri, Collections.singletonList(checked(name)), false, size));
        }
        return result;
    }

    private List<Item> listTree(Uri tree) throws UploadError {
        ContentResolver resolver = context.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        String rootName = null;
        try (Cursor c = resolver.query(rootUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) rootName = c.getString(0);
        }
        folderName = checked(rootName);

        List<Item> result = new ArrayList<>();
        Deque<Object[]> pending = new ArrayDeque<>();
        pending.add(new Object[]{rootId, Collections.<String>emptyList()});
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
        };
        while (!pending.isEmpty()) {
            if (cancelRequested) throw new UploadError(UploadError.Code.CANCELLED, "cancelled");
            Object[] next = pending.removeFirst();
            @SuppressWarnings("unchecked")
            List<String> parent = (List<String>) next[1];
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, (String) next[0]);
            try (Cursor c = resolver.query(children, projection, null, null, null)) {
                if (c == null) continue;
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String name = checked(c.isNull(1) ? null : c.getString(1));
                    boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2));
                    long size = c.isNull(3) ? -1 : c.getLong(3);
                    List<String> path = new ArrayList<>(parent);
                    path.add(name);
                    if (path.size() > UploadNames.MAX_DEPTH) {
                        throw new UploadError(UploadError.Code.BAD_NAME, "folder too deep");
                    }
                    result.add(new Item(DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            Collections.unmodifiableList(path), directory, directory ? 0 : size));
                    if (result.size() > MAX_ENTRIES) {
                        throw new UploadError(UploadError.Code.BAD_NAME, "too many entries");
                    }
                    if (directory) pending.add(new Object[]{id, path});
                }
            }
        }
        return result;
    }

    private String checked(String name) throws UploadError {
        try {
            return UploadNames.checkName(name);
        } catch (UploadError e) {
            badName = name;
            throw e;
        }
    }
}
