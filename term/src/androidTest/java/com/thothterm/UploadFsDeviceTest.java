/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.thothterm.upload.AndroidUploadFs;
import com.thothterm.upload.StagingJournal;
import com.thothterm.upload.UploadBatch;
import com.thothterm.upload.UploadError;
import com.thothterm.upload.UploadFs;
import com.thothterm.upload.UploadTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * The upload engine on the device's own kernel calls: renameat2 with
 * RENAME_NOREPLACE through libtermexec, exclusive creation, exact modes and
 * links that are never followed. Runs in the app's private storage only.
 */
@RunWith(AndroidJUnit4.class)
public class UploadFsDeviceTest {
    private File root;
    private File cwd;
    private final AndroidUploadFs fs = new AndroidUploadFs();
    private StagingJournal journal;

    @Before
    public void setUp() {
        File files = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        root = new File(files, "upload-device-test");
        delete(root);
        cwd = new File(root, "home/مشروع جديد");
        assertTrue(cwd.mkdirs());
        journal = new StagingJournal(new File(root, "journal"));
    }

    @After
    public void tearDown() {
        delete(root);
    }

    @Test
    public void renameNeverReplacesOnThisKernel() throws Exception {
        File a = new File(root, "a");
        File b = new File(root, "b");
        assertTrue(a.createNewFile());
        assertTrue(b.createNewFile());
        int errno = com.thothterm.Process.renameNoReplace(a.getPath(), b.getPath());
        if (Build.VERSION.SDK_INT >= 30) {
            assertEquals("renameat2(RENAME_NOREPLACE) must refuse", OsConstants.EEXIST, errno);
        }
        assertEquals(UploadFs.EEXIST, fs.renameNoReplace(a.getPath(), b.getPath()));
        assertTrue(a.exists() && b.exists());
        assertEquals(0, fs.renameNoReplace(a.getPath(), new File(root, "c").getPath()));
        assertFalse(a.exists());
    }

    @Test
    public void filesAndFoldersLandWithTheSessionsModes() throws Exception {
        UploadTarget target = new UploadTarget(cwd.getPath(), "/home/thoth/x", 022);
        UploadBatch files = UploadBatch.begin(fs, journal, target, UploadBatch.Kind.FILES, null, 10);
        byte[] data = "مرحبا\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("مرحبا.txt", files.receive(Collections.singletonList("مرحبا.txt"), data.length,
                new ByteArrayInputStream(data), null));
        assertEquals("مرحبا (1).txt", files.receive(Collections.singletonList("مرحبا.txt"), data.length,
                new ByteArrayInputStream(data), null));
        files.finish();
        assertEquals(0644, Os.stat(new File(cwd, "مرحبا.txt").getPath()).st_mode & 0777);

        UploadBatch folder = UploadBatch.begin(fs, journal, target, UploadBatch.Kind.FOLDER, "project", 10);
        folder.receive(Arrays.asList("src", "main.py"), 1, new ByteArrayInputStream(new byte[]{'x'}), null);
        folder.makeDirectory(Arrays.asList("empty"));
        assertEquals("project", folder.finish());
        assertEquals(0755, Os.stat(new File(cwd, "project/src").getPath()).st_mode & 0777);
        assertTrue(new File(cwd, "project/empty").isDirectory());
        assertArrayEquals(new String[]{"project", "مرحبا (1).txt", "مرحبا.txt"}, sorted(cwd.list()));
    }

    @Test
    public void aLinkInTheTargetIsNeverFollowed() throws Exception {
        File outside = new File(root, "outside");
        assertTrue(outside.mkdir());
        Os.symlink(outside.getPath(), new File(cwd, "evil").getPath());
        UploadTarget target = new UploadTarget(cwd.getPath(), "/x", 022);
        UploadBatch folder = UploadBatch.begin(fs, journal, target, UploadBatch.Kind.FOLDER, "evil", 1);
        folder.receive(Collections.singletonList("t"), 1, new ByteArrayInputStream(new byte[]{'x'}), null);
        assertEquals("evil (1)", folder.finish());
        assertEquals(0, outside.list().length);
        try {
            fs.create(new File(cwd, "evil").getPath(), 0644);
            fail("created through a link");
        } catch (UploadFs.Failure e) {
            assertEquals(OsConstants.EEXIST, e.errno);
        }
    }

    @Test
    public void anUnwritableDirectoryIsRefused() throws Exception {
        Os.chmod(cwd.getPath(), 0555);
        try {
            UploadBatch.begin(fs, journal, new UploadTarget(cwd.getPath(), "/x", 022),
                    UploadBatch.Kind.FILES, null, 1);
            fail("uploaded into a read-only directory");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.NOT_WRITABLE, e.code);
        } finally {
            Os.chmod(cwd.getPath(), 0700);
        }
    }

    private static String[] sorted(String[] names) {
        Arrays.sort(names);
        return names;
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
