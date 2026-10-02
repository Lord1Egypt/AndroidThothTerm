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

import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;

/** {@link FileOpsContract} against the JVM implementation. */
public class FileOpsContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void jvmFileOpsMeetsTheContract() throws Exception {
        StringBuilder failures = new StringBuilder();
        for (Map.Entry<String, ExtractorSecurityCases.Case> c : FileOpsContract.all().entrySet()) {
            File dir = temporaryFolder.newFolder(c.getKey());
            try {
                c.getValue().run(new JvmFileOps(), dir);
            } catch (Throwable t) {
                failures.append(c.getKey()).append(": ").append(t).append('\n');
            }
        }
        if (failures.length() > 0) fail(failures.toString());
    }
}
