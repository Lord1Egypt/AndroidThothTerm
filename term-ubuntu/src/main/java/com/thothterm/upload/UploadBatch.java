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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;

/**
 * One upload -- some files, or one folder -- into a directory fixed when it
 * began.
 * <p>
 * Nothing appears under a final name until it is complete. Everything is
 * written into a hidden staging directory created inside the target, so it
 * lives on the target's file system, and journalled first so an app that dies
 * mid-upload removes it on its next start. A finished file, or the whole
 * finished folder, is then moved into place with a rename that never replaces
 * anything: if the name is taken, the next "name (n)" is tried, so both are
 * kept. Uploads never merge into an existing directory and never follow a
 * link that is already there.
 * <p>
 * Data is streamed through a fixed buffer, never held whole. Any failure, and
 * {@link #cancel()} from any thread, abandons the batch and removes its
 * staging directory; files already moved into place stay, complete.
 */
public final class UploadBatch {
    public enum Kind { FILES, FOLDER }

    /** Bytes of the item in progress, and of the whole batch so far. */
    public interface Progress {
        void update(long itemBytes, long batchBytes);
    }

    /** Kept free beyond an upload's own size, so the device is not filled to the last block. */
    static final long RESERVE_BYTES = 16L * 1024 * 1024;
    static final int BUFFER_BYTES = 64 * 1024;
    /** "name (1)" ... "name (9999)", then CONFLICT. */
    static final int MAX_CANDIDATES = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private enum State { OPEN, FINISHED, ABANDONED }

    private final UploadFs fs;
    private final StagingJournal journal;
    private final UploadTarget target;
    private final Kind kind;
    private final String folderName;
    private final String staging;
    private final String folderRoot;

    private State state = State.OPEN;
    private boolean receiving;
    private boolean cleaned;
    private Closeable source;
    private int sequence;
    private long batchBytes;

    private UploadBatch(UploadFs fs, StagingJournal journal, UploadTarget target, Kind kind,
                        String folderName, String staging) {
        this.fs = fs;
        this.journal = journal;
        this.target = target;
        this.kind = kind;
        this.folderName = folderName;
        this.staging = staging;
        this.folderRoot = kind == Kind.FOLDER ? staging + "/" + folderName : null;
    }

    /**
     * Check the target and create the staging directory.
     *
     * @param folderName    the uploaded folder's own name (FOLDER only)
     * @param expectedBytes the total size, or -1 when unknown
     */
    public static UploadBatch begin(UploadFs fs, StagingJournal journal, UploadTarget target,
                                    Kind kind, String folderName, long expectedBytes)
            throws UploadError {
        if (kind == Kind.FOLDER) UploadNames.checkName(folderName);
        try {
            if (fs.lstat(target.hostPath) != UploadFs.Kind.DIRECTORY) {
                throw new UploadError(UploadError.Code.DIRECTORY_GONE, "target is not a directory");
            }
            if (!fs.writableDirectory(target.hostPath)) {
                throw new UploadError(UploadError.Code.NOT_WRITABLE, "target not writable");
            }
            if (expectedBytes >= 0 && fs.freeBytes(target.hostPath) - RESERVE_BYTES < expectedBytes) {
                throw new UploadError(UploadError.Code.NO_SPACE, "not enough space");
            }
        } catch (UploadError e) {
            throw e;
        } catch (IOException e) {
            throw map(e);
        }

        byte[] raw = new byte[8];
        RANDOM.nextBytes(raw);
        StringBuilder name = new StringBuilder(UploadNames.STAGING_PREFIX);
        for (byte b : raw) name.append(String.format("%02x", b & 0xff));
        String staging = child(target.hostPath, name.toString());

        try {
            journal.add(staging);
        } catch (IOException e) {
            throw new UploadError(UploadError.Code.IO, "cannot record staging", e);
        }
        UploadBatch batch = new UploadBatch(fs, journal, target, kind, folderName, staging);
        try {
            fs.mkdir(staging, 0700);
        } catch (IOException e) {
            journal.remove(staging);
            throw map(e);
        }
        if (kind == Kind.FOLDER) {
            try {
                fs.mkdir(batch.folderRoot, target.directoryMode());
            } catch (IOException e) {
                batch.cancel();
                throw map(e);
            }
        }
        return batch;
    }

