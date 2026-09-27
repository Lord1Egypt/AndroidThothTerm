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

import java.io.IOException;

/**
 * The few file-system operations an upload needs, none of which follows a
 * symbolic link. {@link AndroidUploadFs} is the app's; JVM tests use one on
 * java.nio.file.
 */
public interface UploadFs {
    int EEXIST = 17;
    int ENOTEMPTY = 39;
    int ENOSPC = 28;
    int EDQUOT = 122;

    enum Kind { MISSING, DIRECTORY, FILE, OTHER }

    /** An I/O failure that knows its errno. */
    final class Failure extends IOException {
        public final int errno;

        public Failure(int errno, String message) {
            super(message);
            this.errno = errno;
        }
    }

    /** An open, exclusively created file. */
    interface Sink {
        void write(byte[] data, int offset, int length) throws IOException;

        /** fsync and close: the data is on disk. */
        void finish() throws IOException;

        /** Close without caring about the data. */
        void abort();
    }

    /** What is at {@code path} itself; a symbolic link is OTHER. */
    Kind lstat(String path) throws IOException;

    /** Whether the app may create entries in the directory (write and search). */
    boolean writableDirectory(String path);

    long freeBytes(String path) throws IOException;

    /** Create a new directory with exactly {@code mode}; fails if anything is there. */
    void mkdir(String path, int mode) throws IOException;

    /** Create a new file with exactly {@code mode}; fails if anything is there, a link included. */
    Sink create(String path, int mode) throws IOException;

    /** Move {@code from} to {@code to} unless {@code to} exists. Returns 0 or the errno. */
    int renameNoReplace(String from, String to);

    /** Unlink a non-directory, or remove an empty directory. */
    void delete(String path) throws IOException;

    /** The names in a directory. */
    String[] list(String path) throws IOException;
}
