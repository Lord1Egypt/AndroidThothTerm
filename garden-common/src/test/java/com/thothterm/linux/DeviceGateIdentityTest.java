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
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The device extractor gate installs only an isolated QA package, proven from
 * the APK files themselves, always with --no-incremental, and never touches a
 * protected app. tests/garden-common/extractor/device-gate-selftest.sh holds
 * the cases; it needs no device and no SDK.
 */
public class DeviceGateIdentityTest {
    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }

    @Test
    public void theGateInstallsOnlyAnIsolatedQaPackage() throws Exception {
        assumeTrue("sh is required", new File("/bin/sh").canExecute());
        File script = new File(repo(), "tests/garden-common/extractor/device-gate-selftest.sh");
        ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(output, 0, p.waitFor());
    }
}
