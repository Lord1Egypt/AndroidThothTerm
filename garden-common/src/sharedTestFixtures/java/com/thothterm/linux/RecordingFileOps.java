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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link FileOps} that delegates every operation and records, for each
 * regular file the extractor writes, the SHA-256 and length of exactly the
 * bytes it wrote, while they pass through. The extractor gate compares that
 * record with the independent manifest, so a file whose final mode its owner
 * cannot read (Arch's {@code usr/lib/dbus-daemon-launch-helper} is 0110) is
 * verified without being reopened and without its mode ever being changed.
 *
 * <p>Records follow inodes, not names: {@link #hardlink} shares the record
 * of the file it links to, {@link #unlink} and {@link #rename} move or drop a
 * name, {@link #createNew} starts a new record. Every record is completed
 * only when its stream is closed. Not thread-safe; the extractor is
 * single-threaded.</p>
 */
public final class RecordingFileOps implements FileOps {
    /** What was written to one inode. */
    public static final class Content {
        private final MessageDigest digest;
        private long length;
        private String sha256;

        private Content() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Hex SHA-256 of the bytes written, or null if the stream was never closed. */
        public String sha256() {
            return sha256;
        }

        public long length() {
            return length;
        }
    }

    /** A real hard link the delegate created: both names must be one inode. */
    public static final class Link {
        public final File existing;
        public final File link;

        Link(File existing, File link) {
            this.existing = existing;
            this.link = link;
        }
    }

    private final FileOps delegate;
    private final Map<String, Content> contents = new HashMap<>();
    private final List<Link> links = new ArrayList<>();

    public RecordingFileOps(FileOps delegate) {
        this.delegate = delegate;
    }

    /** The record of the regular file at {@code file}, or null if none was written there. */
    public Content contentOf(File file) {
        return contents.get(key(file));
    }

    /** Hard links created as such (not materialized as copies). */
    public List<Link> links() {
        return new ArrayList<>(links);
    }

    private static String key(File file) {
        return file.getAbsolutePath();
    }

    @Override
    public OutputStream createNew(File file, int mode) throws IOException {
        OutputStream out = delegate.createNew(file, mode);
        final Content content = new Content();
        contents.put(key(file), content);
        return new FilterOutputStream(out) {
            private boolean closed;

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                content.digest.update(b, off, len);
                content.length += len;
            }

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                out.close();
                content.sha256 = RootfsArchive.toHex(content.digest.digest());
            }
        };
    }

    @Override
    public void hardlink(File existing, File link) throws IOException {
        delegate.hardlink(existing, link);
        Content content = contents.get(key(existing));
        if (content != null) contents.put(key(link), content);
        links.add(new Link(existing, link));
    }

    @Override
    public void unlink(File file) throws IOException {
        delegate.unlink(file);
        contents.remove(key(file));
        forgetLinks(key(file));
    }

    /** A name that goes away no longer describes a pair of names for one inode. */
    private void forgetLinks(String name) {
        java.util.Iterator<Link> it = links.iterator();
        while (it.hasNext()) {
            Link l = it.next();
            String a = key(l.existing);
            String b = key(l.link);
            if (a.equals(name) || b.equals(name) || a.startsWith(name + File.separator)
                    || b.startsWith(name + File.separator)) {
                it.remove();
            }
        }
    }

    @Override
    public void rename(File from, File to) throws IOException {
        delegate.rename(from, to);
        forgetLinks(key(from));
        forgetLinks(key(to));
        String prefix = key(from);
        Map<String, Content> moved = new HashMap<>();
        java.util.Iterator<Map.Entry<String, Content>> it = contents.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Content> e = it.next();
            String k = e.getKey();
            if (k.equals(prefix) || k.startsWith(prefix + File.separator)) {
                moved.put(key(to) + k.substring(prefix.length()), e.getValue());
                it.remove();
            }
        }
        contents.keySet().remove(key(to));
        contents.putAll(moved);
    }

    // ---- pure delegation -------------------------------------------------

    @Override
    public Type type(File file) throws IOException {
        return delegate.type(file);
    }

    @Override
    public int permissions(File file) throws IOException {
        return delegate.permissions(file);
    }

    @Override
    public void mkdir(File dir, int mode) throws IOException {
        delegate.mkdir(dir, mode);
    }

    @Override
    public InputStream openNoFollow(File file) throws IOException {
        return delegate.openNoFollow(file);
    }

    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        delegate.chmodNoFollow(file, mode);
    }

    @Override
    public void rmdir(File dir) throws IOException {
        delegate.rmdir(dir);
    }

    @Override
    public void symlink(String target, File link) throws IOException {
        delegate.symlink(target, link);
    }

    @Override
    public String readlink(File link) throws IOException {
        return delegate.readlink(link);
    }

    @Override
    public void setLastModified(File file, long timeMillis) {
        delegate.setLastModified(file, timeMillis);
    }

    @Override
    public String canonicalPath(File file) throws IOException {
        return delegate.canonicalPath(file);
    }
}
