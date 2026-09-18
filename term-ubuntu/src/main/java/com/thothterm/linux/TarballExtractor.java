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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Minimal, safe tar (ustar/GNU/pax) extractor used for the embedded Ubuntu
 * rootfs. It preserves directories, regular files, symlinks, hardlinks and
 * permission bits, and refuses path-traversal attempts. Device/fifo entries
 * are skipped. Setuid/setgid/sticky bits are dropped by {@link FileOps}.
 *
 * <p>This class is Android-free so the path-safety logic is unit-testable.</p>
 */
public final class TarballExtractor {
    private static final int BLOCK = 512;
    private static final Charset ASCII = Charset.forName("US-ASCII");

    public interface EntryListener {
        void onEntry(long extractedEntries);
    }

    private final FileOps ops;
    private final File targetDir;
    private final EntryListener listener;

    private long extractedEntries;
    private long rejectedEntries;
    private long skippedSpecialEntries;
    private long copiedBytes;

    public TarballExtractor(FileOps ops, File targetDir, EntryListener listener) {
        this.ops = ops;
        this.targetDir = targetDir;
        this.listener = listener;
    }

    public long extractedEntries() {
        return extractedEntries;
    }

    public long rejectedEntries() {
        return rejectedEntries;
    }

    public long skippedSpecialEntries() {
        return skippedSpecialEntries;
    }

    public long copiedBytes() {
        return copiedBytes;
    }

