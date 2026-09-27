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
import java.util.Locale;

/**
 * Why an upload could not start or did not finish. The code is what the user
 * is told (a string on the phone, a JSON error for the browser); the message
 * is for tests and never carries file contents.
 */
public final class UploadError extends IOException {
    public enum Code {
        /** The session's current directory cannot be determined. */
        NO_DIRECTORY,
        /** The current directory is somewhere uploads may not go. */
        OUTSIDE,
        /** The current directory was removed. */
        DIRECTORY_GONE,
        /** The current directory is not writable. */
        NOT_WRITABLE,
        /** Not enough storage, before or during the transfer. */
        NO_SPACE,
        /** A file or folder name that cannot be used. */
        BAD_NAME,
        /** No free "name (n)" was found to keep both. */
        CONFLICT,
        /** Another upload is already running for this terminal. */
        BUSY,
        /** The user, sign-out, LAN Mode off or Exit stopped it. */
        CANCELLED,
        /** Reading the source or writing the file failed. */
        IO;

        /** The code as the browser receives it: {@code no_space}. */
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Code code;

    public UploadError(Code code, String message) {
        super(message);
        this.code = code;
    }

    public UploadError(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
