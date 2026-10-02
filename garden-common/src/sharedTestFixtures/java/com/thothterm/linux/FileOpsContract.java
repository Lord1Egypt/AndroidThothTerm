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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The no-follow contract every {@link FileOps} must meet, written once and run
 * against {@link JvmFileOps} on the JVM and {@link AndroidFileOps} on a device.
 * The state is set up and inspected with java.nio, not with the operations
 * under test.
 */
public final class FileOpsContract {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private FileOpsContract() {
    }

    public static Map<String, ExtractorSecurityCases.Case> all() {
        Map<String, ExtractorSecurityCases.Case> c = new LinkedHashMap<>();
        c.put("typeDoesNotFollow", FileOpsContract::typeDoesNotFollow);
        c.put("createNewRefusesDanglingSymlink", FileOpsContract::createNewRefusesDanglingSymlink);
        c.put("createNewRefusesExistingFile", FileOpsContract::createNewRefusesExistingFile);
        c.put("createNewAppliesMaskedModeOnClose", FileOpsContract::createNewAppliesMaskedModeOnClose);
        c.put("chmodRefusesSymlinkAndLeavesTarget", FileOpsContract::chmodRefusesSymlinkAndLeavesTarget);
        c.put("chmodWorksOnUnreadableFile", FileOpsContract::chmodWorksOnUnreadableFile);
        c.put("mkdirRefusesDanglingSymlink", FileOpsContract::mkdirRefusesDanglingSymlink);
        c.put("unlinkRemovesLinkNotTarget", FileOpsContract::unlinkRemovesLinkNotTarget);
        c.put("rmdirRefusesSymlinkToDirectory", FileOpsContract::rmdirRefusesSymlinkToDirectory);
        c.put("openNoFollowRefusesSymlink", FileOpsContract::openNoFollowRefusesSymlink);
        c.put("symlinkRefusesExisting", FileOpsContract::symlinkRefusesExisting);
        c.put("renameMovesTheEntryItself", FileOpsContract::renameMovesTheEntryItself);
        return c;
    }

