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
     * Extracts the real archive and compares the final tree with the
     * independent manifest: path, type, mode, size and symlink target from
     * {@code lstat} and {@code readlink}; the content of every regular file
     * from the final object itself ({@link ContentCheck}). What
     * {@link RecordingFileOps} saw during extraction is only a witness that
     * must agree. No mode is ever changed to look: Arch's 0110
     * {@code dbus-daemon-launch-helper} stays 0110 and is read through the
     * descriptor pinned before it became unreadable. Every hard link group the
     * extractor made must be one inode on disk.
     *
     * @return the number of failures (0 or 1)
     */
    public static int realArchive(FileOps ops, ObjectInspector inspector, File archive,
                                  String sha256, File manifest, File staging, Report report) {
        RecordingFileOps recording = new RecordingFileOps(ops, inspector);
        try {
            if (staging.exists()) SafeFileTree.deleteTree(ops, staging, staging);
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
            ContentCheck content = new ContentCheck(recording, ops, inspector);
            List<String> actual = TreeManifest.of(staging, ops::permissions, content);
            List<String> diff = TreeManifest.diff(expected, actual, 40);
            if (!diff.isEmpty()) {
                for (String line : diff) report.line("DIFF " + line);
                report.line("FAIL tree differs from the independent manifest ("
                        + expected.size() + " expected, " + actual.size() + " extracted)");
                return 1;
            }
            List<List<File>> groups = recording.hardlinkGroups();
            String split = hardlinkGroupProblem(groups, inspector);
            if (split != null) {
                report.line("FAIL " + split);
                return 1;
            }
            int linked = 0;
            for (List<File> group : groups) linked += group.size();
            report.line("PASS extracted tree matches the independent manifest: "
                    + actual.size() + " paths (" + result.hardlinkFallbacks
                    + " hardlinks materialized as copies)");
            report.line("PASS content of " + content.files + " regular files verified on the final"
                    + " objects and equal to the bytes the extractor wrote: " + content.reread
                    + " re-read from disk, " + content.pinned.size()
                    + " owner-unreadable read through the descriptor pinned on that same inode"
                    + " (no reopen, no chmod)"
                    + (content.pinned.isEmpty() ? "" : " " + content.pinned)
                    + "; " + groups.size() + " hard link groups (" + linked
                    + " names) share one inode each");
            return 0;
        } catch (Throwable t) {
            report.line("FAIL real archive: " + t);
            return 1;
        } finally {
            try {
                recording.close();
            } catch (java.io.IOException e) {
                report.line("WARN closing pinned descriptors: " + e);
            }
        }
    }

    /** Null when every group is one inode on disk, else what is not. */
    static String hardlinkGroupProblem(List<List<File>> groups, ObjectInspector inspector)
            throws java.io.IOException {
        for (List<File> group : groups) {
            String first = inspector.identity(group.get(0));
            for (File name : group) {
                if (!inspector.identity(name).equals(first)) {
                    return "hard link is not one inode with " + group.get(0) + ": " + name;
                }
            }
        }
        return null;
    }

    /**
     * Content of each regular file for {@link TreeManifest}, always taken from
     * the final object, never from a record alone. The extraction record must
     * exist, be complete and equal it.
     * <ul>
     *   <li>A file its owner can read (decided from the mode bits, so root
     *       behaves the same) is re-read from the final path.</li>
     *   <li>A file its owner cannot read must have been pinned while it still
     *       could be; the pinned descriptor's inode must be the inode at the
     *       final path, and its current bytes are hashed through it. A
     *       same-size rewrite, a truncation or a replacement after the record
     *       was made all fail.</li>
     * </ul>
     * A mismatch is returned as a value the manifest cannot contain, so it
     * shows up as a difference.
     */
    static final class ContentCheck implements TreeManifest.Contents {
        private final RecordingFileOps recording;
        private final FileOps ops;
        private final ObjectInspector inspector;
        int files;
        int reread;
        final java.util.List<String> pinned = new java.util.ArrayList<>();

        ContentCheck(RecordingFileOps recording, FileOps ops, ObjectInspector inspector) {
            this.recording = recording;
            this.ops = ops;
            this.inspector = inspector;
        }

        @Override
        public String sha256(File file, String rel, long size) throws java.io.IOException {
            files++;
            RecordingFileOps.Node node = recording.nodeOf(file);
            if (node == null || node.sha256() == null) return "NOT-WRITTEN-BY-THE-EXTRACTOR";
            if (node.length() != size) {
                return "SIZE-ON-DISK-" + size + "-BUT-WROTE-" + node.length();
            }
            int mode = ops.permissions(file);
            String now;
            if ((mode & 0400) != 0) {
                now = TreeManifest.sha256(file.toPath());
                reread++;
            } else {
                ObjectInspector.Pin pin = node.pin();
                if (pin == null) return "OWNER-UNREADABLE-AND-NEVER-PINNED";
                if (!pin.identity().equals(inspector.identity(file))) {
                    return "FINAL-PATH-IS-NOT-THE-PINNED-INODE";
                }
                if (pin.size() != size) return "PINNED-INODE-SIZE-" + pin.size();
                now = pin.sha256();
                pinned.add(rel + "=" + Integer.toOctalString(mode));
            }
            return now.equals(node.sha256()) ? now : "FINAL-OBJECT-" + now + "-BUT-WROTE-" + node.sha256();
        }
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
            failures += realArchive(ops, new JvmObjectInspector(), new File(args[1]), args[2],
                    new File(args[3]), new File(work, "rootfs"), out);
        }
        out.line(failures == 0 ? "GATE PASS" : "GATE FAIL failures=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
