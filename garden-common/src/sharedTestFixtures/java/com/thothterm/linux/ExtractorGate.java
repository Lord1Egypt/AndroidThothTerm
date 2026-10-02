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

    /** @return the number of failures (0 or 1) */
    public static int realArchive(FileOps ops, File archive, String sha256, File manifest,
                                  File staging, Report report) {
        try {
            if (staging.exists()) SafeFileTree.deleteTree(ops, staging, staging);
            RootfsArchive.Result result;
            InputStream in = new FileInputStream(archive);
            try {
                result = RootfsArchive.extract(in, staging, ops, null);
            } finally {
                in.close();
            }
            report.line("INFO extract " + result.summary() + " sha256=" + result.sha256);
            RootfsArchive.verify(result, sha256);
            report.line("PASS archive verified: sha256 matches, 0 rejected entries");
            List<String> expected = TreeManifest.read(manifest);
            List<String> actual = TreeManifest.of(staging, ops::permissions);
            List<String> diff = TreeManifest.diff(expected, actual, 40);
            if (!diff.isEmpty()) {
                for (String line : diff) report.line("DIFF " + line);
                report.line("FAIL tree differs from the independent manifest ("
                        + expected.size() + " expected, " + actual.size() + " extracted)");
                return 1;
            }
            report.line("PASS extracted tree matches the independent manifest: "
                    + actual.size() + " paths (" + result.hardlinkFallbacks
                    + " hardlinks materialized as copies)");
            return 0;
        } catch (Throwable t) {
            report.line("FAIL real archive: " + t);
            return 1;
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
            failures += realArchive(ops, new File(args[1]), args[2], new File(args[3]),
                    new File(work, "rootfs"), out);
        }
        out.line(failures == 0 ? "GATE PASS" : "GATE FAIL failures=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