    static void typeDoesNotFollow(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "t");
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        Files.createSymbolicLink(dir.toPath().resolve("dangling"), Paths.get("missing"));
        Files.createSymbolicLink(dir.toPath().resolve("dirlink"), dir.toPath());
        eq(FileOps.Type.SYMLINK, ops.type(new File(dir, "link")), "link");
        eq(FileOps.Type.SYMLINK, ops.type(new File(dir, "dangling")), "dangling");
        eq(FileOps.Type.SYMLINK, ops.type(new File(dir, "dirlink")), "dirlink");
        eq(FileOps.Type.REGULAR, ops.type(target.toFile()), "file");
        eq(FileOps.Type.DIRECTORY, ops.type(dir), "dir");
        eq(FileOps.Type.NONE, ops.type(new File(dir, "absent")), "absent");
        eq(true, ops.exists(new File(dir, "dangling")), "a dangling link exists");
        eq(false, ops.isDirectory(new File(dir, "dirlink")), "a link to a dir is not a dir");
    }

    static void createNewRefusesDanglingSymlink(FileOps ops, File dir) throws Exception {
        Path victim = dir.toPath().resolve("victim-not-created");
        Files.createSymbolicLink(dir.toPath().resolve("trap"), victim);
        try {
            ops.createNew(new File(dir, "trap"), 0644).close();
            throw new AssertionError("createNew followed or replaced a dangling symlink");
        } catch (IOException expected) {
            // O_EXCL|O_NOFOLLOW
        }
        eq(false, Files.exists(victim, LinkOption.NOFOLLOW_LINKS), "nothing at the link target");
    }

    static void createNewRefusesExistingFile(FileOps ops, File dir) throws Exception {
        write(dir, "existing", "keep");
        try {
            ops.createNew(new File(dir, "existing"), 0644).close();
            throw new AssertionError("createNew truncated an existing file");
        } catch (IOException expected) {
            // O_EXCL
        }
        eq("keep", read(dir, "existing"), "content kept");
    }

    static void createNewAppliesMaskedModeOnClose(FileOps ops, File dir) throws Exception {
        OutputStream out = ops.createNew(new File(dir, "f"), 04755);
        out.write("x".getBytes(UTF8));
        out.close();
        eq(0755, ExtractorSecurityCases.Fixture.mode(new File(dir, "f")), "setuid masked");
    }

    static void chmodRefusesSymlinkAndLeavesTarget(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "t");
        Files.setPosixFilePermissions(target, ExtractorSecurityCases.JvmPermissions.of(0600));
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        try {
            ops.chmodNoFollow(new File(dir, "link"), 0777);
            throw new AssertionError("chmod accepted a symlink");
        } catch (IOException expected) {
            // ELOOP
        }
        eq(0600, ExtractorSecurityCases.Fixture.mode(target.toFile()), "target mode unchanged");
        ops.setMode(new File(dir, "link"), 0777); // best effort: still never follows
        eq(0600, ExtractorSecurityCases.Fixture.mode(target.toFile()), "target mode unchanged");
    }

    static void chmodWorksOnUnreadableFile(FileOps ops, File dir) throws Exception {
        Path f = write(dir, "wo", "x");
        Files.setPosixFilePermissions(f, ExtractorSecurityCases.JvmPermissions.of(0200));
        ops.chmodNoFollow(f.toFile(), 0640);
        eq(0640, ExtractorSecurityCases.Fixture.mode(f.toFile()), "mode applied");
    }

    static void mkdirRefusesDanglingSymlink(FileOps ops, File dir) throws Exception {
        Path victim = dir.toPath().resolve("victim-dir");
        Files.createSymbolicLink(dir.toPath().resolve("trap"), victim);
        try {
            ops.mkdir(new File(dir, "trap"), 0755);
            throw new AssertionError("mkdir went through a dangling symlink");
        } catch (IOException expected) {
            // EEXIST
        }
        eq(false, Files.exists(victim, LinkOption.NOFOLLOW_LINKS), "no directory at the target");
    }

    static void unlinkRemovesLinkNotTarget(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "t");
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        ops.unlink(new File(dir, "link"));
        eq(false, Files.exists(dir.toPath().resolve("link"), LinkOption.NOFOLLOW_LINKS), "link gone");
        eq("t", read(dir, "target"), "target kept");
    }

    static void rmdirRefusesSymlinkToDirectory(FileOps ops, File dir) throws Exception {
        Path real = Files.createDirectory(dir.toPath().resolve("real"));
        Files.createSymbolicLink(dir.toPath().resolve("link"), real);
        try {
            ops.rmdir(new File(dir, "link"));
            throw new AssertionError("rmdir accepted a symlink");
        } catch (IOException expected) {
            // not a directory
        }
        eq(true, Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS), "target directory kept");
    }

    static void openNoFollowRefusesSymlink(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "secret");
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        try {
            InputStream in = ops.openNoFollow(new File(dir, "link"));
            in.close();
            throw new AssertionError("openNoFollow followed a symlink");
        } catch (IOException expected) {
            // ELOOP
        }
    }

    static void symlinkRefusesExisting(FileOps ops, File dir) throws Exception {
        write(dir, "existing", "keep");
        try {
            ops.symlink("/", new File(dir, "existing"));
            throw new AssertionError("symlink replaced an existing file");
        } catch (IOException expected) {
            // EEXIST
        }
        eq("keep", read(dir, "existing"), "content kept");
    }

    static void renameMovesTheEntryItself(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "t");
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        ops.rename(new File(dir, "link"), new File(dir, "moved"));
        eq(true, Files.isSymbolicLink(dir.toPath().resolve("moved")), "the link moved");
        eq("t", read(dir, "target"), "target untouched");
    }

    private static Path write(File dir, String name, String text) throws IOException {
        return Files.write(dir.toPath().resolve(name), text.getBytes(UTF8));
    }

    private static String read(File dir, String name) throws IOException {
        return new String(Files.readAllBytes(dir.toPath().resolve(name)), UTF8);
    }

    private static void eq(Object expected, Object actual, String what) {
        ExtractorSecurityCases.checkEquals(expected, actual, what);
    }
}
