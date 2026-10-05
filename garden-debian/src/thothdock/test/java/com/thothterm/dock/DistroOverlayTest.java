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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;

/**
 * The ThothDock overlay replaces distro.properties wholesale, so it must not
 * drift from the Trixie edition's: the rootfs pin, sizes, ports and sudo path
 * have to be identical, and only the edition name may differ.
 */
public class DistroOverlayTest {
    private static Properties load(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) f = new File("garden-debian/" + path);
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        }
        return p;
    }

    @Test
    public void overlayDiffersFromTrixieOnlyInTheEditionName() throws Exception {
        Properties main = load("src/main/assets/garden/distro.properties");
        Properties overlay = load("src/thothdock/assets/garden/distro.properties");
        assertEquals("ThothDock", overlay.getProperty("editionName"));
        assertTrue(!"ThothDock".equals(main.getProperty("editionName")));
        main.remove("editionName");
        overlay.remove("editionName");
        assertEquals(main, overlay);
    }
}
