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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The staging directories of uploads under way, recorded in app-private
 * storage before each is created. If the app dies mid-upload -- killed,
 * crashed, force-stopped -- the next start removes what is listed, so no
 * half-written upload is left behind in anyone's directory. Only paths this
 * journal wrote, whose name is a staging name, are ever removed.
 */
public final class StagingJournal {
    private final File file;
    private final Set<String> paths = new LinkedHashSet<>();

    public StagingJournal(File file) {
        this.file = file;
    }

    synchronized void add(String path) throws IOException {
        if (paths.add(path)) save();
    }

    synchronized void remove(String path) {
        if (paths.remove(path)) {
            try {
                save();
            } catch (IOException ignore) {
                // A stale entry only costs a lookup at the next sweep.
            }
        }
    }

    /**
     * Remove every staging directory left by a previous run. Call once, before
     * this process starts uploads of its own. Returns how many were removed.
     */
    public synchronized int sweep(UploadFs fs) {
        List<String> left = new ArrayList<>(load());
        int removed = 0;
        for (String path : left) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.startsWith(UploadNames.STAGING_PREFIX)) continue;
            try {
                if (fs.lstat(path) == UploadFs.Kind.DIRECTORY) {
                    Trees.delete(fs, path);
                    ++removed;
                }
            } catch (IOException ignore) {
                // Gone or unreachable; nothing more we can do.
            }
        }
        paths.clear();
        try {
            save();
        } catch (IOException ignore) {
            // Next sweep retries.
        }
        return removed;
    }

    private List<String> load() {
        List<String> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
            // NUL-separated: a directory name may contain a newline.
            for (String entry : new String(bytes.toByteArray(), StandardCharsets.UTF_8).split("\0")) {
                if (entry.startsWith("/")) result.add(entry);
            }
        } catch (IOException ignore) {
            // Unreadable: nothing to sweep.
        }
        return result;
    }

    private void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create journal directory");
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            for (String path : paths) {
                out.write(path.getBytes(StandardCharsets.UTF_8));
                out.write(0);
            }
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) throw new IOException("cannot replace journal");
    }
}
