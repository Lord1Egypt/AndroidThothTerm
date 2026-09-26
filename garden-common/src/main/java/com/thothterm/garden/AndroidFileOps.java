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

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Android implementation. Permissions are masked to {@code 0777}: setuid,
 * setgid, and sticky bits are never preserved from an archive. In a rootless
 * app-private installation those bits are meaningless and only add risk.
 */
public final class AndroidFileOps implements FileOps {
    @Override
    public boolean exists(File file) {
        return file.exists();
    }

    @Override
    public boolean isDirectory(File file) {
        return file.isDirectory();
    }

    @Override
    public boolean isSymlink(File file) {
        try {
            return OsConstants.S_ISLNK(Os.lstat(file.getAbsolutePath()).st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isRegularFile(File file) {
        try {
            return OsConstants.S_ISREG(Os.lstat(file.getAbsolutePath()).st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public void copyFile(File source, File destination, int mode) throws IOException {
        OutputStream out = createFile(destination, mode);
        InputStream in = new FileInputStream(source);
        try {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            out.close();
        }
    }

    @Override
    public void mkdirs(File dir, int mode) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + dir);
        }
        setMode(dir, mode);
    }

    @Override
    public OutputStream createFile(File file, int mode) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            mkdirs(parent, 0755);
        }
        OutputStream out = new FileOutputStream(file);
        setMode(file, mode);
        return out;
    }

    @Override
    public void symlink(String target, File link) throws IOException {
        try {
            Os.symlink(target, link.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException("symlink failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void hardlink(File existing, File link) throws IOException {
        try {
            Os.link(existing.getAbsolutePath(), link.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException("hardlink failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setMode(File file, int mode) {
        try {
            Os.chmod(file.getAbsolutePath(), mode & 0777);
        } catch (ErrnoException ignored) {
        }
    }

    @Override
    public void setLastModified(File file, long timeMillis) {
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(timeMillis);
    }

    @Override
    public String canonicalPath(File file) throws IOException {
        return file.getCanonicalPath();
    }
}
