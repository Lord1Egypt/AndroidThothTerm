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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adversarial extractor cases, independent of the test runner and of the
 * {@link FileOps} implementation: the JVM unit tests run them with
 * {@link JvmFileOps}, and the device instrumentation runs the very same cases
 * with {@link AndroidFileOps}, the code the APK uses.
 *
 * <p>Every case extracts into {@code <dir>/root} next to an OUTSIDE sentinel
 * tree in {@code <dir>/outside}. The sentinel is snapshotted (paths, types,
 * modes, link targets, content digests) before extraction and must be
 * identical afterwards, and nothing new may appear next to {@code root}.</p>
 */
public final class ExtractorSecurityCases {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public interface Case {
        void run(FileOps ops, File dir) throws Exception;
    }

    private ExtractorSecurityCases() {
    }

    /** Every case by name, in a stable order. */
    public static Map<String, Case> all() {
        Map<String, Case> cases = new LinkedHashMap<>();
        cases.put("danglingSymlinkThenFile", ExtractorSecurityCases::danglingSymlinkThenFile);
        cases.put("danglingAbsoluteSymlinkThenFile",
                ExtractorSecurityCases::danglingAbsoluteSymlinkThenFile);
        cases.put("danglingSymlinkThenHardlink", ExtractorSecurityCases::danglingSymlinkThenHardlink);
        cases.put("symlinkToOutsideFileThenFile", ExtractorSecurityCases::symlinkToOutsideFileThenFile);
        cases.put("symlinkToOutsideDirThenChildren",
                ExtractorSecurityCases::symlinkToOutsideDirThenChildren);
        cases.put("symlinkToOutsideDirThenDirectoryEntry",
                ExtractorSecurityCases::symlinkToOutsideDirThenDirectoryEntry);
        cases.put("nestedRelativeSymlinkEscape", ExtractorSecurityCases::nestedRelativeSymlinkEscape);
        cases.put("hardlinkTargetThroughSymlink", ExtractorSecurityCases::hardlinkTargetThroughSymlink);
        cases.put("hardlinkToSymlinkDoesNotFollow", ExtractorSecurityCases::hardlinkToSymlinkDoesNotFollow);
        cases.put("hardlinkTraversalRejected", ExtractorSecurityCases::hardlinkTraversalRejected);
        cases.put("traversalAndAbsoluteNamesRejected",
                ExtractorSecurityCases::traversalAndAbsoluteNamesRejected);
        cases.put("paxTraversalRejected", ExtractorSecurityCases::paxTraversalRejected);
        cases.put("literalBackslashNamesPreserved", ExtractorSecurityCases::literalBackslashNamesPreserved);
        cases.put("utf8NamesPreserved", ExtractorSecurityCases::utf8NamesPreserved);
        cases.put("malformedUtf8Rejected", ExtractorSecurityCases::malformedUtf8Rejected);
        cases.put("rootEntryAccepted", ExtractorSecurityCases::rootEntryAccepted);
        cases.put("fileOverDirectoryRejected", ExtractorSecurityCases::fileOverDirectoryRejected);
        cases.put("specialBitsMasked", ExtractorSecurityCases::specialBitsMasked);
        cases.put("legitimateLinksSupported", ExtractorSecurityCases::legitimateLinksSupported);
        cases.put("specialFilesSkipped", ExtractorSecurityCases::specialFilesSkipped);
        return cases;
    }

    // ---- cases -------------------------------------------------------------

