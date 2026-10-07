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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.thothterm.dock.EngineGuard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Engine Guard enforcement is written before a guest is usable: the three
 * files land in the guest, hook first, each by rename, and a guest that cannot
 * be guarded is an error rather than a silently unguarded terminal.
 */
public class EngineGuardFilesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<String> renames = new ArrayList<>();
    private final FileOps ops = new JvmFileOps() {
        @Override
        public void rename(File from, File to) throws IOException {
            renames.add(to.getName());
            super.rename(from, to);
        }
    };
    private File root;
    private final EngineGuard.Enforcement guard = new EngineGuard.Enforcement(
            "#!/bin/sh\nexit 0\n", "DPkg::Pre-Install-Pkgs { \"/usr/lib/thothdock/engine-guard-hook\"; };\n",
            "Package: docker.io\nPin: version 9999:*\nPin-Priority: 1001\n");

    @Before
    public void guest() throws Exception {
        root = tmp.newFolder("rootfs");
        new File(root, "var/lib/dpkg").mkdirs();
        Files.write(new File(root, "var/lib/dpkg/status").toPath(), new byte[0]);
        new File(root, "etc/apt/apt.conf.d").mkdirs();
        new File(root, "etc/apt/preferences.d").mkdirs();
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void writesTheHookBeforeTheConfigurationThatNamesIt() throws Exception {
        RootfsManager.writeEngineGuard(ops, root, guard);
        assertEquals(guard.hook, read("usr/lib/thothdock/engine-guard-hook"));
        assertEquals(guard.aptConf, read("etc/apt/apt.conf.d/99thothdock-engine-guard"));
        assertEquals(guard.pin, read("etc/apt/preferences.d/thothdock-engine-guard"));
        assertEquals("engine-guard-hook", renames.get(0));
        assertEquals("99thothdock-engine-guard", renames.get(1));
        assertTrue(Files.getPosixFilePermissions(new File(root, "usr/lib/thothdock/engine-guard-hook").toPath())
                .contains(PosixFilePermission.OWNER_EXECUTE));
        // No temporary file is left behind.
        for (File dir : new File[]{new File(root, "usr/lib/thothdock"), new File(root, "etc/apt/apt.conf.d"),
                new File(root, "etc/apt/preferences.d")}) {
            for (String n : dir.list()) assertFalse(n, n.endsWith(".thothterm-new"));
        }
    }

    @Test
    public void anOutdatedHookIsReplacedAndAnUpToDateOneIsLeftAlone() throws Exception {
        RootfsManager.writeEngineGuard(ops, root, guard);
        renames.clear();
        RootfsManager.writeEngineGuard(ops, root, guard);
        assertTrue("an unchanged guard must not be rewritten", renames.isEmpty());
        Files.write(new File(root, "usr/lib/thothdock/engine-guard-hook").toPath(),
                "#!/bin/sh\n# old parser\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        RootfsManager.writeEngineGuard(ops, root, guard);
        assertEquals(guard.hook, read("usr/lib/thothdock/engine-guard-hook"));
    }

    @Test
    public void aGuestWithoutDpkgIsLeftAlone() throws Exception {
        assertTrue(new File(root, "var/lib/dpkg/status").delete());
        RootfsManager.writeEngineGuard(ops, root, guard);
        assertFalse(new File(root, "usr/lib/thothdock").exists());
    }

    @Test
    public void aGuestThatCannotBeGuardedIsAnErrorNotAnUnguardedTerminal() throws Exception {
        // The configuration path is taken by a directory: nothing can be written there.
        assertTrue(new File(root, "etc/apt/apt.conf.d/99thothdock-engine-guard").mkdirs());
        try {
            RootfsManager.writeEngineGuard(ops, root, guard);
            fail("a guest that cannot be guarded must not be treated as guarded");
        } catch (IOException expected) {
            // propagates to prepareSession, so the window does not open unguarded
        }
    }
}
