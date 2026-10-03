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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * {@link ObjectInspector} on a Linux JVM. java.nio has no fstat, so the held
 * descriptor is stat'ed through {@code /proc/self/fd/N}, a magic link to that
 * exact open object; N comes from {@link FileDescriptor}'s private field,
 * which needs {@code --add-opens java.base/java.io=ALL-UNNAMED} (set by the
 * Gradle unit tests and host-gate.sh). Without it every pin fails: nothing is
 * verified by assumption.
 */
public final class JvmObjectInspector implements ObjectInspector {
    @Override
    public String identity(File file) throws IOException {
        return identityOf(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static String identityOf(Path path, LinkOption... options) throws IOException {
        Object dev = Files.getAttribute(path, "unix:dev", options);
        Object ino = Files.getAttribute(path, "unix:ino", options);
        return dev + ":" + ino;
    }

    @Override
    public Pin pin(File file) throws IOException {
        final FileInputStream in = new FileInputStream(file);
        final Path self;
        try {
            self = Paths.get("/proc/self/fd/" + descriptorNumber(in.getFD()));
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
        final FileChannel channel = in.getChannel();
        return new Pin() {
            @Override
            public String identity() throws IOException {
                // Following the magic link is fstat of the held descriptor.
                return identityOf(self);
            }

            @Override
            public long size() throws IOException {
                return channel.size();
            }

            @Override
            public String sha256() throws IOException {
                MessageDigest digest = sha256Digest();
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                long position = 0;
                int read;
                while ((read = channel.read(buffer, position)) > 0) {
                    digest.update(buffer.array(), 0, read);
                    position += read;
                    buffer.clear();
                }
                return RootfsArchive.toHex(digest.digest());
            }

            @Override
            public void close() throws IOException {
                in.close();
            }
        };
    }

    private static int descriptorNumber(FileDescriptor fd) throws IOException {
        try {
            Field field = FileDescriptor.class.getDeclaredField("fd");
            field.setAccessible(true);
            return field.getInt(fd);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("Cannot read the descriptor number (run the JVM with"
                    + " --add-opens java.base/java.io=ALL-UNNAMED): " + e, e);
        }
    }

    static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
