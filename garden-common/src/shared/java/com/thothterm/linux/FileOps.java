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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Filesystem primitives for {@link TarballExtractor} and {@link SafeFileTree}.
 *
 * <p>Every operation acts on the named path itself and never follows a
 * symbolic link in the final component. Intermediate components are the
 * caller's responsibility: the extractor walks them one by one with
 * {@link #type(File)} and refuses to descend through anything that is not a
 * real directory. Together that is the libarchive "secure symlinks" model:
 * nothing an archive creates can redirect a later write.</p>
 *
 * <p>Implementations: {@link AndroidFileOps} (the app, {@code android.system.Os})
 * and the JVM implementation used by unit tests and the host extractor gate.
 * Both must satisfy the same contract; the shared contract test checks it.</p>
 */
public interface FileOps {
    /** The permission bits a directory may carry: rwx for all, plus sticky. */
    int DIRECTORY_MODE_MASK = 01777;
    /** The permission bits a regular file may carry: never setuid or setgid. */
    int FILE_MODE_MASK = 0777;

    /** What a path itself is, as {@code lstat(2)} reports it. */
    enum Type {
        /** Nothing exists at the path (ENOENT). */
        NONE,
        REGULAR,
        DIRECTORY,
        SYMLINK,
        /** A device, FIFO or socket. */
        OTHER
    }

    /** lstat: the type of the path itself. Never follows a final symlink. */
    Type type(File file) throws IOException;

    /** Permission bits (07777) of the path itself, from {@code lstat(2)}. */
    int permissions(File file) throws IOException;

    /**
     * Creates one directory. Fails if anything, including a dangling symlink,
     * already exists at the path. The mode is applied without following links.
     */
    void mkdir(File dir, int mode) throws IOException;

    /**
     * Creates a new regular file exclusively ({@code O_CREAT|O_EXCL|O_NOFOLLOW}):
     * fails if anything, including a dangling symlink, exists at the path.
     * The file starts owner-only; {@code mode} (masked to 0777: no setuid,
     * setgid or sticky on a file) is applied to
     * the open descriptor when the stream is closed, so it can never land on
     * another inode.
     */
    OutputStream createNew(File file, int mode) throws IOException;

    /** Opens a regular file for reading; refuses a symlink ({@code O_NOFOLLOW}). */
    InputStream openNoFollow(File file) throws IOException;

    /**
     * Sets permission bits on a regular file or directory. setuid and setgid
     * are never applied; the sticky bit is kept on a directory only (a guest's
     * {@code /tmp} is 1777). A symlink is refused, never followed.
     */
    void chmodNoFollow(File file, int mode) throws IOException;

    /** Removes a non-directory entry; a symlink itself is removed, not its target. */
    void unlink(File file) throws IOException;

    /** Removes an empty directory; refuses a symlink. */
    void rmdir(File dir) throws IOException;

    /** Creates a symlink; fails if anything exists at {@code link}. */
    void symlink(String target, File link) throws IOException;

    /** The target text of a symlink. */
    String readlink(File link) throws IOException;

    /**
     * {@code link(2)}: a hard link to {@code existing} itself (a symlink is not
     * dereferenced); fails if anything exists at {@code link}.
     */
    void hardlink(File existing, File link) throws IOException;

    /** {@code rename(2)}: atomic within one filesystem; never follows either path. */
    void rename(File from, File to) throws IOException;

    /** Best effort; call only on a path just verified to be a regular file or directory. */
    void setLastModified(File file, long timeMillis);

    String canonicalPath(File file) throws IOException;

    // ---- conveniences on top of the primitives ---------------------------

    /** True when anything, a dangling symlink included, exists at the path. */
    default boolean exists(File file) {
        try {
            return type(file) != Type.NONE;
        } catch (IOException e) {
            return false;
        }
    }

    /** True for a real directory; a symlink to a directory is not one. */
    default boolean isDirectory(File file) {
        try {
            return type(file) == Type.DIRECTORY;
        } catch (IOException e) {
            return false;
        }
    }

    default boolean isSymlink(File file) {
        try {
            return type(file) == Type.SYMLINK;
        } catch (IOException e) {
            return false;
        }
    }

    default boolean isRegularFile(File file) {
        try {
            return type(file) == Type.REGULAR;
        } catch (IOException e) {
            return false;
        }
    }

    /** Best-effort {@link #chmodNoFollow}; a failure or a symlink is ignored. */
    default void setMode(File file, int mode) {
        try {
            chmodNoFollow(file, mode);
        } catch (IOException ignored) {
            // Best effort by contract.
        }
    }
}
