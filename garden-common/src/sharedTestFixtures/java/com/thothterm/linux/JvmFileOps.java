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
     * The same two paths as {@link AndroidFileOps#chmodNoFollow}. First
     * {@code fchmod} on an {@code O_NOFOLLOW} descriptor: OpenJDK's
     * {@code "unix:mode"} with {@code NOFOLLOW_LINKS} is exactly
     * {@code open(O_RDONLY|O_NOFOLLOW)} + {@code fchmod}
     * ({@code UnixFileAttributeViews.Posix.setMode},
     * {@code UnixPath.openForAttributeAccess}); a symlink fails with ELOOP.
     * That open needs read permission, so a file or directory its owner
     * cannot read (0200, 0300, 0110) fails with EACCES although chmod(2)
     * itself needs none. Then, and only for EACCES, {@code chmod(2)} on the
     * path, after {@code lstat} has just said it is that same regular file or
     * directory and not a symlink; the tree has a single writer, this thread.
     * The "unix:mode" attribute carries the sticky bit; PosixFilePermission
     * cannot.
     */
    @Override
    public void chmodNoFollow(File file, int mode) throws IOException {
        Type type = type(file);
        if (type != Type.REGULAR && type != Type.DIRECTORY) {
            throw new IOException("Refusing to chmod " + type + ": " + file);
        }
        int masked = mode & (type == Type.DIRECTORY ? DIRECTORY_MODE_MASK : FILE_MODE_MASK);
        Path path = file.toPath();
        try {
            Files.setAttribute(path, "unix:mode", masked, LinkOption.NOFOLLOW_LINKS);
        } catch (AccessDeniedException unreadable) {
            if (type(file) != type) {
                throw new IOException("Refusing to chmod: " + file + " changed while being changed");
            }
            // chmod(2): follows a final symlink, which lstat just ruled out.
            Files.setAttribute(path, "unix:mode", masked);
        }
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
