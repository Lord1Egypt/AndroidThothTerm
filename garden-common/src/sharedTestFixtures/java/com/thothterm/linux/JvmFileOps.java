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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * {@link FileOps} for the JVM (unit tests and the host extractor gate), with
 * the same no-follow contract as {@link AndroidFileOps}: {@code CREATE_NEW}
 * is {@code O_CREAT|O_EXCL} and {@code NOFOLLOW_LINKS} is {@code O_NOFOLLOW}
 * in OpenJDK on Linux. {@link FileOpsContractTest} holds both to it.
 */
public class JvmFileOps implements FileOps {
    @Override
    public Type type(File file) throws IOException {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return Type.NONE;
        }
        if (attrs.isSymbolicLink()) return Type.SYMLINK;
        if (attrs.isDirectory()) return Type.DIRECTORY;
        if (attrs.isRegularFile()) return Type.REGULAR;
        return Type.OTHER;
    }

    @Override
    public int permissions(File file) throws IOException {
        Object mode = Files.getAttribute(file.toPath(), "unix:mode", LinkOption.NOFOLLOW_LINKS);
        return ((Integer) mode) & 07777;
    }

    @Override
    public void mkdir(File dir, int mode) throws IOException {
        Files.createDirectory(dir.toPath());
        chmodNoFollow(dir, mode);
    }

    @Override
    public OutputStream createNew(File file, int mode) throws IOException {
        final Path path = file.toPath();
        final FileChannel channel = FileChannel.open(path, EnumSet.of(
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        permissions(0600)));
        final int finalMode = mode & FILE_MODE_MASK;
        return new FilterOutputStream(Channels.newOutputStream(channel)) {
            private boolean closed;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    chmodNoFollow(path.toFile(), finalMode);
                } finally {
                    channel.close();
                }
            }
        };
    }

    @Override
    public InputStream openNoFollow(File file) throws IOException {
        return Files.newInputStream(file.toPath(), StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Never follows a symlink, whatever happens to the path meanwhile; every
     * change is made through a descriptor bound to one object.
     *
     * <ol>
     *   <li>{@code lstat} only picks the mask and refuses the obvious cases;
     *       it is not what keeps the operation safe.</li>
     *   <li>{@code "unix:mode"} with {@code NOFOLLOW_LINKS} is, in OpenJDK,
     *       {@code open(O_RDONLY|O_NOFOLLOW)} + {@code fchmod} on that
     *       descriptor ({@code UnixFileAttributeViews.Posix.setMode},
     *       {@code UnixPath.openForAttributeAccess}): a symlink fails with
     *       ELOOP, and the mode lands on the inode that was opened.</li>
     *   <li>That open needs read permission, so an owner-unreadable file or
     *       directory (0200, 0300, 0110, 0000) fails with EACCES although
     *       chmod(2) needs none. The JVM cannot open {@code O_PATH}, so
     *       {@link #O_PATH_CHMOD} does it in a helper process, exactly as
     *       {@link AndroidFileOps#chmodNoFollow} does on the device:
     *       {@code open(O_PATH|O_NOFOLLOW)}, {@code fstat} that descriptor,
     *       chmod through {@code /proc/self/fd/N}, check the result. The type
     *       that decides the mask is the descriptor's, not the earlier
     *       {@code lstat}'s.</li>
     * </ol>
     *
     * <p>A path swapped for a symlink between the steps is refused (ELOOP or
     * the helper's type check); its target never changes.
     * {@code FileOpsContract.chmodSwapRacesNeverReachTheTarget} holds every
     * implementation to that.</p>
     */
    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        Type type = type(file);
        if (type != Type.REGULAR && type != Type.DIRECTORY) {
            throw new IOException("Refusing to chmod " + type + ": " + file);
        }
        int masked = mode & (type == Type.DIRECTORY ? DIRECTORY_MODE_MASK : FILE_MODE_MASK);
        try {
            Files.setAttribute(file.toPath(), "unix:mode", masked, LinkOption.NOFOLLOW_LINKS);
        } catch (AccessDeniedException unreadable) {
            beforeDescriptorChmod(file);
            chmodThroughPathDescriptor(file, mode);
        }
    }

    /** Test seam: runs between the failed read-only open and the O_PATH chmod. */
    protected void beforeDescriptorChmod(File file) throws IOException {
    }

    /**
     * {@code argv[1]} = the path as hex UTF-8 bytes (no locale, no shell),
     * {@code argv[2]} = the requested mode in octal. Exit 0 = done, 3 = not a
     * regular file or directory (a symlink in particular), 4 = the mode did
     * not take effect.
     */
    static final String O_PATH_CHMOD = String.join("\n",
            "import os, stat, sys",
            "path = bytes.fromhex(sys.argv[1]); mode = int(sys.argv[2], 8)",
            "fd = os.open(path, os.O_PATH | os.O_NOFOLLOW | os.O_CLOEXEC)",
            "try:",
            "    kind = os.fstat(fd).st_mode",
            "    if stat.S_ISDIR(kind): mode &= 0o1777",
            "    elif stat.S_ISREG(kind): mode &= 0o777",
            "    else: sys.exit(3)",
            "    os.chmod('/proc/self/fd/%d' % fd, mode)",
            "    if stat.S_IMODE(os.fstat(fd).st_mode) != mode: sys.exit(4)",
            "finally:",
            "    os.close(fd)",
            "");

    private static void chmodThroughPathDescriptor(File file, int mode) throws IOException {
        byte[] name = file.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(name.length * 2);
        for (byte b : name) hex.append(String.format("%02x", b & 0xFF));
        Process process = new ProcessBuilder("python3", "-c", O_PATH_CHMOD, hex.toString(),
                Integer.toOctalString(mode)).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(readAll(in), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("chmod interrupted: " + file);
        }
        if (code == 3) throw new IOException("Refusing to chmod a non-regular file: " + file);
        if (code != 0) throw new IOException("chmod failed (" + code + "): " + file + " " + output);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    @Override
    public void unlink(File file) throws IOException {
        if (type(file) == Type.DIRECTORY) throw new IOException("Is a directory: " + file);
        Files.delete(file.toPath());
    }

    @Override
    public void rmdir(File dir) throws IOException {
        if (type(dir) != Type.DIRECTORY) throw new IOException("Not a directory: " + dir);
        Files.delete(dir.toPath());
    }

    @Override
    public void symlink(String target, File link) throws IOException {
        Files.createSymbolicLink(link.toPath(), Paths.get(target));
    }

    @Override
    public String readlink(File link) throws IOException {
        return Files.readSymbolicLink(link.toPath()).toString();
    }

    @Override
    public void hardlink(File existing, File link) throws IOException {
        Files.createLink(link.toPath(), existing.toPath());
    }

    @Override
    public void rename(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            throw new IOException("rename target exists: " + to, e);
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

    static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] order = {
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE};
        for (int i = 0; i < 9; i++) {
            if ((mode & (0400 >> i)) != 0) perms.add(order[i]);
        }
        return perms;
    }
}
