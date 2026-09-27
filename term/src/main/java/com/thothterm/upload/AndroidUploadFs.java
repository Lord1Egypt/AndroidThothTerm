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

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.system.StructStatVfs;

import androidx.annotation.RequiresApi;

import com.thothterm.Process;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * {@link UploadFs} on the kernel's calls, as the app's uid. Access checks are
 * the kernel's own, the same a shell running as this uid gets -- and, under
 * PRoot, the same the guest user gets, because this PRoot build emulates no
 * file ownership of its own (see docs/garden/UPLOADS.md).
 */
@RequiresApi(21)
public final class AndroidUploadFs implements UploadFs {

    @Override
    public Kind lstat(String path) throws IOException {
        try {
            StructStat st = Os.lstat(path);
            if (OsConstants.S_ISDIR(st.st_mode)) return Kind.DIRECTORY;
            if (OsConstants.S_ISREG(st.st_mode)) return Kind.FILE;
            return Kind.OTHER;
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.ENOENT) return Kind.MISSING;
            throw failure(e);
        }
    }

    @Override
    public boolean writableDirectory(String path) {
        try {
            return Os.access(path, OsConstants.W_OK | OsConstants.X_OK);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public long freeBytes(String path) throws IOException {
        try {
            StructStatVfs vfs = Os.statvfs(path);
            return vfs.f_bavail * vfs.f_frsize;
        } catch (ErrnoException e) {
            throw failure(e);
        }
    }

    @Override
    public void mkdir(String path, int mode) throws IOException {
        try {
            Os.mkdir(path, mode);
            // The app's umask (077) would otherwise narrow it.
            Os.chmod(path, mode);
        } catch (ErrnoException e) {
            throw failure(e);
        }
    }

    @Override
    public Sink create(String path, int mode) throws IOException {
        final FileDescriptor fd;
        try {
            fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL
                    | OsConstants.O_NOFOLLOW, 0600);
        } catch (ErrnoException e) {
            throw failure(e);
        }
        try {
            Os.fchmod(fd, mode);
        } catch (ErrnoException e) {
            closeQuietly(fd);
            throw failure(e);
        }
        return new Sink() {
            @Override
            public void write(byte[] data, int offset, int length) throws IOException {
                try {
                    while (length > 0) {
                        int n = Os.write(fd, data, offset, length);
                        offset += n;
                        length -= n;
                    }
                } catch (ErrnoException e) {
                    throw failure(e);
                } catch (java.io.InterruptedIOException e) {
                    throw new Failure(OsConstants.EINTR, "interrupted");
                }
            }

            @Override
            public void finish() throws IOException {
                try {
                    Os.fsync(fd);
                    Os.close(fd);
                } catch (ErrnoException e) {
                    closeQuietly(fd);
                    throw failure(e);
                }
            }

            @Override
            public void abort() {
                closeQuietly(fd);
            }
        };
    }

    @Override
    public int renameNoReplace(String from, String to) {
        int result = Process.renameNoReplace(from, to);
        if (result != OsConstants.ENOSYS && result != OsConstants.EINVAL) return result;
        // Before Android 11, or a file system without RENAME_NOREPLACE: check,
        // then rename. Only another process of this same uid creating the same
        // name in between could be replaced.
        try {
            if (lstat(to) != Kind.MISSING) return EEXIST;
            Os.rename(from, to);
            return 0;
        } catch (ErrnoException e) {
            return e.errno;
        } catch (Failure e) {
            return e.errno;
        } catch (IOException e) {
            return OsConstants.EIO;
        }
    }

    @Override
    public void delete(String path) throws IOException {
        try {
            // remove(3): unlink(2), or rmdir(2) for a directory; a link itself goes.
            Os.remove(path);
        } catch (ErrnoException e) {
            throw failure(e);
        }
    }

    @Override
    public String[] list(String path) throws IOException {
        String[] names = new File(path).list();
        if (names == null) throw new IOException("cannot list directory");
        return names;
    }

    private static Failure failure(ErrnoException e) {
        return new Failure(e.errno, e.getMessage());
    }

    private static void closeQuietly(FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (ErrnoException ignore) {
            // Nothing more to release.
        }
    }
}
