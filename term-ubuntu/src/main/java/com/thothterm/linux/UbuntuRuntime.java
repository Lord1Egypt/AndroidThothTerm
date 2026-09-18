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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the PRoot command line and environment used to enter Ubuntu. Kept
 * free of Android APIs so argument construction is unit-testable.
 *
 * <p>PRoot runs with its fake-root option ({@code --root-id}) so apt/dpkg and
 * package maintainer scripts behave normally. This is user-space emulation
 * inside a rootless Android application and grants no host privileges.</p>
 */
public final class UbuntuRuntime {
    public static final String LINUX_HOME = "/home/thoth";

    private final String prootPath;
    private final String loaderPath;
    private final String rootfsDir;
    private final String runtimeLibDir;
    private final String prootTmpDir;
    private final String terminalType;

    public UbuntuRuntime(String prootPath, String loaderPath, String rootfsDir,
                         String runtimeLibDir, String prootTmpDir, String terminalType) {
        this.prootPath = prootPath;
        this.loaderPath = loaderPath;
        this.rootfsDir = rootfsDir;
        this.runtimeLibDir = runtimeLibDir;
        this.prootTmpDir = prootTmpDir;
        this.terminalType = terminalType;
    }

    public static UbuntuRuntime from(RootfsManager manager, String terminalType) {
        return new UbuntuRuntime(
                manager.prootPath(),
                manager.loaderPath(),
                manager.rootfsDir().getAbsolutePath(),
                manager.runtimeLibDir().getAbsolutePath(),
                manager.prootTmpDir().getAbsolutePath(),
                terminalType);
    }

    public List<String> buildArgv() {
        List<String> argv = new ArrayList<>();
        argv.add(prootPath);
        argv.add("--rootfs=" + rootfsDir);
        argv.add("--root-id");
        argv.add("--link2symlink");
        argv.add("--cwd=" + LINUX_HOME);
        argv.add("--kill-on-exit");
        argv.add("--kernel-release=6.1.0-thothterm");
        argv.add("--bind=/dev");
        argv.add("--bind=/proc");
        argv.add("--bind=/sys");
        argv.add("--bind=/proc/mounts:/etc/mtab");
        argv.add("/bin/bash");
        argv.add("--login");
        argv.add("-i");
        return argv;
    }

    public Map<String, String> buildEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", LINUX_HOME);
        env.put("USER", "thoth");
        env.put("LOGNAME", "thoth");
        env.put("TERM", terminalType == null ? "xterm-256color" : terminalType);
        env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.put("TMPDIR", "/tmp");
        env.put("LANG", "C.UTF-8");
        env.put("PROOT_TMP_DIR", prootTmpDir);
        env.put("PROOT_LOADER", loaderPath);
        env.put("LD_LIBRARY_PATH", runtimeLibDir);
        return env;
    }

    public static String[] toEnvArray(Map<String, String> env) {
        String[] array = new String[env.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            array[i++] = entry.getKey() + "=" + entry.getValue();
        }
        return array;
    }
}
