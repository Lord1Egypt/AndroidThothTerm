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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * The extractor gate, as one runner-independent routine: every adversarial
 * case, the FileOps contract, then the real pinned archive through
 * {@link RootfsArchive} (the app's own extract-and-verify path) compared
 * against the independent manifest. The host tool runs it with
 * {@link JvmFileOps}; the device instrumentation runs it with
 * {@link AndroidFileOps}, which is what proves the APK.
 */
public final class ExtractorGate {
    public interface Report {
        void line(String text);
    }

    private ExtractorGate() {
    }

    /** @return the number of failures */
    public static int securityCases(FileOps ops, File work, Report report) {
        int failures = 0;
        failures += run("case", ExtractorSecurityCases.all(), ops, work, report);
        failures += run("contract", FileOpsContract.all(), ops, work, report);
        return failures;
    }

    /**
     * Extracts the real archive and compares the tree with the independent
     * manifest: path, type, mode, size and symlink target from {@code lstat}
     * and {@code readlink}, content from the SHA-256 of the bytes the
     * extractor wrote, recorded while they were written
     * ({@link RecordingFileOps}). Nothing is reopened that its owner cannot
     * read, and no mode is ever changed to look: Arch's 0110
     * {@code dbus-daemon-launch-helper} stays 0110. Every file the owner can
     * read is also re-read from disk and must match its record; every real
     * hard link must share its target's inode.
     *
     * @return the number of failures (0 or 1)
     */
    public static int realArchive(FileOps ops, File archive, String sha256, File manifest,
                                  File staging, Report report) {
        try {
            if (staging.exists()) SafeFileTree.deleteTree(ops, staging, staging);
            RecordingFileOps recording = new RecordingFileOps(ops);
            RootfsArchive.Result result;
            InputStream in = new FileInputStream(archive);
            try {
                result = RootfsArchive.extract(in, staging, recording, null);
            } finally {
                in.close();
            }
            report.line("INFO extract " + result.summary() + " sha256=" + result.sha256);
            RootfsArchive.verify(result, sha256);
            report.line("PASS archive verified: sha256 matches, 0 rejected entries");
            List<String> expected = TreeManifest.read(manifest);
            ContentCheck content = new ContentCheck(recording, ops);
            List<String> actual = TreeManifest.of(staging, ops::permissions, content);
            List<String> diff = TreeManifest.diff(expected, actual, 40);
            if (!diff.isEmpty()) {
                for (String line : diff) report.line("DIFF " + line);
                report.line("FAIL tree differs from the independent manifest ("
                        + expected.size() + " expected, " + actual.size() + " extracted)");
                return 1;
            }
            int links = 0;
            for (RecordingFileOps.Link link : recording.links()) {
                if (!sameInode(link.existing, link.link)) {
                    report.line("FAIL hard link is not its target's inode: " + link.link);
                    return 1;
                }
                links++;
            }
            report.line("PASS extracted tree matches the independent manifest: "
                    + actual.size() + " paths (" + result.hardlinkFallbacks
                    + " hardlinks materialized as copies)");
            report.line("PASS content of " + content.files + " regular files = the bytes the"
                    + " extractor wrote (SHA-256 recorded during extraction); " + content.reread
                    + " re-read from disk and identical, " + content.unreadable.size()
                    + " owner-unreadable verified without reopening or chmod"
                    + (content.unreadable.isEmpty() ? "" : " " + content.unreadable)
                    + "; " + links + " hard links share their target's inode");
            return 0;
        } catch (Throwable t) {
            report.line("FAIL real archive: " + t);
            return 1;
        }
    }

    /**
     * Content of each regular file for {@link TreeManifest}: its extraction
     * record, which must exist, be complete and match the file's lstat size.
     * A file its owner can read must also hash to the same value on disk. A
     * mismatch is returned as a value the manifest cannot contain, so it
     * shows up as a difference.
     */
    static final class ContentCheck implements TreeManifest.Contents {
        private final RecordingFileOps recording;
        private final FileOps ops;
        int files;
        int reread;
        final java.util.List<String> unreadable = new java.util.ArrayList<>();

        ContentCheck(RecordingFileOps recording, FileOps ops) {
            this.recording = recording;
            this.ops = ops;
        }

        @Override
        public String sha256(File file, String rel, long size) throws java.io.IOException {
            files++;
            RecordingFileOps.Content record = recording.contentOf(file);
            if (record == null || record.sha256() == null) return "NOT-WRITTEN-BY-THE-EXTRACTOR";
            if (record.length() != size) {
                return "SIZE-ON-DISK-" + size + "-BUT-WROTE-" + record.length();
            }
            // Decided from the mode bits, not by trying: root could read it anyway.
            if ((ops.permissions(file) & 0400) == 0) {
                unreadable.add(rel + "=" + Integer.toOctalString(ops.permissions(file)));
                return record.sha256();
            }
            String disk = TreeManifest.sha256(file.toPath());
            reread++;
            return disk.equals(record.sha256()) ? record.sha256() : "ON-DISK-" + disk;
        }
    }

    private static boolean sameInode(File a, File b) throws java.io.IOException {
        Object ka = java.nio.file.Files.readAttributes(a.toPath(),
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();
        Object kb = java.nio.file.Files.readAttributes(b.toPath(),
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();
        return ka != null && ka.equals(kb);
    }

    private static int run(String kind, Map<String, ExtractorSecurityCases.Case> cases,
                           FileOps ops, File work, Report report) {
        int failures = 0;
        for (Map.Entry<String, ExtractorSecurityCases.Case> c : cases.entrySet()) {
            File dir = new File(work, kind + "-" + c.getKey());
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("mkdirs " + dir);
                c.getValue().run(ops, dir);
                report.line("PASS " + kind + " " + c.getKey());
            } catch (Throwable t) {
                failures++;
                report.line("FAIL " + kind + " " + c.getKey() + ": " + t);
            }
        }
        return failures;
    }

    /** Host entry point: ExtractorGate WORKDIR [ARCHIVE SHA256 MANIFEST]. */
    public static void main(String[] args) throws Exception {
        File work = new File(args[0]);
        if (!work.isDirectory() && !work.mkdirs()) throw new IllegalStateException("workdir");
        FileOps ops = new JvmFileOps();
        Report out = System.out::println;
        out.line("INFO host gate: shared extractor policy with JvmFileOps (NOT the Android"
                + " FileOps; the device gate proves AndroidFileOps)");
        int failures = securityCases(ops, new File(work, "cases"), out);
        if (args.length >= 4) {
            failures += realArchive(ops, new File(args[1]), args[2], new File(args[3]),
                    new File(work, "rootfs"), out);
        }
        out.line(failures == 0 ? "GATE PASS" : "GATE FAIL failures=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
