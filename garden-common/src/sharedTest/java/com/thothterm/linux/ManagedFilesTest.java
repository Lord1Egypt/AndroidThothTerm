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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ManagedFilesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final JvmFileOps ops = new JvmFileOps();

    @Test
    public void guestSymlinksResolveInsideTheRootfs() throws Exception {
        File root = temporaryFolder.newFolder("root");
        Files.createDirectories(new File(root, "usr/bin").toPath());
        Files.createSymbolicLink(new File(root, "bin").toPath(), Paths.get("usr/bin"));
        Files.createSymbolicLink(new File(root, "usr/bin/abs").toPath(), Paths.get("/usr/bin/x"));
        Files.createSymbolicLink(new File(root, "usr/bin/up").toPath(),
                Paths.get("../../../../../../x"));
        Files.createSymbolicLink(new File(root, "loop").toPath(), Paths.get("loop"));

        assertEquals(new File(root, "usr/bin/bash"),
                GuestPaths.resolve(ops, root, "/bin/bash", true));
        assertEquals(new File(root, "usr/bin/x"),
                GuestPaths.resolve(ops, root, "/bin/abs", true));
        assertEquals(new File(root, "bin"), GuestPaths.resolve(ops, root, "/bin", false));
        assertEquals("never above the rootfs", new File(root, "x"),
                GuestPaths.resolve(ops, root, "/usr/bin/up", true));
        assertNull(GuestPaths.resolve(ops, root, "/loop", true));
    }

    @Test
    public void writesThroughAGuestSymlinkInsideTheRootfs() throws Exception {
        File root = temporaryFolder.newFolder("dotfiles");
        File home = new File(root, "home/thoth");
        Files.createDirectories(new File(home, "dotfiles").toPath());
        Files.write(new File(home, "dotfiles/bashrc").toPath(), "mine\n".getBytes(StandardCharsets.UTF_8));
        // An absolute guest link: on Android it would point at /home/thoth/...
        Files.createSymbolicLink(new File(home, ".bashrc").toPath(),
                Paths.get("/home/thoth/dotfiles/bashrc"));

        File bashrc = new File(home, ".bashrc");
        String text = ManagedFiles.read(ops, root, bashrc);
        assertEquals("mine\n", text);
        assertTrue(ManagedFiles.writeIfChanged(ops, root, bashrc, text + "managed\n", -1));

        assertTrue("the user's link is kept", Files.isSymbolicLink(bashrc.toPath()));
        assertEquals("mine\nmanaged\n", new String(Files.readAllBytes(
                new File(home, "dotfiles/bashrc").toPath()), StandardCharsets.UTF_8));
        assertFalse(ManagedFiles.writeIfChanged(ops, root, bashrc, "mine\nmanaged\n", -1));
    }

    @Test
    public void neverWritesOutsideTheRootfs() throws Exception {
        File base = temporaryFolder.newFolder("escape");
        File root = new File(base, "root");
        File outside = new File(base, "outside.txt");
        Files.write(outside.toPath(), "outside".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(new File(root, "etc").toPath());
        Files.createSymbolicLink(new File(root, "etc/hosts").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "etc/up").toPath(), Paths.get("../../outside.txt"));

        ManagedFiles.writeIfChanged(ops, root, new File(root, "etc/hosts"), "127.0.0.1\n", -1);
        ManagedFiles.writeIfChanged(ops, root, new File(root, "etc/up"), "127.0.0.1\n", -1);

        assertEquals("outside", new String(Files.readAllBytes(outside.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void largeMultibyteFilesAreNotCorrupted() throws Exception {
        File root = temporaryFolder.newFolder("utf8");
        File file = new File(root, "home/thoth/.bashrc");
        Files.createDirectories(file.getParentFile().toPath());
        StringBuilder text = new StringBuilder();
        // Two-byte characters across every 8 KiB boundary.
        for (int i = 0; i < 20000; i++) text.append(i % 2 == 0 ? 'a' : 'ع');
        Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(text.toString(), ManagedFiles.read(ops, root, file));
        assertFalse(ManagedFiles.writeIfChanged(ops, root, file, text.toString(), -1));
    }

    @Test
    public void invalidUtf8IsLeftExactlyAsItIs() throws Exception {
        File root = temporaryFolder.newFolder("latin1");
        File file = new File(root, "etc/passwd");
        Files.createDirectories(file.getParentFile().toPath());
        byte[] bytes = {'r', 'o', 'o', 't', ':', (byte) 0xE9, '\n'};
        Files.write(file.toPath(), bytes);
        try {
            ManagedFiles.read(ops, root, file);
            fail("not valid UTF-8 must not be managed");
        } catch (ManagedFiles.UnmanageableException expected) {
            // left alone
        }
        assertArrayEquals(bytes, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void keepsTheModeOfARewrittenFileAndAppliesARequestedOne() throws Exception {
        File root = temporaryFolder.newFolder("modes");
        File file = new File(root, "etc/sudoers.d/thoth");
        assertTrue(ManagedFiles.writeIfChanged(ops, root, file, "a\n", 0440));
        assertEquals(0440, ops.permissions(file));
        File shadow = new File(root, "etc/shadow");
        Files.write(shadow.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        ops.chmodNoFollow(shadow, 0600);
        ManagedFiles.writeIfChanged(ops, root, shadow, "y", -1);
        assertEquals(0600, ops.permissions(shadow));
        assertFalse("no temporary file left", new File(root, "etc/.shadow.thothterm-new").exists());
    }

    @Test
    public void writesIntoAnOwnerReadOnlyDirectoryAndRestoresIt() throws Exception {
        File root = temporaryFolder.newFolder("ro-dir");
        File dir = new File(root, "etc/ro");
        Files.createDirectories(dir.toPath());
        File file = new File(dir, "conf");
        Files.write(file.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        ops.chmodNoFollow(dir, 0555);
        try {
            assertTrue(ManagedFiles.writeIfChanged(ops, root, file, "new", -1));
            assertEquals("new", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            assertEquals("directory mode restored", 0555, ops.permissions(dir));
        } finally {
            ops.chmodNoFollow(dir, 0755);
        }
    }
}
