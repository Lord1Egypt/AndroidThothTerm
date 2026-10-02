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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@link FileOps} on Android, through the system calls themselves
 * ({@code android.system.Os}) so the no-follow guarantees are explicit:
 * {@code lstat}, {@code open(O_CREAT|O_EXCL|O_NOFOLLOW)}, {@code fchmod} on the
 * open descriptor, {@code unlink}, {@code link}, {@code symlink},
 * {@code rename}. setuid and setgid bits from an archive are never applied:
 * in a rootless app-private installation they are meaningless and only add
 * risk. A directory keeps its sticky bit ({@code /tmp} is 1777).
 */
public final class AndroidFileOps implements FileOps {
    /** O_CLOEXEC exists in OsConstants from API 27; the app's minimum is 26. */
    private static final int CLOEXEC =
            Build.VERSION.SDK_INT >= 27 ? OsConstants.O_CLOEXEC : 0;

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
        final int finalMode = mode & FILE_MODE_MASK;
        return new FileOutputStream(fd) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                IOException failure = null;
                try {
                    // The descriptor, not the path: the mode lands on exactly
                    // the inode this stream created.
                    Os.fchmod(fd, finalMode);
                } catch (ErrnoException e) {
                    failure = io("fchmod", file, e);
                }
                super.close();
                if (failure != null) throw failure;
            }
        };
    }

    @Override
    public InputStream openNoFollow(File file) throws IOException {
        try {
            FileDescriptor fd = Os.open(file.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | CLOEXEC, 0);
            return new FileInputStream(fd) {
                private boolean closed;

                @Override
                public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    super.close();
                }
            };
        } catch (ErrnoException e) {
            throw io("open", file, e);
        }
    }

    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        String path = file.getAbsolutePath();
        FileDescriptor fd;
        try {
            // O_NOFOLLOW: a symlink fails with ELOOP instead of being followed.
            fd = Os.open(path, OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | CLOEXEC, 0);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EACCES) throw io("chmod", file, e);
            // An owner-unreadable file (mode 0200, 0000) cannot be opened
            // read-only. Without a race the path is still what lstat says; the
            // installation tree has a single writer, this thread.
            Type type = type(file);
            if (type != Type.REGULAR && type != Type.DIRECTORY) {
                throw new IOException("Refusing to chmod " + type + ": " + file);
            }
            try {
                Os.chmod(path, mode & (type == Type.DIRECTORY ? DIRECTORY_MODE_MASK : FILE_MODE_MASK));
            } catch (ErrnoException chmodError) {
                throw io("chmod", file, chmodError);
            }
            return;
        }
        try {
            int kind = Os.fstat(fd).st_mode;
            if (!OsConstants.S_ISREG(kind) && !OsConstants.S_ISDIR(kind)) {
                throw new IOException("Refusing to chmod a special file: " + file);
            }
            Os.fchmod(fd, mode & (OsConstants.S_ISDIR(kind) ? DIRECTORY_MODE_MASK : FILE_MODE_MASK));
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

    @Override
    public void unlink(File file) throws IOException {
        try {
            Os.unlink(file.getAbsolutePath());
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