    public UploadTarget target() {
        return target;
    }

    public Kind kind() {
        return kind;
    }

    public synchronized long batchBytes() {
        return batchBytes;
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN;
    }

    /** NO_SPACE unless {@code bytes} more fit in the target, beyond the reserve. */
    public void ensureSpace(long bytes) throws UploadError {
        try {
            if (fs.freeBytes(target.hostPath) - RESERVE_BYTES < bytes) {
                throw new UploadError(UploadError.Code.NO_SPACE, "not enough space");
            }
        } catch (UploadError e) {
            throw e;
        } catch (IOException e) {
            throw map(e);
        }
    }

    /**
     * Stream one file in. For FILES, {@code path} is its single name and the
     * returned name is the one it was given in the target ("a (1).txt" if
     * "a.txt" was taken). For FOLDER, {@code path} is relative to the folder
     * and missing parent directories are created.
     *
     * @param size the announced size, or -1; a different byte count fails
     */
    public String receive(List<String> path, long size, InputStream in, Progress progress)
            throws UploadError {
        checkPath(path);
        enter(in);
        String part = null;
        UploadFs.Sink sink = null;
        boolean ok = false;
        try {
            if (kind == Kind.FILES) {
                synchronized (this) {
                    part = staging + "/" + (++sequence) + ".part";
                }
            } else {
                makeParents(path, path.size() - 1);
                part = folderRoot + "/" + join(path, path.size());
                if (fs.lstat(part) != UploadFs.Kind.MISSING) {
                    throw new UploadError(UploadError.Code.BAD_NAME, "duplicate path");
                }
            }
            sink = fs.create(part, target.fileMode());
            byte[] buffer = new byte[BUFFER_BYTES];
            long done = 0;
            while (true) {
                checkOpen();
                int n;
                try {
                    n = in.read(buffer);
                } catch (IOException e) {
                    checkOpen();
                    throw new UploadError(UploadError.Code.IO, "source failed", e);
                }
                if (n < 0) break;
                if (n == 0) continue;
                if (size >= 0 && done + n > size) {
                    throw new UploadError(UploadError.Code.IO, "more data than announced");
                }
                sink.write(buffer, 0, n);
                done += n;
                long total;
                synchronized (this) {
                    batchBytes += n;
                    total = batchBytes;
                }
                if (progress != null) progress.update(done, total);
            }
            if (size >= 0 && done != size) throw new UploadError(UploadError.Code.IO, "truncated");
            checkOpen();
            sink.finish();
            sink = null;
            String result;
            if (kind == Kind.FILES) {
                result = publish(part, path.get(0), false);
                part = null;
            } else {
                result = join(path, path.size());
            }
            ok = true;
            return result;
        } catch (UploadError e) {
            throw e;
        } catch (IOException e) {
            throw map(e);
        } finally {
            if (sink != null) sink.abort();
            if (!ok && part != null) deleteQuietly(part);
            leave(ok);
        }
    }

    /** Create a directory of the folder, with its parents (FOLDER only); an empty one survives. */
    public void makeDirectory(List<String> path) throws UploadError {
        checkPath(path);
        enter(null);
        boolean ok = false;
        try {
            makeParents(path, path.size());
            ok = true;
        } finally {
            leave(ok);
        }
    }

    /**
     * Move the folder into place (FOLDER) and remove the staging directory.
     * Returns the folder's final name, or null for FILES.
     */
    public String finish() throws UploadError {
        synchronized (this) {
            if (state != State.OPEN || receiving) {
                throw new UploadError(UploadError.Code.CANCELLED, "not open");
            }
            receiving = true;
        }
        String name = null;
        boolean ok = false;
        try {
            if (kind == Kind.FOLDER) name = publish(folderRoot, folderName, true);
            ok = true;
        } finally {
            synchronized (this) {
                receiving = false;
                state = ok ? State.FINISHED : State.ABANDONED;
            }
            cleanUp();
        }
        return name;
    }

    /**
     * Abandon the batch: the transfer in progress stops at its next read (its
     * source is closed now), and the staging directory is removed. Idempotent
     * and callable from any thread.
     */
    public void cancel() {
        Closeable toClose;
        synchronized (this) {
            if (state != State.OPEN) return;
            state = State.ABANDONED;
            toClose = source;
        }
        if (toClose != null) {
            try {
                toClose.close();
            } catch (IOException ignore) {
                // Closing is only to unblock a read.
            }
        }
        cleanUpIfIdle();
    }