    /** Sonnet's PoC: a dangling symlink, then a file at the same name. */
    static void danglingSymlinkThenFile(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .symlink("etc/x", "../../outside/NEW-FILE")
                .file("etc/x", "pwned")
                .build());
        f.assertOutsideUntouched();
        check(ops.type(new File(f.root, "etc/x")) == FileOps.Type.REGULAR,
                "the file replaced the symlink");
        checkEquals("pwned", f.read("etc/x"), "content");
        checkEquals(0L, x.rejectedEntries(), "rejected");
    }

    static void danglingAbsoluteSymlinkThenFile(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        File planted = new File(f.outside, "ABSOLUTE-NEW");
        f.extract(new TarBuilder()
                .symlink("etc/abs", planted.getAbsolutePath())
                .file("etc/abs", "pwned")
                .build());
        f.assertOutsideUntouched();
        check(!new File(f.outside, "ABSOLUTE-NEW").exists(), "nothing created through the link");
    }

    static void danglingSymlinkThenHardlink(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        f.extract(new TarBuilder()
                .file("usr/data", "payload")
                .symlink("usr/y", "../../outside/NEW-FROM-LINK")
                .hardlink("usr/y", "usr/data")
                .build());
        f.assertOutsideUntouched();
        check(ops.type(new File(f.root, "usr/y")) == FileOps.Type.REGULAR, "hardlink landed in root");
        checkEquals("payload", f.read("usr/y"), "content");
    }

    static void symlinkToOutsideFileThenFile(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        File victim = new File(f.outside, "victim");
        f.extract(new TarBuilder()
                .symlink("etc/z", victim.getAbsolutePath())
                .file("etc/z", "overwritten")
                .build());
        f.assertOutsideUntouched();
        checkEquals("overwritten", f.read("etc/z"), "content in root");
    }

    static void symlinkToOutsideDirThenChildren(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .file("a", "a")
                .symlink("usr/evil", f.outside.getAbsolutePath())
                .file("usr/evil/payload", "pwned")
                .dir("usr/evil/sub", 0777)
                .hardlink("usr/evil/hl", "a")
                .symlink("usr/evil/sl", "/")
                .build());
        f.assertOutsideUntouched();
        checkEquals(4L, x.rejectedEntries(), "every entry through the link is rejected");
    }

    static void symlinkToOutsideDirThenDirectoryEntry(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        f.extract(new TarBuilder()
                .symlink("d", f.outside.getAbsolutePath())
                .dir("d", 0700)
                .file("d/inside", "ok")
                .build());
        f.assertOutsideUntouched();
        check(ops.type(new File(f.root, "d")) == FileOps.Type.DIRECTORY,
                "the directory entry replaced the symlink with a real directory");
        checkEquals("ok", f.read("d/inside"), "child in the new directory");
    }

    static void nestedRelativeSymlinkEscape(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .dir("a", 0755)
                .symlink("a/b", "../..")
                .file("a/b/outside/victim", "pwned")
                .file("a/b/root-sibling", "pwned")
                .build());
        f.assertOutsideUntouched();
        checkEquals(2L, x.rejectedEntries(), "rejected");
    }

    static void hardlinkTargetThroughSymlink(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .symlink("lnk", f.outside.getAbsolutePath())
                .hardlink("stolen", "lnk/victim")
                .build());
        f.assertOutsideUntouched();
        check(ops.type(new File(f.root, "stolen")) == FileOps.Type.NONE, "no link to an outside file");
        checkEquals(1L, x.rejectedEntries(), "rejected");
    }

    static void hardlinkToSymlinkDoesNotFollow(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        File victim = new File(f.outside, "victim");
        f.extract(new TarBuilder()
                .symlink("s", victim.getAbsolutePath())
                .hardlink("h", "s")
                .file("h", "replaced")
                .build());
        f.assertOutsideUntouched();
        checkEquals("replaced", f.read("h"), "the file replaced the link, not its target");
    }

    static void hardlinkTraversalRejected(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .hardlink("usr/bin/link", "../outside/victim")
                .hardlink("usr/bin/abs", new File(f.outside, "victim").getAbsolutePath())
                .build());
        f.assertOutsideUntouched();
        checkEquals(2L, x.rejectedEntries(), "rejected");
        check(ops.type(new File(f.root, "usr/bin/link")) == FileOps.Type.NONE, "no link");
    }

    static void traversalAndAbsoluteNamesRejected(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .file("../outside/victim", "pwned")
                .file("a/../../outside/victim", "pwned")
                .file("/abs-evil", "pwned")
                .file("a\\..\\..\\outside\\victim", "pwned")
                .file("\\outside\\victim", "pwned")
                .file("..", "pwned")
                .build());
        f.assertOutsideUntouched();
        checkEquals(6L, x.rejectedEntries(), "rejected");
    }

    static void paxTraversalRejected(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .pax("../outside/victim", "pwned")
                .build());
        f.assertOutsideUntouched();
        checkEquals(1L, x.rejectedEntries(), "rejected");
        check(ops.type(new File(f.root, "placeholder")) == FileOps.Type.NONE,
                "the ustar fallback name is not used");
    }

    /** Codex's fix: systemd unit names with literal backslashes. */
    static void literalBackslashNamesPreserved(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        String unit = "usr/lib/systemd/system/system-systemd\\x2dcryptsetup.slice";
        TarballExtractor x = f.extract(new TarBuilder()
                .file(unit, "[Unit]\n")
                .hardlink("usr/lib/systemd/system/copy\\x2dlink.slice", unit)
                .build());
        f.assertOutsideUntouched();
        checkEquals("[Unit]\n", f.read(unit), "unit file");
        checkEquals("[Unit]\n", f.read("usr/lib/systemd/system/copy\\x2dlink.slice"),
                "hardlink to it");
        check(ops.type(new File(f.root, "usr/lib/systemd/system/system-systemd"))
                == FileOps.Type.NONE, "no directory made from the backslash");
        checkEquals(0L, x.rejectedEntries(), "rejected");
    }

    /** Debian trixie ships ca-certificates files with UTF-8 names. */
    static void utf8NamesPreserved(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        String name = "etc/ssl/certs/NetLock_Arany_=Class_Gold=_Főtanúsítvány.pem";
        String paxName = "usr/share/doc/العربية.txt";
        TarballExtractor x = f.extract(new TarBuilder()
                .file(name, "cert")
                .pax(paxName, "arabic")
                .build());
        f.assertOutsideUntouched();
        checkEquals("cert", f.read(name), "ustar UTF-8 name");
        checkEquals("arabic", f.read(paxName), "pax UTF-8 name");
        checkEquals(0L, x.rejectedEntries(), "rejected");
    }

    static void malformedUtf8Rejected(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        // 0xC0 0xAF is an overlong encoding of '/'; 0xFF is never valid.
        TarballExtractor x = f.extract(new TarBuilder()
                .rawNameFile(new byte[]{'a', (byte) 0xC0, (byte) 0xAF, '.', '.'}, "x")
                .rawNameFile(new byte[]{'b', (byte) 0xFF}, "x")
                .paxRaw(new byte[]{'.', '.', (byte) 0xC0, (byte) 0xAF, 'x'}, "x")
                .file("good", "ok")
                .build());
        f.assertOutsideUntouched();
        checkEquals(3L, x.rejectedEntries(), "rejected");
        checkEquals("ok", f.read("good"), "the stream stays aligned");
    }

    static void rootEntryAccepted(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .dir(".", 0755)
                .dir("./", 0755)
                .file("./etc/os-release", "ID=test\n")
                .build());
        f.assertOutsideUntouched();
        checkEquals(0L, x.rejectedEntries(), "the root entry is not a rejection");
        checkEquals("ID=test\n", f.read("etc/os-release"), "content");
    }

    static void fileOverDirectoryRejected(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .dir("usr", 0755)
                .file("usr/keep", "keep")
                .file("usr", "clobber")
                .build());
        f.assertOutsideUntouched();
        checkEquals(1L, x.rejectedEntries(), "rejected");
        checkEquals("keep", f.read("usr/keep"), "directory kept");
    }

    static void specialBitsMasked(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        f.extract(new TarBuilder()
                .file("usr/bin/suid", "x".getBytes(UTF8), 04755)
                .dir("tmp", 01777)
                .build());
        f.assertOutsideUntouched();
        checkEquals(0755, Fixture.mode(new File(f.root, "usr/bin/suid")), "setuid dropped");
        checkEquals(0777, Fixture.mode(new File(f.root, "tmp")), "sticky dropped");
    }

    static void legitimateLinksSupported(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .dir("usr/lib", 0755)
                .file("usr/lib/os-release", "NAME=x\n")
                .symlink("etc/os-release", "../usr/lib/os-release")
                .symlink("etc/localtime", "/usr/share/zoneinfo/UTC")
                .symlink("lib", "usr/lib")
                .hardlink("usr/lib/os-release.hl", "usr/lib/os-release")
                .dir("ro", 0555)
                .file("ro/child", "c")
                .build());
        f.assertOutsideUntouched();
        checkEquals(0L, x.rejectedEntries(), "rejected");
        checkEquals("../usr/lib/os-release", ops.readlink(new File(f.root, "etc/os-release")),
                "relative symlink kept verbatim");
        checkEquals("/usr/share/zoneinfo/UTC", ops.readlink(new File(f.root, "etc/localtime")),
                "absolute guest symlink kept verbatim");
        checkEquals("NAME=x\n", f.read("usr/lib/os-release.hl"), "hardlink content");
        checkEquals(0555, Fixture.mode(new File(f.root, "ro")), "final directory mode");
        checkEquals("c", f.read("ro/child"), "child of a read-only directory");
    }

    static void specialFilesSkipped(FileOps ops, File dir) throws Exception {
        Fixture f = new Fixture(ops, dir);
        TarballExtractor x = f.extract(new TarBuilder()
                .fifo("dev/fifo")
                .file("after", "ok")
                .build());
        f.assertOutsideUntouched();
        checkEquals(1L, x.skippedSpecialEntries(), "skipped");
        checkEquals("ok", f.read("after"), "content");
    }

    // ---- fixture -----------------------------------------------------------

    static final class Fixture {
        final FileOps ops;
        final File base;
        final File root;
        final File outside;
        private final Map<String, String> before;
        private final String[] siblingsBefore;

        Fixture(FileOps ops, File base) throws IOException {
            this.ops = ops;
            this.base = base;
            this.root = new File(base, "root");
            this.outside = new File(base, "outside");
            // The sentinel is made and observed with java.nio, never with the
            // operations under test.
            if (!base.isDirectory() && !base.mkdirs()) throw new IOException("mkdirs " + base);
            Files.createDirectory(outside.toPath());
            write(new File(outside, "victim"), "original", 0644);
            Files.createDirectory(new File(outside, "dir").toPath());
            write(new File(outside, "dir/inner"), "inner", 0600);
            Files.createSymbolicLink(new File(outside, "link").toPath(), Paths.get("victim"));
            before = snapshot(outside);
            siblingsBefore = sortedList(base);
        }

        TarballExtractor extract(byte[] tar) throws IOException {
            TarballExtractor extractor = new TarballExtractor(ops, root, null);
            extractor.extract(new ByteArrayInputStream(tar));
            return extractor;
        }

        void assertOutsideUntouched() throws IOException {
            Map<String, String> after = snapshot(outside);
            checkEquals(before.toString(), after.toString(), "OUTSIDE sentinel tree");
            java.util.TreeSet<String> expected =
                    new java.util.TreeSet<>(java.util.Arrays.asList(siblingsBefore));
            expected.add("root");
            java.util.TreeSet<String> actual =
                    new java.util.TreeSet<>(java.util.Arrays.asList(sortedList(base)));
            checkEquals(expected.toString(), actual.toString(),
                    "nothing new next to the extraction root");
        }

        String read(String rel) throws IOException {
            Path path = new File(root, rel).toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new AssertionError("not a regular file: " + rel);
            }
            return new String(Files.readAllBytes(path), UTF8);
        }

        private static void write(File file, String text, int mode) throws IOException {
            Files.write(file.toPath(), text.getBytes(UTF8), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            Files.setPosixFilePermissions(file.toPath(), JvmPermissions.of(mode));
        }

        private Map<String, String> snapshot(File top) throws IOException {
            Map<String, String> result = new TreeMap<>();
            walk(top, "", result);
            return result;
        }

        private void walk(File node, String rel, Map<String, String> out) throws IOException {
            Path path = node.toPath();
            String key = rel.isEmpty() ? "." : rel;
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            String type = attrs.isSymbolicLink() ? "SYMLINK" : attrs.isDirectory() ? "DIRECTORY"
                    : attrs.isRegularFile() ? "REGULAR" : "OTHER";
            switch (type) {
                case "DIRECTORY":
                    out.put(key, "dir " + Integer.toOctalString(mode(node)) + " mtime "
                            + attrs.lastModifiedTime().toMillis());
                    String[] names = node.list();
                    if (names != null) {
                        java.util.Arrays.sort(names);
                        for (String name : names) {
                            walk(new File(node, name), rel.isEmpty() ? name : rel + "/" + name, out);
                        }
                    }
                    break;
                case "SYMLINK":
                    out.put(key, "link " + Files.readSymbolicLink(path));
                    break;
                case "REGULAR":
                    out.put(key, "file " + Integer.toOctalString(mode(node)) + " "
                            + digest(Files.readAllBytes(path)) + " mtime "
                            + attrs.lastModifiedTime().toMillis());
                    break;
                default:
                    out.put(key, type);
            }
        }

        static int mode(File file) throws IOException {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                    java.nio.file.Files.getPosixFilePermissions(file.toPath(),
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
            int mode = 0;
            for (java.nio.file.attribute.PosixFilePermission p : perms) {
                mode |= 0400 >> p.ordinal();
            }
            return mode;
        }

        private static String[] sortedList(File dir) {
            String[] names = dir.list();
            if (names == null) names = new String[0];
            java.util.Arrays.sort(names);
            return names;
        }
    }

    // ---- helpers -----------------------------------------------------------

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    static String digest(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x & 0xFF));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    static void checkEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Mode bits to java.nio permissions (Android API 26 has java.nio.file). */
    static final class JvmPermissions {
        static java.util.Set<java.nio.file.attribute.PosixFilePermission> of(int mode) {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                    java.util.EnumSet.noneOf(java.nio.file.attribute.PosixFilePermission.class);
            for (java.nio.file.attribute.PosixFilePermission p
                    : java.nio.file.attribute.PosixFilePermission.values()) {
                if ((mode & (0400 >> p.ordinal())) != 0) perms.add(p);
            }
            return perms;
        }
    }
}
