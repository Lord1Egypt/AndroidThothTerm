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

package com.thothterm.lan;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A real pseudo-terminal with a shell on its slave side. The Android
 * implementation starts the same PRoot + Ubuntu command line as a local
 * window; tests substitute an in-memory one.
 */
interface Pty {
    InputStream input();

    OutputStream output();

    /** TIOCSWINSZ on the master, which also sends SIGWINCH to the foreground job. */
    void resize(int columns, int rows) throws IOException;

    /** Block until the shell's process tree leader exits; returns its status. */
    int waitFor();

    /** Hang the terminal up and make sure its processes go, then release the master. */
    void hangUp();

    /** The session leader's pid, for Exit's final sweep; -1 when not a process. */
    int pid();

    interface Factory {
        Pty open(int columns, int rows) throws IOException;
    }
}
