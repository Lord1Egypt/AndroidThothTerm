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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.system.Os;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** The sudo setuid helper: a regular file only, through one descriptor, never a symlink. */
@RunWith(AndroidJUnit4.class)
public class AndroidFileOpsSetuidTest {
    @Test
    public void setsSetuidOnARegularFileAndRefusesASymlink() throws Exception {
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getCacheDir(), "setuid-test");
        if (dir.exists()) SafeFileTree.deleteTree(new AndroidFileOps(), dir, dir);
        assertTrue(dir.mkdirs());
        try {
            File sudo = new File(dir, "sudo.ws");
            Files.write(sudo.toPath(), new byte[]{1});
            Os.chmod(sudo.getAbsolutePath(), 0755);
            assertTrue(AndroidFileOps.ensureSetuidNoFollow(sudo, 04755));
            assertEquals(04755, Os.lstat(sudo.getAbsolutePath()).st_mode & 07777);

            File outside = new File(dir, "outside");
            Files.write(outside.toPath(), new byte[]{2});
            Os.chmod(outside.getAbsolutePath(), 0600);
            File link = new File(dir, "link");
            Os.symlink(outside.getAbsolutePath(), link.getAbsolutePath());
            try {
                AndroidFileOps.ensureSetuidNoFollow(link, 04755);
                fail("a symlink was accepted");
            } catch (IOException expected) {
                // not a regular file
            }
            assertEquals(0600, Os.lstat(outside.getAbsolutePath()).st_mode & 07777);
        } finally {
            SafeFileTree.deleteTree(new AndroidFileOps(), dir, dir);
        }
    }
}
