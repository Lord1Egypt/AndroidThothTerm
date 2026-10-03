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

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.security.MessageDigest;

/** {@link ObjectInspector} on a device: {@code lstat}, {@code open}, {@code fstat}, {@code pread}. */
final class AndroidObjectInspector implements ObjectInspector {
    @Override
    public String identity(File file) throws IOException {
        try {
            return key(Os.lstat(file.getAbsolutePath()));
        } catch (ErrnoException e) {
            throw new IOException("lstat " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Pin pin(File file) throws IOException {
        final FileDescriptor fd;
        try {
            fd = Os.open(file.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
        } catch (ErrnoException e) {
            throw new IOException("open " + file + ": " + e.getMessage(), e);
        }
        return new Pin() {
            @Override
            public String identity() throws IOException {
                return key(fstat());
            }

            @Override
            public long size() throws IOException {
                return fstat().st_size;
            }

            @Override
            public String sha256() throws IOException {
                MessageDigest digest = JvmObjectInspector.sha256Digest();
                byte[] buffer = new byte[64 * 1024];
                long position = 0;
                try {
                    int read;
                    while ((read = Os.pread(fd, buffer, 0, buffer.length, position)) > 0) {
                        digest.update(buffer, 0, read);
                        position += read;
                    }
                } catch (ErrnoException e) {
                    throw new IOException("pread: " + e.getMessage(), e);
                }
                return RootfsArchive.toHex(digest.digest());
            }

            @Override
            public void close() throws IOException {
                try {
                    Os.close(fd);
                } catch (ErrnoException e) {
                    throw new IOException("close: " + e.getMessage(), e);
                }
            }

            private StructStat fstat() throws IOException {
                try {
                    return Os.fstat(fd);
                } catch (ErrnoException e) {
                    throw new IOException("fstat: " + e.getMessage(), e);
                }
            }
        };
    }

    private static String key(StructStat st) {
        return st.st_dev + ":" + st.st_ino;
    }
}
