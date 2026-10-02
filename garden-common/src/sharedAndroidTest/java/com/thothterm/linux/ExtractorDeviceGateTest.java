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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The extractor gate on a device, with the APK's own {@link AndroidFileOps}
 * and {@link RootfsArchive}: every adversarial case and the FileOps contract,
 * then -- when tests/garden-common/extractor/device-gate.sh provides it -- the
 * real pinned rootfs, compared with the independent manifest.
 *
 * <p>The report is written to logcat (tag ExtractorGate) and to
 * {@code files/extractor-gate/report.txt} in the app under test.</p>
 */
@RunWith(AndroidJUnit4.class)
public class ExtractorDeviceGateTest {
    private static final String TAG = "ExtractorGate";

    @Test
    public void securityCasesWithAndroidFileOps() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File work = fresh(new File(context.getCacheDir(), "extractor-gate-cases"));
        Report report = new Report(context, "cases.txt");
        int failures;
        try {
            failures = ExtractorGate.securityCases(new AndroidFileOps(), work, report);
        } finally {
            report.close();
            SafeFileTree.deleteTree(new AndroidFileOps(), work, work);
        }
        assertEquals("extractor security failures, see " + report.file, 0, failures);
    }

    @Test
    public void realRootfsWithAndroidFileOps() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String archive = args.getString("gateArchive");
        String sha256 = args.getString("gateSha256");
        String manifest = args.getString("gateManifest");
        Assume.assumeTrue("no real archive given (device-gate.sh passes gateArchive,"
                + " gateSha256 and gateManifest)", archive != null && sha256 != null
                && manifest != null);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File files = context.getFilesDir();
        // Not the edition's rootfs directory: the gate never touches an installation.
        File staging = new File(files, "extractor-gate/rootfs");
        Report report = new Report(context, "real.txt");
        int failures;
        try {
            failures = ExtractorGate.realArchive(new AndroidFileOps(), new File(files, archive),
                    sha256, new File(files, manifest), staging, report);
        } finally {
            report.close();
            if (staging.exists()) SafeFileTree.deleteTree(new AndroidFileOps(), staging, staging);
        }
        assertEquals("real archive gate failed, see " + report.file, 0, failures);
    }

    private static File fresh(File dir) throws IOException {
        if (dir.exists()) SafeFileTree.deleteTree(new AndroidFileOps(), dir, dir);
        if (!dir.mkdirs()) throw new IOException("mkdirs " + dir);
        return dir;
    }

    private static final class Report implements ExtractorGate.Report {
        final File file;
        private final OutputStream out;

        Report(Context context, String name) throws IOException {
            File dir = new File(context.getFilesDir(), "extractor-gate");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
            file = new File(dir, name);
            out = new FileOutputStream(file);
        }

        @Override
        public void line(String text) {
            Log.i(TAG, text);
            try {
                out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // logcat has it
            }
        }

        void close() throws IOException {
            out.close();
        }
    }
}
