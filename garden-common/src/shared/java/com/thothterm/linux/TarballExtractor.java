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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, safe tar (ustar/GNU/pax) extractor for Garden rootfs archives,
 * shared by every edition (Arch, Debian, Ubuntu). It preserves directories,
 * regular files, symlinks, hardlinks and permission bits; device and FIFO
 * entries are skipped; setuid, setgid and sticky bits are never applied.
 *
 * <p>Security model (libarchive's "secure symlinks" behaviour):</p>
 * <ul>
 *   <li>names are strict UTF-8; absolute names, {@code ..} components (also
 *       spelled with backslashes) and NUL are rejected, while a literal
 *       backslash inside a name is kept, as Linux allows it;</li>
 *   <li>every intermediate component must be a real directory inside the
 *       root -- an entry is never extracted through a symlink;</li>
 *   <li>the final component is never followed: an existing non-directory is
 *       unlinked and the new file created with {@code O_CREAT|O_EXCL|O_NOFOLLOW},
 *       so a dangling symlink planted by an earlier entry cannot redirect it;</li>
 *   <li>a hardlink's target is resolved the same way and must be a regular
 *       file or symlink inside the root; the Android SELinux fallback copies it
 *       through {@code O_NOFOLLOW};</li>
 *   <li>symlink entries are created verbatim. Their targets are guest paths,
 *       resolved later by PRoot inside the guest; this extractor never follows
 *       any of them.</li>
 * </ul>
 *
 * <p>This class is Android-free so the policy is unit-testable; the system
 * calls are behind {@link FileOps}.</p>
 */
public final class TarballExtractor {
    private static final int BLOCK = 512;
    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Keep the first few rejection reasons for diagnostics. */
    private static final int MAX_REASONS = 20;

    /**
     * Directories are created owner-only during extraction; their archived
     * modes are recorded and applied only after every entry has been
     * materialized. Applying a restrictive final directory mode too early would
     * break later child or hardlink creation.
     */
    private static final int TEMP_DIR_MODE = 0700;
    /** Final mode of a parent directory the archive never listed itself. */
    private static final int IMPLICIT_DIR_MODE = 0755;

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
    private long hardlinkFallbacks;
    private final List<String> rejectionReasons = new ArrayList<>();

    /** Directory path -> its final mode, in creation order. */
    private final Map<String, PendingDir> pendingDirs = new LinkedHashMap<>();
    /**
     * Regular file path -> a final mode its owner cannot read (Arch's
     * {@code dbus-daemon-launch-helper} is 04110). Such a file is written
     * owner-readable and takes its mode only after every entry, like a
     * directory: a later hardlink entry that Android forces into a copy
     * ({@link #linkOrCopy}) must still be able to read it.
     */
    private final Map<String, PendingFile> pendingFiles = new LinkedHashMap<>();

    public TarballExtractor(FileOps ops, File targetDir, EntryListener listener) {
        this.ops = ops;
        this.targetDir = targetDir;
        this.listener = listener;
    }

    public long extractedEntries() {
        return extractedEntries;
    }

    /** Entries refused for safety; a trusted, pinned archive has none. */
    public long rejectedEntries() {
        return rejectedEntries;
    }

    /** The first rejection reasons, for the log. */
    public List<String> rejectionReasons() {
        return Collections.unmodifiableList(rejectionReasons);
    }

    public long skippedSpecialEntries() {
        return skippedSpecialEntries;
    }

    public long copiedBytes() {
        return copiedBytes;
    }

    /** Hardlinks that had to be materialized as copies (Android SELinux). */
    public long hardlinkFallbacks() {
        return hardlinkFallbacks;
    }

    public void extract(InputStream tar) throws IOException {
        FileOps.Type rootType = ops.type(targetDir);
        if (rootType == FileOps.Type.NONE) {
            ops.mkdir(targetDir, TEMP_DIR_MODE);
        } else if (rootType != FileOps.Type.DIRECTORY) {
            throw new IOException("Extraction root is not a directory: " + targetDir);
        }

        byte[] header = new byte[BLOCK];
        String pendingName = null;
        String pendingLink = null;
        boolean pendingBadName = false;

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

            String name = parseName(header, 0, 100);
            int mode = (int) parseOctal(header, 100, 8);
            long size = parseNumeric(header, 124, 12);
            long mtime = parseNumeric(header, 136, 12);
            char type = (char) (header[156] & 0xFF);
            String link = parseName(header, 157, 100);
            String magic = parseString(header, 257, 6);
            String prefix = parseName(header, 345, 155);
            if (size < 0) throw new IOException("Invalid tar entry size");

            // Only POSIX ustar ("ustar\0") has a name prefix; old GNU headers
            // ("ustar  ") keep other fields in those bytes.
            if (magic.equals("ustar") && type != 'L' && type != 'K') {
                if (prefix == null) {
                    name = null;
                } else if (prefix.length() > 0 && name != null) {
                    name = prefix + "/" + name;
                }
            }

            if (type == 'L') {
                pendingName = readDataString(tar, size);
                if (pendingName == null) pendingBadName = true;
                continue;
            }
            if (type == 'K') {
                pendingLink = readDataString(tar, size);
                if (pendingLink == null) pendingBadName = true;
                continue;
            }
            if (type == 'x' || type == 'g') {
                String[] pax = parsePaxHeaders(tar, size);
                if (pax[0] != null) pendingName = pax[0];
                if (pax[1] != null) pendingLink = pax[1];
                if (pax[2] != null) pendingBadName = true;
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
            if (pendingBadName) {
                name = null;
                pendingBadName = false;
            }

            extractEntry(tar, type, name, link, size, mode, mtime);
        }

        applyFinalFileModes();
        applyFinalDirectoryModes();
        if (listener != null) listener.onEntry(extractedEntries);
    }

    private void extractEntry(InputStream tar, char type, String name, String link,
                              long size, int mode, long mtime) throws IOException {
        boolean hasData = type == '0' || type == '\0' || type == '7';
        if (!hasData && size > 0) {
            // Only regular files carry data here; consume any other payload
            // so the stream stays aligned on the next header.
            skip(tar, padded(size));
            size = 0;
        }
        if (name == null) {
            reject("name is not valid UTF-8", "?");
            skip(tar, padded(size));
            return;
        }
        if (type == '5' && isRootName(name)) {
            // "./" names the extraction root itself, which already exists.
            onEntry();
            return;
        }
        String rel = sanitizeEntryName(name);
        if (rel == null) {
            reject("unsafe name", name);
            skip(tar, padded(size));
            return;
        }
        File dest = new File(targetDir, rel);

        if (!isExtractableType(type)) {
            // Character/block devices, FIFOs, and unknown types are not created.
            skippedSpecialEntries++;
            skip(tar, padded(size));
            return;
        }

        String parentProblem = prepareParents(rel);
        if (parentProblem != null) {
            reject(parentProblem, rel);
            skip(tar, padded(size));
            return;
        }

        if (type == '5') {
            FileOps.Type existing = ops.type(dest);
            if (existing != FileOps.Type.DIRECTORY) {
                if (existing != FileOps.Type.NONE) ops.unlink(dest);
                pendingFiles.remove(rel);
                ops.mkdir(dest, TEMP_DIR_MODE);
            }
            pendingDirs.put(rel, new PendingDir(dest, mode, mtime));
            onEntry();
            return;
        }

        if (hasData) {
            if (!clearForReplacement(dest, rel)) {
                skip(tar, padded(size));
                return;
            }
            OutputStream out = ops.createNew(dest, writeMode(rel, dest, mode));
            try {
                copy(tar, out, size);
            } finally {
                out.close();
            }
            skip(tar, pad(size));
            ops.setLastModified(dest, mtime * 1000L);
            copiedBytes += size;
            onEntry();
            return;
        }

        if (type == '2') {
            if (link == null || link.length() == 0 || link.indexOf('\0') >= 0) {
                reject("symlink without a valid target", rel);
                return;
            }
            if (!clearForReplacement(dest, rel)) return;
            ops.symlink(link, dest);
            onEntry();
            return;
        }

        // type == '1': a hardlink to an earlier entry.
        String linkRel = link == null ? null : sanitizeEntryName(link);
        if (linkRel == null) {
            reject("hardlink target is unsafe", rel + " -> " + link);
            return;
        }
        String targetProblem = walkExisting(linkRel);
        if (targetProblem != null) {
            reject("hardlink target " + targetProblem, rel + " -> " + linkRel);
            return;
        }
        File existing = new File(targetDir, linkRel);
        FileOps.Type targetType = ops.type(existing);
        if (targetType != FileOps.Type.REGULAR && targetType != FileOps.Type.SYMLINK) {
            reject("hardlink target is " + targetType, rel + " -> " + linkRel);
            return;
        }
        if (linkRel.equals(rel)) {
            // A hardlink to itself: the file is already there.
            onEntry();
            return;
        }
        if (!clearForReplacement(dest, rel)) return;
        boolean linked = linkOrCopy(existing, targetType, dest, rel, linkRel);
        PendingFile targetPending = pendingFiles.get(linkRel);
        if (linked && targetPending != null) {
            // A real hard link is the same inode: keep its final mode reachable
            // through this name too, in case the target name is replaced later.
            pendingFiles.put(rel, new PendingFile(dest, targetPending.mode));
        }
        onEntry();
    }

    private static boolean isExtractableType(char type) {
        return type == '0' || type == '\0' || type == '7' || type == '5'
                || type == '1' || type == '2';
    }

    private static boolean isRootName(String name) {
        for (String part : name.split("/")) {
            if (part.length() > 0 && !part.equals(".")) return false;
        }
        return name.length() > 0 && name.charAt(0) != '/';
    }

    /**
     * Makes every parent of {@code rel} a real directory inside the root,
     * creating missing ones. Returns a rejection reason, or null.
     */
    private String prepareParents(String rel) throws IOException {
        String[] parts = rel.split("/");
        File current = targetDir;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            current = new File(current, parts[i]);
            if (path.length() > 0) path.append('/');
            path.append(parts[i]);
            FileOps.Type type = ops.type(current);
            if (type == FileOps.Type.NONE) {
                ops.mkdir(current, TEMP_DIR_MODE);
                String key = path.toString();
                if (!pendingDirs.containsKey(key)) {
                    pendingDirs.put(key, new PendingDir(current, IMPLICIT_DIR_MODE, -1));
                }
            } else if (type == FileOps.Type.SYMLINK) {
                return "path goes through a symlink at " + path;
            } else if (type != FileOps.Type.DIRECTORY) {
                return "path goes through a non-directory at " + path;
            }
        }
        return null;
    }

    /** Like {@link #prepareParents} but creates nothing: for hardlink targets. */
    private String walkExisting(String rel) throws IOException {
        String[] parts = rel.split("/");
        File current = targetDir;
        for (int i = 0; i < parts.length - 1; i++) {
            current = new File(current, parts[i]);
            FileOps.Type type = ops.type(current);
            if (type != FileOps.Type.DIRECTORY) {
                return "path is not a real directory (" + type + ") at " + parts[i];
            }
        }
        return null;
    }

    /**
     * Clears the final component for a new non-directory: an existing file,
     * symlink (dangling or not) or special file is unlinked, never followed.
     * An existing directory is not replaced; the entry is rejected.
     */
    private boolean clearForReplacement(File dest, String rel) throws IOException {
        FileOps.Type existing = ops.type(dest);
        if (existing == FileOps.Type.NONE) return true;
        if (existing == FileOps.Type.DIRECTORY) {
            reject("would replace a directory", rel);
            return false;
        }
        ops.unlink(dest);
        pendingFiles.remove(rel);
        return true;
    }

    /**
     * The mode a new regular file is written with: its own, or owner-only
     * when its final mode would make it unreadable to its owner, recorded in
     * {@link #pendingFiles}.
     */
    private int writeMode(String rel, File dest, int mode) {
        int finalMode = mode & FileOps.FILE_MODE_MASK;
        if ((finalMode & 0400) != 0) return mode;
        pendingFiles.put(rel, new PendingFile(dest, finalMode));
        return 0600;
    }

    /**
     * Materializes a hardlink. Android SELinux forbids untrusted apps from
     * creating hardlinks ({@code neverallow all_untrusted_apps file_type:file
     * link}), which returns EACCES. When a real hardlink is refused, the
     * already validated target is materialized as a local copy instead: a
     * regular file through {@code O_NOFOLLOW}, a symlink by its target text.
     */
    /** @return true for a real hard link, false for a copy */
    private boolean linkOrCopy(File existing, FileOps.Type targetType, File dest,
                               String entry, String target) throws IOException {
        try {
            ops.hardlink(existing, dest);
            return true;
        } catch (IOException e) {
            try {
                if (ops.type(dest) != FileOps.Type.NONE) {
                    throw new IOException("destination appeared during hardlink");
                }
                if (targetType == FileOps.Type.SYMLINK) {
                    ops.symlink(ops.readlink(existing), dest);
                } else {
                    // A hard link is its target's inode, so the copy takes the
                    // target's final mode, not the link header's.
                    PendingFile pending = pendingFiles.get(target);
                    int targetMode = pending != null ? pending.mode : ops.permissions(existing);
                    copyNoFollow(existing, dest, writeMode(entry, dest, targetMode));
                }
            } catch (IOException copyError) {
                throw new IOException("hardlink failed entry=" + entry
                        + " target=" + target + ": " + e.getMessage()
                        + "; copy failed: " + copyError.getMessage(), e);
            }
            hardlinkFallbacks++;
            return false;
        }
    }

    private void copyNoFollow(File source, File dest, int mode) throws IOException {
        InputStream in = ops.openNoFollow(source);
        try {
            OutputStream out = ops.createNew(dest, mode);
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private void applyFinalFileModes() throws IOException {
        for (PendingFile file : pendingFiles.values()) {
            // Only a regular file; a replaced name was dropped from the map.
            if (ops.type(file.file) != FileOps.Type.REGULAR) continue;
            ops.chmodNoFollow(file.file, file.mode);
        }
    }

    private void applyFinalDirectoryModes() throws IOException {
        List<PendingDir> dirs = new ArrayList<>(pendingDirs.values());
        Collections.sort(dirs, new Comparator<PendingDir>() {
            @Override
            public int compare(PendingDir a, PendingDir b) {
                return depth(b.file) - depth(a.file);
            }
        });
        for (PendingDir dir : dirs) {
            // Only a real directory; anything else was rejected or replaced.
            if (ops.type(dir.file) != FileOps.Type.DIRECTORY) continue;
            ops.chmodNoFollow(dir.file, dir.mode);
            if (dir.mtime >= 0) ops.setLastModified(dir.file, dir.mtime * 1000L);
        }
    }

    private static int depth(File file) {
        return file.getAbsolutePath().split("/").length;
    }

    private void reject(String reason, String entry) {
        rejectedEntries++;
        if (rejectionReasons.size() < MAX_REASONS) {
            rejectionReasons.add(reason + ": " + entry);
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
        if (raw.indexOf('\0') >= 0) return null;
        if (raw.length() == 0 || raw.length() > 4096) return null;

        // A backslash is a valid Linux filename character. Arch Linux ARM's
        // systemd package uses literal "\\x2d" in three unit names. Rewriting
        // those bytes as separators silently loses installed package files.
        // Still reject Windows-style traversal and absolute names, then use
        // only the actual Unix separator when constructing the destination.
        String safetyName = raw.replace('\\', '/');
        if (safetyName.charAt(0) == '/') return null;
        for (String part : safetyName.split("/")) {
            if (part.equals("..")) return null;
        }

        StringBuilder result = new StringBuilder(raw.length());
        for (String part : raw.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (result.length() > 0) result.append('/');
            result.append(part);
        }
        return result.length() == 0 ? null : result.toString();
    }

    /** Strict UTF-8, or null for a malformed name (the entry is then rejected). */
    static String decodeName(byte[] data, int offset, int length) {
        CharsetDecoder decoder = UTF8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(data, offset, length)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
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
        return decodeName(data, 0, end);
    }

    private String[] parsePaxHeaders(InputStream in, long size) throws IOException {
        String path = null;
        String linkpath = null;
        String malformed = null;
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
            if (key.equals("path") || key.equals("linkpath")) {
                // pax records are UTF-8 by definition.
                String value = decodeName(data, eq + 1, valueEnd - (eq + 1));
                if (value == null) malformed = key;
                else if (key.equals("path")) path = value;
                else linkpath = value;
            }
            offset += length;
        }
        return new String[]{path, linkpath, malformed};
    }

    private static long padded(long size) {
        return size + pad(size);
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

    /** A NUL-terminated header name field, strict UTF-8; null if malformed. */
    private static String parseName(byte[] block, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && block[end] != 0) end++;
        return decodeName(block, offset, end - offset);
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

    private static final class PendingFile {
        final File file;
        final int mode;

        PendingFile(File file, int mode) {
            this.file = file;
            this.mode = mode;
        }
    }

    private static final class PendingDir {
        final File file;
        final int mode;
        /** Seconds, or -1 for a directory the archive never listed. */
        final long mtime;

        PendingDir(File file, int mode, long mtime) {
            this.file = file;
            this.mode = mode;
            this.mtime = mtime;
        }
    }
}
