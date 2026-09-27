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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** {@link UploadFs} on java.nio.file, for JVM tests on a Linux host. */
class NioUploadFs implements UploadFs {
    /** Set to make every further write fail with ENOSPC after this many bytes. */
    long spaceLeft = Long.MAX_VALUE;
    long freeBytes = Long.MAX_VALUE / 2;
    final List<String> renames = new ArrayList<>();

    @Override
    public Kind lstat(String path) throws IOException {
        try {
            BasicFileAttributes a = Files.readAttributes(Paths.get(path), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (a.isDirectory()) return Kind.DIRECTORY;
            if (a.isRegularFile()) return Kind.FILE;
            return Kind.OTHER;
        } catch (NoSuchFileException e) {
            return Kind.MISSING;
        }
    }

    @Override
    public boolean writableDirectory(String path) {
        Path p = Paths.get(path);
        return Files.isWritable(p) && Files.isExecutable(p);
    }

    @Override
    public long freeBytes(String path) {
        return freeBytes;
    }

    @Override
    public void mkdir(String path, int mode) throws IOException {
        try {
            Files.createDirectory(Paths.get(path));
        } catch (FileAlreadyExistsException e) {
            throw new Failure(EEXIST, "exists");
        } catch (java.nio.file.AccessDeniedException e) {
            throw new Failure(13, "denied");
        }
        Files.setPosixFilePermissions(Paths.get(path), permissions(mode));
    }

    @Override
    public Sink create(String path, int mode) throws IOException {
        final FileChannel channel;
        try {
            channel = FileChannel.open(Paths.get(path), EnumSet.of(StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE), java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    permissions(0600)));
        } catch (FileAlreadyExistsException e) {
            throw new Failure(EEXIST, "exists");
        } catch (java.nio.file.AccessDeniedException e) {
            throw new Failure(13, "denied");
        }
        Files.setPosixFilePermissions(Paths.get(path), permissions(mode));
        return new Sink() {
            @Override
            public void write(byte[] data, int offset, int length) throws IOException {
                if (length > spaceLeft) throw new Failure(ENOSPC, "No space left on device");
                spaceLeft -= length;
                ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
                while (buffer.hasRemaining()) channel.write(buffer);
            }

            @Override
            public void finish() throws IOException {
                channel.force(true);
                channel.close();
            }

            @Override
            public void abort() {
                try {
                    channel.close();
                } catch (IOException ignore) {
                    // test double
                }
            }
        };
    }

    @Override
    public int renameNoReplace(String from, String to) {
        try {
            if (lstat(to) != Kind.MISSING) return EEXIST;
            Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.ATOMIC_MOVE);
            renames.add(to);
            return 0;
        } catch (IOException e) {
            return 5;
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Files.delete(Paths.get(path));
    }

    @Override
    public String[] list(String path) throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(Paths.get(path))) {
            for (Path p : s) names.add(p.getFileName().toString());
        }
        return names.toArray(new String[0]);
    }

    static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> set = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] order = {
                PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_READ,
        };
        for (int bit = 0; bit < 9; ++bit) {
            if ((mode & (1 << bit)) != 0) set.add(order[bit]);
        }
        return set;
    }
}
