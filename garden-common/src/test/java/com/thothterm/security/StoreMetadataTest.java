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
package com.thothterm.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Every app built from this repository keeps its store metadata in its own
 * module. F-Droid's metadata scan reads a repository-root fastlane/ for every
 * app (fdroidserver update.insert_localized_app_metadata), so a root directory
 * would put one edition's text, changelogs and images into all of them.
 */
public class StoreMetadataTest {
    private static final File ROOT = new File("..");

    @Test
    public void noRepositoryRootFastlane() {
        assertFalse(new File(ROOT, "fastlane").exists());
    }

    @Test
    public void everyStoreAppHasItsOwnListingAndIcon() throws IOException {
        File[] modules = ROOT.listFiles((dir, name) -> name.equals("term-ubuntu")
                || (name.startsWith("garden-") && !name.equals("garden-common")));
        assertTrue(modules != null && modules.length >= 3);
        for (File module : modules) {
            File en = new File(module, "fastlane/metadata/android/en-US");
            for (String f : new String[]{"title.txt", "short_description.txt", "full_description.txt"}) {
                assertTrue(new File(en, f) + " missing", new File(en, f).isFile());
            }
            File icon = new File(en, "images/icon.png");
            assertTrue(icon + " missing", icon.isFile());
            try (DataInputStream in = new DataInputStream(new FileInputStream(icon))) {
                assertEquals(0x89504E47, in.readInt());
                in.skipBytes(12);
                assertEquals(icon.toString(), 512, in.readInt());
                assertEquals(icon.toString(), 512, in.readInt());
            }
        }
    }
}
