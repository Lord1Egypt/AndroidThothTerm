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

import java.io.IOException;

/** Removing a staging tree without ever following a link out of it. */
@RequiresApi(21)
final class Trees {
    private Trees() {
    }

    /** Delete {@code path} and, if it is a real directory, everything in it. */
    static void delete(UploadFs fs, String path) throws IOException {
        if (fs.lstat(path) == UploadFs.Kind.DIRECTORY) {
            for (String name : fs.list(path)) delete(fs, path + "/" + name);
        }
        fs.delete(path);
    }
}
