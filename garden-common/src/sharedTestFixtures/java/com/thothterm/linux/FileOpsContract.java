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
        c.put("chmodOfUnreadableFileStillMasksSetuid", FileOpsContract::chmodOfUnreadableFileStillMasksSetuid);
        c.put("chmodRefusesSymlinkToUnreadableTarget", FileOpsContract::chmodRefusesSymlinkToUnreadableTarget);
        c.put("chmodWorksOnDirectory", FileOpsContract::chmodWorksOnDirectory);
        c.put("chmodWorksOnUnreadableDirectoryKeepingSticky",
                FileOpsContract::chmodWorksOnUnreadableDirectoryKeepingSticky);
        c.put("chmodRefusesSymlinkToDirectory", FileOpsContract::chmodRefusesSymlinkToDirectory);
        c.put("chmodRefusesFifoWithoutOpeningIt", FileOpsContract::chmodRefusesFifoWithoutOpeningIt);
        c.put("chmodSwapRacesNeverReachTheTarget", FileOpsContract::chmodSwapRacesNeverReachTheTarget);
        c.put("mkdirRefusesDanglingSymlink", FileOpsContract::mkdirRefusesDanglingSymlink);
        c.put("unlinkRemovesLinkNotTarget", FileOpsContract::unlinkRemovesLinkNotTarget);
        c.put("unlinkOfDanglingSymlinkRemovesTheLink", FileOpsContract::unlinkOfDanglingSymlinkRemovesTheLink);
        c.put("unlinkOfSymlinkToDirectoryRemovesTheLink",
                FileOpsContract::unlinkOfSymlinkToDirectoryRemovesTheLink);
        c.put("unlinkRefusesADirectory", FileOpsContract::unlinkRefusesADirectory);
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
        eq(0755, ops.permissions(new File(dir, "f")), "setuid masked");
        java.nio.file.Files.createDirectory(dir.toPath().resolve("d"));
        ops.chmodNoFollow(new File(dir, "d"), 03777);
        eq(01777, ops.permissions(new File(dir, "d")), "directory keeps sticky, loses setgid");
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

    /** The owner-unreadable path (the EACCES fallback) applies the same masks. */
    static void chmodOfUnreadableFileStillMasksSetuid(FileOps ops, File dir) throws Exception {
        Path f = write(dir, "wo", "x");
        Files.setPosixFilePermissions(f, ExtractorSecurityCases.JvmPermissions.of(0200));
        ops.chmodNoFollow(f.toFile(), 06755);
        eq(0755, ExtractorSecurityCases.Fixture.mode(f.toFile()), "setuid and setgid masked");
        Files.setPosixFilePermissions(f, ExtractorSecurityCases.JvmPermissions.of(0000));
        ops.chmodNoFollow(f.toFile(), 0110);
        eq(0110, ExtractorSecurityCases.Fixture.mode(f.toFile()), "0000 -> 0110");
    }

    /**
     * A symlink is refused even when its target is one the owner cannot read,
     * the case where a no-follow open fails with EACCES before it could fail
     * with ELOOP: the target keeps its mode.
     */
    static void chmodRefusesSymlinkToUnreadableTarget(FileOps ops, File dir) throws Exception {
        Path target = write(dir, "target", "t");
        Files.setPosixFilePermissions(target, ExtractorSecurityCases.JvmPermissions.of(0200));
        Files.createSymbolicLink(dir.toPath().resolve("link"), target);
        try {
            ops.chmodNoFollow(new File(dir, "link"), 0777);
            throw new AssertionError("chmod accepted a symlink");
        } catch (IOException expected) {
            // a symlink is never chmodded
        }
        eq(0200, ExtractorSecurityCases.Fixture.mode(target.toFile()), "target mode unchanged");
        ops.setMode(new File(dir, "link"), 0777);
        eq(0200, ExtractorSecurityCases.Fixture.mode(target.toFile()), "target mode unchanged");
    }

    static void chmodWorksOnDirectory(FileOps ops, File dir) throws Exception {
        Path d = Files.createDirectory(dir.toPath().resolve("d"));
        Files.setPosixFilePermissions(d, ExtractorSecurityCases.JvmPermissions.of(0755));
        ops.chmodNoFollow(d.toFile(), 0700);
        eq(0700, ExtractorSecurityCases.Fixture.mode(d.toFile()), "directory mode applied");
    }

    /** A directory its owner cannot list (0300) still takes a mode, sticky included. */
    static void chmodWorksOnUnreadableDirectoryKeepingSticky(FileOps ops, File dir) throws Exception {
        Path d = Files.createDirectory(dir.toPath().resolve("d"));
        Files.setPosixFilePermissions(d, ExtractorSecurityCases.JvmPermissions.of(0300));
        ops.chmodNoFollow(d.toFile(), 03777);
        // lstat's mode; java.nio's PosixFilePermission has no sticky bit.
        eq(01777, ops.permissions(d.toFile()), "sticky kept, setgid dropped");
        Files.setPosixFilePermissions(d, ExtractorSecurityCases.JvmPermissions.of(0300));
        ops.chmodNoFollow(d.toFile(), 0755);
        eq(0755, ExtractorSecurityCases.Fixture.mode(d.toFile()), "0300 -> 0755");
    }

    static void chmodRefusesSymlinkToDirectory(FileOps ops, File dir) throws Exception {
        Path d = Files.createDirectory(dir.toPath().resolve("d"));
        Files.setPosixFilePermissions(d, ExtractorSecurityCases.JvmPermissions.of(0700));
        Files.createSymbolicLink(dir.toPath().resolve("link"), d);
        try {
            ops.chmodNoFollow(new File(dir, "link"), 01777);
            throw new AssertionError("chmod accepted a symlink to a directory");
        } catch (IOException expected) {
            // refused
        }
        eq(0700, ExtractorSecurityCases.Fixture.mode(d.toFile()), "target directory mode unchanged");
    }

    static void chmodRefusesFifoWithoutOpeningIt(FileOps ops, File dir) throws Exception {
        File fifo = new File(dir, "fifo");
        Process mkfifo = new ProcessBuilder("mkfifo", "-m", "600", fifo.getAbsolutePath()).start();
        eq(0, mkfifo.waitFor(), "mkfifo");
        try {
            ops.chmodNoFollow(fifo, 0666);
            throw new AssertionError("chmod accepted a FIFO");
        } catch (IOException expected) {
            // a special file is refused; opening it for I/O would block
        }
        eq(0600, ops.permissions(fifo), "FIFO mode unchanged");
    }

    /**
     * Codex QA round 3: a path swapped for a symlink while chmod runs must
     * never change the symlink's target. A second thread keeps replacing the
     * path -- an owner-unreadable regular file (so the no-read-permission
     * path runs), a symlink to an outside sentinel, a symlink to an outside
     * directory, nothing -- while this thread chmods it. After every attempt
     * both outside objects keep their modes; any attempt may fail, none may
     * follow.
     */
    static void chmodSwapRacesNeverReachTheTarget(FileOps ops, File dir) throws Exception {
        final Path root = dir.toPath();
        Path outsideDir = Files.createDirectory(root.resolve("outside"));
        final Path sentinel = write(outsideDir.toFile(), "sentinel", "keep");
        Files.setPosixFilePermissions(sentinel, ExtractorSecurityCases.JvmPermissions.of(0600));
        final Path victimDir = Files.createDirectory(outsideDir.resolve("victim-dir"));
        Files.setPosixFilePermissions(victimDir, ExtractorSecurityCases.JvmPermissions.of(0700));
        Path tree = Files.createDirectory(root.resolve("tree"));
        final Path target = tree.resolve("p");
        final Path[] holds = {
                write(tree.toFile(), "hold-file", "x"),
                tree.resolve("hold-link"),
                tree.resolve("hold-dirlink"),
        };
        Files.setPosixFilePermissions(holds[0], ExtractorSecurityCases.JvmPermissions.of(0200));
        Files.createSymbolicLink(holds[1], sentinel);
        Files.createSymbolicLink(holds[2], victimDir);

        final java.util.concurrent.atomic.AtomicBoolean stop =
                new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicReference<Throwable> swapError =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread swapper = new Thread(() -> {
            java.util.Random random = new java.util.Random();
            int i = 0;
            try {
                while (!stop.get()) {
                    Path in = holds[i % holds.length];
                    Files.move(in, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    // Mostly swap at once, so an operation straddles changes;
                    // half the time the regular file stays up to 80 ms, so whole
                    // operations also run, and finish, on it.
                    if (in == holds[0] && random.nextBoolean()) {
                        java.util.concurrent.locks.LockSupport.parkNanos(
                                random.nextInt(80_000_000));
                    } else {
                        Thread.yield();
                    }
                    Files.move(target, in, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    i++;
                }
            } catch (Throwable t) {
                swapError.set(t);
            }
        }, "chmod-swapper");
        swapper.start();
        int applied = 0;
        try {
            // At least 200 attempts and 3 that reached the regular file
            // through the no-read-permission path; at most 15 s.
            long deadline = System.nanoTime() + 15_000_000_000L;
            for (int attempt = 0; (attempt < 200 || applied < 3) && System.nanoTime() < deadline;
                    attempt++) {
                try {
                    ops.chmodNoFollow(target.toFile(), attempt % 2 == 0 ? 0000 : 0200);
                    applied++;
                } catch (IOException raced) {
                    // refused or the path was briefly absent; never followed
                }
                eq(0600, ExtractorSecurityCases.Fixture.mode(sentinel.toFile()),
                        "outside sentinel mode after attempt " + attempt);
                eq(0700, ExtractorSecurityCases.Fixture.mode(victimDir.toFile()),
                        "outside directory mode after attempt " + attempt);
            }
        } finally {
            stop.set(true);
            swapper.join();
        }
        if (swapError.get() != null) throw new AssertionError("swapper failed", swapError.get());
        eq("keep", read(outsideDir.toFile(), "sentinel"), "outside sentinel content");
        // The regular file did take modes in between: the race really ran
        // through the no-read-permission path, not only through refusals.
        if (applied < 3) throw new AssertionError("only " + applied + " chmods reached the regular file");
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

    static void unlinkOfDanglingSymlinkRemovesTheLink(FileOps ops, File dir) throws Exception {
        Files.createSymbolicLink(dir.toPath().resolve("dangling"), Paths.get("nowhere"));
        ops.unlink(new File(dir, "dangling"));
        eq(false, Files.exists(dir.toPath().resolve("dangling"), LinkOption.NOFOLLOW_LINKS), "link gone");
    }

    static void unlinkOfSymlinkToDirectoryRemovesTheLink(FileOps ops, File dir) throws Exception {
        Path real = Files.createDirectory(dir.toPath().resolve("real"));
        write(real.toFile(), "keep", "k");
        Files.createSymbolicLink(dir.toPath().resolve("link"), real);
        ops.unlink(new File(dir, "link"));
        eq(false, Files.exists(dir.toPath().resolve("link"), LinkOption.NOFOLLOW_LINKS), "link gone");
        eq(true, Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS), "target directory kept");
        eq("k", read(real.toFile(), "keep"), "target contents kept");
    }

    static void unlinkRefusesADirectory(FileOps ops, File dir) throws Exception {
        Path empty = Files.createDirectory(dir.toPath().resolve("empty"));
        try {
            ops.unlink(empty.toFile());
            throw new AssertionError("unlink removed a directory");
        } catch (IOException expected) {
            // a directory is never unlinked (remove(3) would rmdir it)
        }
        eq(true, Files.isDirectory(empty, LinkOption.NOFOLLOW_LINKS), "directory kept");
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
