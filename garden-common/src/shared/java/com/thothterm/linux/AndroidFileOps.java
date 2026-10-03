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

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@link FileOps} on Android, through the system calls themselves
 * ({@code android.system.Os}, public SDK API only -- see
 * {@code PublicSdkApiTest}) so the no-follow guarantees are explicit:
 * {@code lstat}, {@code open(O_CREAT|O_EXCL|O_NOFOLLOW)}, {@code fchmod} on the
 * open descriptor, {@code remove} (unlink), {@code link}, {@code symlink},
 * {@code rename}. setuid and setgid bits from an archive are never applied:
 * in a rootless app-private installation they are meaningless and only add
 * risk. A directory keeps its sticky bit ({@code /tmp} is 1777).
 */
public final class AndroidFileOps implements FileOps {
    /** O_CLOEXEC exists in OsConstants from API 27; the app's minimum is 26. */
    private static final int CLOEXEC =
            Build.VERSION.SDK_INT >= 27 ? OsConstants.O_CLOEXEC : 0;
    /**
     * Linux O_PATH (2.6.39+). OsConstants has no public O_PATH; the value is
     * the generic one, which arm64, arm and x86 all use.
     */
    private static final int O_PATH = 010000000;

    @Override
    public Type type(File file) throws IOException {
        StructStat st;
        try {
            st = Os.lstat(file.getAbsolutePath());
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.ENOENT) return Type.NONE;
            throw io("lstat", file, e);
        }
        int mode = st.st_mode;
        if (OsConstants.S_ISLNK(mode)) return Type.SYMLINK;
        if (OsConstants.S_ISDIR(mode)) return Type.DIRECTORY;
        if (OsConstants.S_ISREG(mode)) return Type.REGULAR;
        return Type.OTHER;
    }

    @Override
    public int permissions(File file) throws IOException {
        try {
            return Os.lstat(file.getAbsolutePath()).st_mode & 07777;
        } catch (ErrnoException e) {
            throw io("lstat", file, e);
        }
    }

    @Override
    public void mkdir(File dir, int mode) throws IOException {
        try {
            Os.mkdir(dir.getAbsolutePath(), 0700);
        } catch (ErrnoException e) {
            throw io("mkdir", dir, e);
        }
        chmodNoFollow(dir, mode);
    }

    @Override
    public OutputStream createNew(File file, int mode) throws IOException {
        final FileDescriptor fd;
        try {
            fd = Os.open(file.getAbsolutePath(),
                    OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL
                            | OsConstants.O_NOFOLLOW | CLOEXEC, 0600);
        } catch (ErrnoException e) {
            throw io("create", file, e);
        }
        return new DescriptorOutputStream(fd, file, mode & FILE_MODE_MASK);
    }

    @Override
    public InputStream openNoFollow(File file) throws IOException {
        try {
            return new DescriptorInputStream(Os.open(file.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | CLOEXEC, 0), file);
        } catch (ErrnoException e) {
            throw io("open", file, e);
        }
    }

    /**
     * Writes straight to the descriptor with {@code write(2)}. Android's
     * {@code FileOutputStream(FileDescriptor)} does not own the descriptor and
     * would never close it: one leaked descriptor per extracted file. Closing
     * applies the mode with {@code fchmod} on this descriptor, then closes it.
     */
    private static final class DescriptorOutputStream extends OutputStream {
        private final FileDescriptor fd;
        private final File file;
        private final int mode;
        private boolean closed;

        DescriptorOutputStream(FileDescriptor fd, File file, int mode) {
            this.fd = fd;
            this.file = file;
            this.mode = mode;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (closed) throw new IOException("Stream closed: " + file);
            while (length > 0) {
                int written;
                try {
                    written = Os.write(fd, buffer, offset, length);
                } catch (ErrnoException e) {
                    throw io("write", file, e);
                }
                offset += written;
                length -= written;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            IOException failure = null;
            try {
                // The descriptor, not the path: the mode lands on exactly the
                // inode this stream created.
                Os.fchmod(fd, mode);
            } catch (ErrnoException e) {
                failure = io("fchmod", file, e);
            }
            try {
                Os.close(fd);
            } catch (ErrnoException e) {
                if (failure == null) failure = io("close", file, e);
            }
            if (failure != null) throw failure;
        }
    }

    /** Reads with {@code read(2)} and closes the descriptor it owns. */
    private static final class DescriptorInputStream extends InputStream {
        private final FileDescriptor fd;
        private final File file;
        private boolean closed;

        DescriptorInputStream(FileDescriptor fd, File file) {
            this.fd = fd;
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (closed) throw new IOException("Stream closed: " + file);
            if (length == 0) return 0;
            int n;
            try {
                n = Os.read(fd, buffer, offset, length);
            } catch (ErrnoException e) {
                throw io("read", file, e);
            }
            return n == 0 ? -1 : n;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                Os.close(fd);
            } catch (ErrnoException e) {
                throw io("close", file, e);
            }
        }
    }

    /**
     * One object, one descriptor, no second lookup. {@code O_PATH|O_NOFOLLOW}
     * binds the object at the path itself -- a symlink stays the symlink --
     * and, unlike {@code O_RDONLY}, needs no read permission, so 0200, 0110,
     * 0000 and 0300 behave like any other mode, and a FIFO is never opened
     * for I/O. The type check, the {@code fchmod} and the check of its result
     * are all on that descriptor: swapping the path for a symlink (or
     * anything else) after the open cannot redirect the change. Bionic
     * applies {@code fchmod} to an {@code O_PATH} descriptor through
     * {@code /proc/self/fd/N}, which names the descriptor's own inode; if it
     * could not, the call fails and nothing changes.
     */
    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        FileDescriptor fd;
        try {
            fd = Os.open(file.getAbsolutePath(), O_PATH | OsConstants.O_NOFOLLOW | CLOEXEC, 0);
        } catch (ErrnoException e) {
            throw io("chmod", file, e);
        }
        try {
            int kind = Os.fstat(fd).st_mode;
            if (!OsConstants.S_ISREG(kind) && !OsConstants.S_ISDIR(kind)) {
                throw new IOException("Refusing to chmod "
                        + (OsConstants.S_ISLNK(kind) ? "a symlink" : "a special file") + ": " + file);
            }
            int masked = mode & (OsConstants.S_ISDIR(kind) ? DIRECTORY_MODE_MASK : FILE_MODE_MASK);
            Os.fchmod(fd, masked);
            if ((Os.fstat(fd).st_mode & 07777) != masked) {
                throw new IOException("chmod did not take effect: " + file);
            }
        } catch (ErrnoException e) {
            throw io("fchmod", file, e);
        } finally {
            try {
                Os.close(fd);
            } catch (ErrnoException ignored) {
                // Nothing useful to do.
            }
        }
    }

    /**
     * The public SDK has no {@code Os.unlink}; {@code Os.remove} is
     * {@code remove(3)}, which in bionic is {@code unlink(2)} and falls back
     * to {@code rmdir(2)} only when unlink fails with EISDIR. unlink(2) never
     * follows a final symlink: a link, dangling or not, is removed itself and
     * its target is untouched. A directory is refused first, so this never
     * acts as rmdir; the installation tree has a single writer, this thread.
     */
    @Override
    public void unlink(File file) throws IOException {
        if (type(file) == Type.DIRECTORY) {
            throw new IOException("Is a directory: " + file);
        }
        try {
            Os.remove(file.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("unlink", file, e);
        }
    }

    @Override
    public void rmdir(File dir) throws IOException {
        if (type(dir) != Type.DIRECTORY) {
            throw new IOException("Not a directory: " + dir);
        }
        try {
            Os.remove(dir.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("rmdir", dir, e);
        }
    }

    @Override
    public void symlink(String target, File link) throws IOException {
        try {
            Os.symlink(target, link.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("symlink", link, e);
        }
    }

    @Override
    public String readlink(File link) throws IOException {
        try {
            return Os.readlink(link.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("readlink", link, e);
        }
    }

    @Override
    public void hardlink(File existing, File link) throws IOException {
        try {
            Os.link(existing.getAbsolutePath(), link.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("link", link, e);
        }
    }

    @Override
    public void rename(File from, File to) throws IOException {
        try {
            Os.rename(from.getAbsolutePath(), to.getAbsolutePath());
        } catch (ErrnoException e) {
            throw io("rename", from, e);
        }
    }

    @Override
    public void setLastModified(File file, long timeMillis) {
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(timeMillis);
    }

    @Override
    public String canonicalPath(File file) throws IOException {
        return file.getCanonicalPath();
    }

    private static IOException io(String op, File file, ErrnoException e) {
        return new IOException(op + " failed: " + file + ": " + e.getMessage(), e);
    }
}
