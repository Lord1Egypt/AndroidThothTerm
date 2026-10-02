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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/**
 * Reads and writes the guest files the app manages ({@code /etc/passwd},
 * {@code ~/.bashrc}, {@code /etc/thothterm/*}, ...) from the Android side.
 *
 * <ul>
 *   <li>A guest symlink is followed the way the guest sees it, inside the
 *       rootfs ({@link GuestPaths}); a link that would leave the rootfs is
 *       never written through, so a user's {@code ~/.bashrc -> dotfiles/bashrc}
 *       works and an absolute guest link can never send a write to an Android
 *       path.</li>
 *   <li>Files are read whole and decoded as strict UTF-8. A file that is not
 *       valid UTF-8 is left exactly as it is instead of being rewritten with
 *       replacement characters.</li>
 *   <li>Writes go to a temporary file in the same directory and are renamed
 *       into place, so a crash never leaves a truncated configuration file.</li>
 * </ul>
 */
public final class ManagedFiles {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ManagedFiles() {
    }

    /** Thrown when a file cannot be managed safely; callers log and skip. */
    public static final class UnmanageableException extends IOException {
        public UnmanageableException(String message) {
            super(message);
        }
    }

    /**
     * The text of a guest file, or "" when it does not exist.
     *
     * @throws UnmanageableException when it is not valid UTF-8, is not a
     *                               regular file, or resolves outside the rootfs
     */
    public static String read(FileOps ops, File root, File file) throws IOException {
        File target = resolve(ops, root, file);
        FileOps.Type type = ops.type(target);
        if (type == FileOps.Type.NONE) return "";
        if (type != FileOps.Type.REGULAR) {
            throw new UnmanageableException("Not a regular file: " + file);
        }
        String text = decode(readBytes(ops, target));
        if (text == null) throw new UnmanageableException("Not valid UTF-8, left as is: " + file);
        return text;
    }

    /**
     * Writes {@code text} unless the file already holds exactly it.
     *
     * @param mode permission bits for a new or rewritten file, or -1 to keep
     *             the existing file's bits (0644 for a new file)
     * @return true when the file was (re)written
     */
    public static boolean writeIfChanged(FileOps ops, File root, File file, String text, int mode)
            throws IOException {
        File target = resolve(ops, root, file);
        FileOps.Type type = ops.type(target);
        int finalMode = mode;
        if (type == FileOps.Type.REGULAR) {
            String current = decode(readBytes(ops, target));
            if (text.equals(current)) {
                if (mode >= 0 && (ops.permissions(target) & 07777) != (mode & 0777)) {
                    ops.chmodNoFollow(target, mode);
                }
                return false;
            }
            if (finalMode < 0) finalMode = ops.permissions(target) & 0777;
        } else if (type != FileOps.Type.NONE) {
            throw new UnmanageableException("Not a regular file: " + file);
        }
        if (finalMode < 0) finalMode = 0644;

        File parent = target.getParentFile();
        ensureDirectories(ops, root, parent);
        // The rename needs write permission on the directory. A directory the
        // image ships owner-read-only (the app owns every guest file) gets it
        // for the duration of the write, as the guest's fake root would.
        int parentMode = ops.permissions(parent);
        boolean unlock = (parentMode & 0300) != 0300;
        if (unlock) ops.chmodNoFollow(parent, parentMode | 0300);
        try {
            File temp = new File(parent, "." + target.getName() + ".thothterm-new");
            if (ops.type(temp) != FileOps.Type.NONE) ops.unlink(temp);
            OutputStream out = ops.createNew(temp, finalMode);
            try {
                out.write(text.getBytes(UTF8));
            } finally {
                out.close();
            }
            ops.rename(temp, target);
        } finally {
            if (unlock) ops.chmodNoFollow(parent, parentMode);
        }
        return true;
    }

    /** Creates missing directories below {@code root}, never through a symlink. */
    public static void ensureDirectories(FileOps ops, File root, File dir) throws IOException {
        String guest = GuestPaths.guestPath(root, dir);
        File current = root;
        for (String part : guest.split("/")) {
            if (part.isEmpty()) continue;
            current = new File(current, part);
            FileOps.Type type = ops.type(current);
            if (type == FileOps.Type.NONE) {
                ops.mkdir(current, 0755);
            } else if (type != FileOps.Type.DIRECTORY) {
                throw new UnmanageableException("Not a directory: " + current);
            }
        }
    }

    private static File resolve(FileOps ops, File root, File file) throws IOException {
        File target = GuestPaths.resolve(ops, root, GuestPaths.guestPath(root, file), true);
        if (target == null) throw new UnmanageableException("Symlink loop at " + file);
        // Directories on the way were resolved inside the rootfs, so only the
        // parents that do not exist yet remain; ensureDirectories handles them.
        return target;
    }

    private static byte[] readBytes(FileOps ops, File file) throws IOException {
        InputStream in = ops.openNoFollow(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Strict UTF-8 over the whole file, or null. */
    static String decode(byte[] bytes) {
        try {
            return UTF8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
