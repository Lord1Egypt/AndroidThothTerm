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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

public class SafeFileTreeTest {
    private static final Charset ASCII = Charset.forName("US-ASCII");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final TarballExtractorTest.JvmFileOps ops = new TarballExtractorTest.JvmFileOps();

    @Test
    public void removesReadOnlyDirectories() throws Exception {
        File root = temporaryFolder.newFolder("ro-root");
        File sub = new File(root, "sub");
        sub.mkdirs();
        Files.write(new File(sub, "f").toPath(), "x".getBytes(ASCII));
        Files.setPosixFilePermissions(sub.toPath(),
                PosixFilePermissions.fromString("r-xr-xr-x"));

        SafeFileTree.deleteTree(ops, root, root);

        assertFalse(root.exists());
    }

    @Test
    public void doesNotFollowSymlinksOutsideRoot() throws Exception {
        File base = temporaryFolder.newFolder("base");
        File root = new File(base, "staging");
        root.mkdirs();
        File outside = new File(base, "outside");
        outside.mkdirs();
        File keep = new File(outside, "keep");
        Files.write(keep.toPath(), "keep".getBytes(ASCII));
        Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath());

        SafeFileTree.deleteTree(ops, root, root);

        assertFalse(root.exists());
        assertTrue(outside.isDirectory());
        assertTrue(keep.isFile());
    }

    @Test(expected = IOException.class)
    public void refusesToDeleteOutsideRoot() throws Exception {
        File base = temporaryFolder.newFolder("containment");
        File root = new File(base, "staging");
        root.mkdirs();
        File outside = new File(base, "target");
        outside.mkdirs();

        SafeFileTree.deleteTree(ops, root, outside);
    }
}
