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

import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The real /proc. Android shows an app only its own processes there, so this
 * never reaches another app's.
 */
@RequiresApi(21)
public final class ProcFiles implements SessionDirectory.Proc {
    public static final ProcFiles SYSTEM = new ProcFiles();

    private ProcFiles() {
    }

    @Override
    public String stat(int pid) throws IOException {
        return read("/proc/" + pid + "/stat");
    }

    @Override
    public String status(int pid) throws IOException {
        return read("/proc/" + pid + "/status");
    }

    @Override
    public String cwd(int pid) throws IOException {
        try {
            return Os.readlink("/proc/" + pid + "/cwd");
        } catch (ErrnoException e) {
            throw new IOException("cwd unreadable", e);
        }
    }

    @Override
    public int[] pids() throws IOException {
        String[] names = new File("/proc").list();
        if (names == null) throw new IOException("cannot list /proc");
        int[] pids = new int[names.length];
        int n = 0;
        for (String name : names) {
            int pid = parsePid(name);
            if (pid > 0) pids[n++] = pid;
        }
        int[] result = new int[n];
        System.arraycopy(pids, 0, result, 0, n);
        return result;
    }

    private static int parsePid(String name) {
        if (name.isEmpty() || name.length() > 10) return -1;
        for (int i = 0; i < name.length(); ++i) {
            if (name.charAt(i) < '0' || name.charAt(i) > '9') return -1;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String read(String path) throws IOException {
        byte[] buffer = new byte[8192];
        int length = 0;
        try (InputStream in = new FileInputStream(path)) {
            int read;
            while (length < buffer.length
                    && (read = in.read(buffer, length, buffer.length - length)) > 0) {
                length += read;
            }
        }
        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }
}