    private void checkPath(List<String> path) throws UploadError {
        if (path == null || path.isEmpty() || (kind == Kind.FILES && path.size() != 1)) {
            throw new UploadError(UploadError.Code.BAD_NAME, "wrong path shape");
        }
        for (String part : path) UploadNames.checkName(part);
    }

    private synchronized void enter(Closeable in) throws UploadError {
        if (state != State.OPEN) throw new UploadError(UploadError.Code.CANCELLED, "abandoned");
        if (receiving) throw new UploadError(UploadError.Code.BUSY, "one file at a time");
        receiving = true;
        source = in;
    }

    private void leave(boolean ok) {
        synchronized (this) {
            receiving = false;
            source = null;
            if (!ok) state = State.ABANDONED;
        }
        cleanUpIfIdle();
    }

    private synchronized void checkOpen() throws UploadError {
        if (state != State.OPEN) throw new UploadError(UploadError.Code.CANCELLED, "abandoned");
    }

    private void cleanUpIfIdle() {
        synchronized (this) {
            if (state == State.OPEN || receiving) return;
        }
        cleanUp();
    }

    private void cleanUp() {
        synchronized (this) {
            if (cleaned) return;
            cleaned = true;
        }
        try {
            if (fs.lstat(staging) != UploadFs.Kind.MISSING) Trees.delete(fs, staging);
            journal.remove(staging);
        } catch (IOException ignore) {
            // Left in the journal: the next start removes it.
        }
    }

    /** Create path[0 .. count-1] under the folder root, each a directory. */
    private void makeParents(List<String> path, int count) throws UploadError {
        try {
            for (int i = 1; i <= count; ++i) {
                String dir = folderRoot + "/" + join(path, i);
                switch (fs.lstat(dir)) {
                    case MISSING:
                        fs.mkdir(dir, target.directoryMode());
                        break;
                    case DIRECTORY:
                        break;
                    default:
                        throw new UploadError(UploadError.Code.BAD_NAME, "a file and a folder share a name");
                }
            }
        } catch (UploadError e) {
            throw e;
        } catch (IOException e) {
            throw map(e);
        }
    }

    /** Rename {@code from} into the target under the first free candidate of {@code name}. */
    private String publish(String from, String name, boolean directory) throws UploadError {
        for (int n = 0; n < MAX_CANDIDATES; ++n) {
            checkOpen();
            String candidate = UploadNames.candidate(name, n, directory);
            int errno = fs.renameNoReplace(from, child(target.hostPath, candidate));
            if (errno == 0) return candidate;
            if (errno == UploadFs.EEXIST || errno == UploadFs.ENOTEMPTY) continue;
            throw map(new UploadFs.Failure(errno, "rename failed"));
        }
        throw new UploadError(UploadError.Code.CONFLICT, "no free name");
    }

    private void deleteQuietly(String path) {
        try {
            fs.delete(path);
        } catch (IOException ignore) {
            // The staging clean-up removes it.
        }
    }

    static UploadError map(IOException e) {
        if (e instanceof UploadError) return (UploadError) e;
        if (e instanceof UploadFs.Failure) {
            int errno = ((UploadFs.Failure) e).errno;
            if (errno == UploadFs.ENOSPC || errno == UploadFs.EDQUOT) {
                return new UploadError(UploadError.Code.NO_SPACE, "storage full", e);
            }
            // EACCES, EPERM, EROFS
            if (errno == 13 || errno == 1 || errno == 30) {
                return new UploadError(UploadError.Code.NOT_WRITABLE, "not writable", e);
            }
            // ENOENT, ENOTDIR: the target went away under us.
            if (errno == 2 || errno == 20) {
                return new UploadError(UploadError.Code.DIRECTORY_GONE, "target removed", e);
            }
        }
        return new UploadError(UploadError.Code.IO, "write failed", e);
    }

    private static String join(List<String> path, int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            if (i > 0) b.append('/');
            b.append(path.get(i));
        }
        return b.toString();
    }

    static String child(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }
}
