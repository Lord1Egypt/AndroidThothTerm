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

package com.thothterm.dock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ComposeSetupTest {
    private static File status(String text) throws Exception {
        File f = Files.createTempFile("dpkg-status", "").toFile();
        f.deleteOnExit();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void detectsAnInstalledPackage() throws Exception {
        assertTrue(ComposeSetup.installed(status(
                "Package: bash\nStatus: install ok installed\n\n"
                + "Package: docker-compose\nStatus: install ok installed\nVersion: 2.26.1-4\n\n")));
    }

    @Test
    public void aHalfInstalledOrOtherPackageDoesNotCount() throws Exception {
        assertFalse(ComposeSetup.installed(status(
                "Package: docker-compose\nStatus: install ok half-configured\n\n")));
        assertFalse(ComposeSetup.installed(status(
                "Package: docker-compose\nStatus: deinstall ok config-files\n\n")));
        assertFalse(ComposeSetup.installed(status(
                "Package: docker-compose-v2\nStatus: install ok installed\n\n")));
        assertFalse(ComposeSetup.installed(status("")));
        assertFalse(ComposeSetup.installed(new File("/nonexistent/dpkg/status")));
    }
}
