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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * What the extractor gate needs to prove a final object and FileOps does not
 * offer: the identity of a path itself ({@code lstat} dev and inode) and a
 * read descriptor held on one object ({@code fstat} identity, size, and its
 * current bytes). A file whose final mode its owner cannot read is pinned
 * while it is still readable; at verification its bytes are read through
 * that descriptor, and the descriptor's inode must be the inode at the final
 * path. Implementations: {@link JvmObjectInspector} (host),
 * {@code AndroidObjectInspector} (device, {@code android.system.Os}).
 */
public interface ObjectInspector {
    /** "dev:ino" of the path itself; never follows a final symlink. */
    String identity(File file) throws IOException;

    /** Opens and holds a read descriptor on the regular file at {@code file}. */
    Pin pin(File file) throws IOException;

    interface Pin extends Closeable {
        /** "dev:ino" of the held object (fstat). */
        String identity() throws IOException;

        /** Its current size (fstat). */
        long size() throws IOException;

        /** SHA-256 of its current bytes, read from offset 0 through the descriptor. */
        String sha256() throws IOException;
    }
}
