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

package com.thothterm.garden;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Filesystem operations used by {@link TarballExtractor}. Abstracted so the
 * path-safety logic can be unit-tested without Android APIs.
 */
public interface FileOps {
    boolean exists(File file);

    boolean isDirectory(File file);

    /** True when the path itself is a symbolic link (never follows it). */
    boolean isSymlink(File file);

    /** True when the path itself is a regular file (never follows symlinks). */
    boolean isRegularFile(File file);

    void mkdirs(File dir, int mode) throws IOException;

    OutputStream createFile(File file, int mode) throws IOException;

    void copyFile(File source, File destination, int mode) throws IOException;

    void symlink(String target, File link) throws IOException;

    void hardlink(File existing, File link) throws IOException;

    void setMode(File file, int mode);

    void setLastModified(File file, long timeMillis);

    String canonicalPath(File file) throws IOException;
}
