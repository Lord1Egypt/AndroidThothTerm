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

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class UbuntuRuntimeTest {
    private static UbuntuRuntime runtime() {
        return new UbuntuRuntime(
                "/data/app/com.thothterm.ubuntu/lib/arm64/libproot.so",
                "/data/app/com.thothterm.ubuntu/lib/arm64/libproot_loader.so",
                "/data/user/0/com.thothterm.ubuntu/files/linux/ubuntu-26.04/rootfs",
                "/data/user/0/com.thothterm.ubuntu/files/linux/runtime/lib",
                "/data/user/0/com.thothterm.ubuntu/files/linux/runtime/tmp",
                "/data/user/0/com.thothterm.ubuntu/files/linux/runtime/resolv.conf",
                "xterm-256color");
    }

    @Test
    public void argvEntersUbuntuAsFakeRootInHome() {
        List<String> argv = runtime().buildArgv();

        assertEquals("/data/app/com.thothterm.ubuntu/lib/arm64/libproot.so", argv.get(0));
        assertTrue(argv.contains("--rootfs=/data/user/0/com.thothterm.ubuntu/files/linux/ubuntu-26.04/rootfs"));
        assertTrue(argv.contains("--root-id"));
        assertTrue(argv.contains("--cwd=" + UbuntuRuntime.LINUX_HOME));
        assertTrue(argv.contains("--bind=/dev"));
        assertTrue(argv.contains("--bind=/proc"));
        assertTrue(argv.contains("--bind=/sys"));
        assertTrue(argv.contains("--bind=/data/user/0/com.thothterm.ubuntu/files/linux/runtime/resolv.conf:/etc/resolv.conf"));
        assertTrue(argv.contains("/usr/bin/su"));
        assertTrue(argv.contains("-m"));
        assertTrue(argv.contains("thoth"));
        assertTrue(argv.contains("/bin/bash"));
        // su options must precede the user name, and -i is not a su option.
        assertTrue(argv.indexOf("/usr/bin/su") < argv.indexOf("thoth"));
        assertTrue(argv.indexOf("-s") < argv.indexOf("thoth"));
        assertFalse("su must not receive -i", argv.contains("-i"));
        assertFalse(argv.contains("--login"));
        assertFalse("must not bind the whole Android /data", argv.contains("--bind=/data"));
    }

    @Test
    public void environmentTargetsRuntimeAndDoesNotLeakPreload() {
        Map<String, String> env = runtime().buildEnvironment();

        assertEquals("/home/thoth", env.get("HOME"));
        assertEquals("thoth", env.get("USER"));
        assertEquals("thoth", env.get("LOGNAME"));
        assertEquals("/bin/bash", env.get("SHELL"));
        assertEquals("/data/app/com.thothterm.ubuntu/lib/arm64/libproot_loader.so",
                env.get("PROOT_LOADER"));
        assertEquals("/data/user/0/com.thothterm.ubuntu/files/linux/runtime/lib",
                env.get("LD_LIBRARY_PATH"));
        assertEquals("xterm-256color", env.get("TERM"));
        assertEquals("C.UTF-8", env.get("LANG"));
        assertEquals("C.UTF-8", env.get("LC_ALL"));
        assertFalse(env.containsKey("LD_PRELOAD"));
        assertFalse(env.containsKey("ENV"));
    }

    @Test
    public void envArrayMatchesMap() {
        Map<String, String> env = runtime().buildEnvironment();
        String[] array = UbuntuRuntime.toEnvArray(env);
        assertEquals(env.size(), array.length);
        boolean found = false;
        for (String entry : array) {
            if (entry.equals("HOME=/home/thoth")) found = true;
        }
        assertTrue(found);
    }
}
