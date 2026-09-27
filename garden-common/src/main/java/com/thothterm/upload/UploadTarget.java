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

import androidx.annotation.RequiresApi;

/**
 * The directory an upload goes to, fixed when the upload begins: a later
 * {@code cd} in the terminal does not move an upload that is under way.
 */
@RequiresApi(21)
public final class UploadTarget {
    /** The directory as the app reaches it. */
    public final String hostPath;
    /** The directory as the terminal's user knows it (the guest path in a Garden edition). */
    public final String displayPath;
    /** The session's umask, applied to what the upload creates, as a shell's cp would. */
    public final int umask;

    public UploadTarget(String hostPath, String displayPath, int umask) {
        this.hostPath = hostPath;
        this.displayPath = displayPath;
        this.umask = umask & 0777;
    }

    int fileMode() {
        return 0666 & ~umask;
    }

    int directoryMode() {
        return 0777 & ~umask;
    }
}