    public void extract(InputStream tar) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create extraction root: " + targetDir);
        }
        String canonicalTarget = ops.canonicalPath(targetDir);

        byte[] header = new byte[BLOCK];
        String pendingName = null;
        String pendingLink = null;

        while (true) {
            int read = readFully(tar, header, BLOCK);
            if (read == 0) break;
            if (read < BLOCK) throw new IOException("Truncated tar header");
            if (isZeroBlock(header)) break;

            long storedChecksum = parseOctal(header, 148, 8);
            long actualChecksum = checksum(header);
            if (storedChecksum != actualChecksum) {
                throw new IOException("Tar header checksum mismatch");
            }

            String name = parseString(header, 0, 100);
            int mode = (int) parseOctal(header, 100, 8);
            long size = parseNumeric(header, 124, 12);
            long mtime = parseNumeric(header, 136, 12);
            char type = (char) (header[156] & 0xFF);
            String link = parseString(header, 157, 100);
            String magic = parseString(header, 257, 6);
            String prefix = parseString(header, 345, 155);

            if (magic.startsWith("ustar") && prefix.length() > 0
                    && type != 'L' && type != 'K') {
                name = prefix + "/" + name;
            }

            if (type == 'L') {
                pendingName = readDataString(tar, size);
                continue;
            }
            if (type == 'K') {
                pendingLink = readDataString(tar, size);
                continue;
            }
            if (type == 'x' || type == 'g') {
                String[] pax = parsePaxHeaders(tar, size);
                if (pax[0] != null) pendingName = pax[0];
                if (pax[1] != null) pendingLink = pax[1];
                continue;
            }

            if (pendingName != null) {
                name = pendingName;
                pendingName = null;
            }
            if (pendingLink != null) {
                link = pendingLink;
                pendingLink = null;
            }

            extractEntry(tar, type, name, link, size, mode, mtime, canonicalTarget);
        }

        if (listener != null) listener.onEntry(extractedEntries);
    }

    private void extractEntry(InputStream tar, char type, String name, String link,
                              long size, int mode, long mtime, String canonicalTarget)
            throws IOException {
        String rel = sanitizeEntryName(name);
        if (rel == null) {
            rejectedEntries++;
            skip(tar, padded(size));
            return;
        }

        File dest = new File(targetDir, rel);

        if (type == '5') {
            if (!ensureContainedParent(dest, canonicalTarget)) {
                rejectedEntries++;
                return;
            }
            ops.mkdirs(dest, mode);
            ops.setLastModified(dest, mtime * 1000L);
            onEntry();
            return;
        }

        if (type == '0' || type == '\0' || type == '7') {
            if (!ensureContainedParent(dest, canonicalTarget)) {
                rejectedEntries++;
                skip(tar, padded(size));
                return;
            }
            removeNonDirectory(dest);
            OutputStream out = null;
            try {
                out = ops.createFile(dest, mode);
                copy(tar, out, size);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            skip(tar, pad(size));
            ops.setLastModified(dest, mtime * 1000L);
            copiedBytes += size;
            onEntry();
            return;
        }

        if (type == '2') {
            if (link == null || link.length() == 0) {
                rejectedEntries++;
                return;
            }
            if (!ensureContainedParent(dest, canonicalTarget)) {
                rejectedEntries++;
                return;
            }
            removeNonDirectory(dest);
            ops.symlink(link, dest);
            onEntry();
            return;
        }

        if (type == '1') {
            String linkRel = sanitizeEntryName(link);
            if (linkRel == null) {
                rejectedEntries++;
                return;
            }
            if (!ensureContainedParent(dest, canonicalTarget)) {
                rejectedEntries++;
                return;
            }
            File existing = new File(targetDir, linkRel);
            if (!ensureContained(existing, canonicalTarget) || !ops.exists(existing)) {
                rejectedEntries++;
                return;
            }
            removeNonDirectory(dest);
            ops.hardlink(existing, dest);
            onEntry();
            return;
        }

        // Character/block devices, FIFOs, and unknown types are not created.
        skippedSpecialEntries++;
        skip(tar, padded(size));
    }

    private boolean ensureContainedParent(File dest, String canonicalTarget) throws IOException {
        File parent = dest.getParentFile();
        if (parent == null) return false;

        File existing = parent;
        while (existing != null && !ops.exists(existing)) {
            existing = existing.getParentFile();
        }
        if (existing == null || !ensureContained(existing, canonicalTarget)) {
            return false;
        }

        if (!parent.exists()) ops.mkdirs(parent, 0755);

        return ensureContained(parent, canonicalTarget);
    }

    private boolean ensureContained(File file, String canonicalTarget) throws IOException {
        String canonical = ops.canonicalPath(file);
        return canonical.equals(canonicalTarget)
                || canonical.startsWith(canonicalTarget + File.separator);
    }

    private void removeNonDirectory(File file) throws IOException {
        if (ops.exists(file) && !ops.isDirectory(file)) {
            if (!file.delete()) throw new IOException("Cannot replace: " + file);
        }
    }

    private void onEntry() {
        extractedEntries++;
        if (listener != null && (extractedEntries % 64L) == 0L) {
            listener.onEntry(extractedEntries);
        }
    }

    /**
     * Normalizes an archive member name to a safe relative path, or returns
     * {@code null} when it would escape the extraction root.
     */
    static String sanitizeEntryName(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/');
        if (name.indexOf('\0') >= 0) return null;
        if (name.length() == 0 || name.length() > 4096) return null;
        if (name.charAt(0) == '/') return null;

        StringBuilder result = new StringBuilder(name.length());
        for (String part : name.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) return null;
            if (result.length() > 0) result.append('/');
            result.append(part);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private String readDataString(InputStream in, long size) throws IOException {
        if (size <= 0 || size > 1 << 20) throw new IOException("Invalid long-name size");
        byte[] data = new byte[(int) size];
        if (readFully(in, data, data.length) != data.length) {
            throw new IOException("Truncated tar long-name block");
        }
        int end = data.length;
        while (end > 0 && data[end - 1] == 0) end--;
        skip(in, pad(size));
        return new String(data, 0, end, ASCII);
    }

    private String[] parsePaxHeaders(InputStream in, long size) throws IOException {
        String path = null;
        String linkpath = null;
        if (size < 0 || size > 1 << 22) throw new IOException("Invalid pax header size");
        byte[] data = new byte[(int) size];
        if (readFully(in, data, data.length) != data.length) {
            throw new IOException("Truncated pax header");
        }
        skip(in, pad(size));

        int offset = 0;
        while (offset < data.length) {
            int space = -1;
            for (int i = offset; i < data.length; i++) {
                if (data[i] == ' ') {
                    space = i;
                    break;
                }
            }
            if (space < 0) break;
            int length;
            try {
                length = Integer.parseInt(new String(data, offset, space - offset, ASCII));
            } catch (NumberFormatException e) {
                break;
            }
            if (length <= 0 || offset + length > data.length) break;
            int keyStart = space + 1;
            int eq = -1;
            for (int i = keyStart; i < offset + length; i++) {
                if (data[i] == '=') {
                    eq = i;
                    break;
                }
            }
            if (eq < 0) {
                offset += length;
                continue;
            }
            String key = new String(data, keyStart, eq - keyStart, ASCII);
            int valueEnd = offset + length;
            if (valueEnd > 0 && data[valueEnd - 1] == '\n') valueEnd--;
            String value = new String(data, eq + 1, valueEnd - (eq + 1), ASCII);
            if (key.equals("path")) path = value;
            else if (key.equals("linkpath")) linkpath = value;
            offset += length;
        }
        return new String[]{path, linkpath};
    }

    private static int padded(long size) {
        return (int) (size + pad(size));
    }

    private static long pad(long size) {
        long rem = size % BLOCK;
        return rem == 0 ? 0 : BLOCK - rem;
    }

    private static void skip(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new IOException("Truncated tar data");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static void copy(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, want);
            if (read < 0) throw new IOException("Truncated tar data");
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static long checksum(byte[] header) {
        long sum = 0;
        for (int i = 0; i < BLOCK; i++) {
            if (i >= 148 && i < 156) continue;
            sum += header[i] & 0xFF;
        }
        for (int i = 148; i < 156; i++) sum += ' ';
        return sum;
    }

    private static String parseString(byte[] block, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && block[end] != 0) end++;
        return new String(block, offset, end - offset, ASCII);
    }

    private static long parseNumeric(byte[] block, int offset, int length) {
        if ((block[offset] & 0x80) != 0) {
            return parseBase256(block, offset, length);
        }
        return parseOctal(block, offset, length);
    }

    private static long parseOctal(byte[] block, int offset, int length) {
        long value = 0;
        int i = offset;
        int limit = offset + length;
        while (i < limit && (block[i] == ' ' || block[i] == 0)) i++;
        while (i < limit) {
            byte b = block[i];
            if (b < '0' || b > '7') break;
            value = (value << 3) + (b - '0');
            i++;
        }
        return value;
    }

    private static long parseBase256(byte[] block, int offset, int length) {
        long value = 0;
        for (int i = offset; i < offset + length; i++) {
            int b = block[i] & 0xFF;
            if (i == offset) b &= 0x7F;
            value = (value << 8) | b;
        }
        return value;
    }
}
