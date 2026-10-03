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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link FileOps} that delegates every operation and keeps, per object the
 * extractor made, what the extractor did to it. The extractor gate uses this
 * as a witness, never as the answer: every content claim it makes is checked
 * against the final object on disk ({@code ExtractorGate.ContentCheck}).
 *
 * <ul>
 *   <li><b>Names map to nodes, nodes are objects.</b> {@link #createNew},
 *       {@link #symlink} and {@link #mkdir} make a new node; {@link #hardlink}
 *       gives an existing node one more name; {@link #unlink}, {@link #rmdir}
 *       and {@link #rename} only move or drop names (a rename also drops
 *       whatever it replaced). So the names that still share a node at the
 *       end are exactly the hard link groups the delegate created, whichever
 *       member was renamed or removed on the way.</li>
 *   <li><b>Content.</b> A regular file's node records the SHA-256 and
 *       length of the bytes written to it, complete only once its stream is
 *       closed.</li>
 *   <li><b>Pins.</b> Before a regular file can become unreadable to its owner
 *       -- created with such a mode, or chmodded to one -- a read descriptor
 *       is opened on it while it is still readable
 *       ({@link ObjectInspector#pin}). That is how its final bytes are read
 *       later without changing its mode.</li>
 * </ul>
 *
 * <p>Not thread-safe; the extractor is single-threaded. {@link #close}
 * releases every pin.</p>
 */
public final class RecordingFileOps implements FileOps, java.io.Closeable {
    /** One object the extractor created. */
    public static final class Node {
        private MessageDigest digest;
        private long length;
        private String sha256;
        private ObjectInspector.Pin pin;

        /** Hex SHA-256 of the bytes written, or null if none were, or not completely. */
        public String sha256() {
            return sha256;
        }

        public long length() {
            return length;
        }

        /** The read descriptor held on this object, or null. */
        public ObjectInspector.Pin pin() {
            return pin;
        }
    }

    private final FileOps delegate;
    private final ObjectInspector inspector;
    /** Absolute name -> the node it names now. */
    private final Map<String, Node> names = new HashMap<>();
    private final List<ObjectInspector.Pin> pins = new ArrayList<>();

    public RecordingFileOps(FileOps delegate, ObjectInspector inspector) {
        this.delegate = delegate;
        this.inspector = inspector;
    }

    /** The node a path names now, or null when the extractor made nothing there. */
    public Node nodeOf(File file) {
        return names.get(key(file));
    }

    /**
     * Every node that more than one surviving name refers to, with those names
     * in sorted order: what must be one inode on disk.
     */
    public List<List<File>> hardlinkGroups() {
        Map<Node, List<String>> byNode = new IdentityHashMap<>();
        for (Map.Entry<String, Node> e : names.entrySet()) {
            List<String> list = byNode.get(e.getValue());
            if (list == null) byNode.put(e.getValue(), list = new ArrayList<>());
            list.add(e.getKey());
        }
        List<List<File>> groups = new ArrayList<>();
        for (List<String> list : byNode.values()) {
            if (list.size() < 2) continue;
            java.util.Collections.sort(list);
            List<File> files = new ArrayList<>();
            for (String name : list) files.add(new File(name));
            groups.add(files);
        }
        return groups;
    }

    private static String key(File file) {
        return file.getAbsolutePath();
    }

    private static boolean isAtOrUnder(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + File.separator);
    }

    /** Drops every name at or under {@code prefix}. */
    private void forget(String prefix) {
        Iterator<String> it = names.keySet().iterator();
        while (it.hasNext()) {
            if (isAtOrUnder(it.next(), prefix)) it.remove();
        }
    }

    @Override
    public OutputStream createNew(File file, int mode) throws IOException {
        OutputStream out = delegate.createNew(file, mode);
        final Node node = new Node();
        names.put(key(file), node);
        try {
            node.digest = JvmObjectInspector.sha256Digest();
            if ((mode & FILE_MODE_MASK & 0400) == 0) pin(node, file);
        } catch (IOException e) {
            out.close();
            throw e;
        }
        return new FilterOutputStream(out) {
            private boolean closed;

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                node.digest.update(b, off, len);
                node.length += len;
            }

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                out.close();
                node.sha256 = RootfsArchive.toHex(node.digest.digest());
            }
        };
    }

    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        Node node = names.get(key(file));
        if (node != null && node.pin == null && node.sha256 != null
                && (mode & FILE_MODE_MASK & 0400) == 0
                && delegate.type(file) == Type.REGULAR) {
            pin(node, file);
        }
        delegate.chmodNoFollow(file, mode);
    }

    private void pin(Node node, File file) throws IOException {
        node.pin = inspector.pin(file);
        pins.add(node.pin);
    }

    @Override
    public void hardlink(File existing, File link) throws IOException {
        delegate.hardlink(existing, link);
        Node node = names.get(key(existing));
        // A name the extractor did not make (nothing to verify) still gets a
        // node, so the pair is checked as one inode.
        if (node == null) names.put(key(existing), node = new Node());
        names.put(key(link), node);
    }

    @Override
    public void symlink(String target, File link) throws IOException {
        delegate.symlink(target, link);
        names.put(key(link), new Node());
    }

    @Override
    public void mkdir(File dir, int mode) throws IOException {
        delegate.mkdir(dir, mode);
        names.put(key(dir), new Node());
    }

    @Override
    public void unlink(File file) throws IOException {
        delegate.unlink(file);
        names.remove(key(file));
    }

    @Override
    public void rmdir(File dir) throws IOException {
        delegate.rmdir(dir);
        forget(key(dir));
    }

    @Override
    public void rename(File from, File to) throws IOException {
        delegate.rename(from, to);
        String source = key(from);
        String target = key(to);
        Map<String, Node> moved = new LinkedHashMap<>();
        Iterator<Map.Entry<String, Node>> it = names.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Node> e = it.next();
            if (isAtOrUnder(e.getKey(), source)) {
                moved.put(target + e.getKey().substring(source.length()), e.getValue());
                it.remove();
            }
        }
        // rename(2) replaced whatever was at the target.
        forget(target);
        names.putAll(moved);
    }

    /** Closes every pin. */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (ObjectInspector.Pin pin : pins) {
            try {
                pin.close();
            } catch (IOException e) {
                if (failure == null) failure = e;
            }
        }
        pins.clear();
        if (failure != null) throw failure;
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
    public InputStream openNoFollow(File file) throws IOException {
        return delegate.openNoFollow(file);
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
