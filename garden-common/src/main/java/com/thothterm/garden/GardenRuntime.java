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

package com.thothterm.garden;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the PRoot command line and environment used to enter the distro. Kept
 * free of Android APIs so argument construction is unit-testable.
 *
 * <p>PRoot runs with its fake-root option ({@code --root-id}) so apt/dpkg and
 * package maintainer scripts behave normally. This is user-space emulation
 * inside a rootless Android application and grants no host privileges.</p>
 */
public final class GardenRuntime {
    public static final String LINUX_HOME = GardenDistro.USER_HOME;

    private final String prootPath;
    private final String loaderPath;
    private final String rootfsDir;
    private final String runtimeLibDir;
    private final String prootTmpDir;
    private final String resolverFile;
    private final String terminalType;
    private final String shell;
    private final String suPath;

    public GardenRuntime(String prootPath, String loaderPath, String rootfsDir,
                         String runtimeLibDir, String prootTmpDir, String resolverFile,
                         String terminalType, String shell, String suPath) {
        this.prootPath = prootPath;
        this.loaderPath = loaderPath;
        this.rootfsDir = rootfsDir;
        this.runtimeLibDir = runtimeLibDir;
        this.prootTmpDir = prootTmpDir;
        this.resolverFile = resolverFile;
        this.terminalType = terminalType;
        this.shell = shell;
        this.suPath = suPath;
    }

    public static GardenRuntime from(RootfsManager manager, String terminalType) {
        return new GardenRuntime(
                manager.prootPath(),
                manager.loaderPath(),
                manager.prootRootfsPath(),
                manager.runtimeLibDir().getAbsolutePath(),
                manager.prootTmpDir().getAbsolutePath(),
                AndroidNetworkResolver.get().resolverFile().getAbsolutePath(),
                terminalType,
                manager.distro().shell(),
                manager.distro().suPath());
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
        argv.add("--bind=" + resolverFile + ":/etc/resolv.conf");
        // Drop from PRoot fake-root to the guest "thoth" account through the
        // guest's own su. Options must precede the user name; "-m" preserves
        // the PRoot environment (notably LD_LIBRARY_PATH, which the PRoot
        // loader needs for every later guest execve).
        argv.add(suPath);
        argv.add("-m");
        argv.add("-s");
        argv.add(shell);
        argv.add(GardenDistro.USER);
        return argv;
    }

    public Map<String, String> buildEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", LINUX_HOME);
        env.put("USER", GardenDistro.USER);
        env.put("LOGNAME", GardenDistro.USER);
        env.put("SHELL", shell);
        env.put("TERM", terminalType == null ? "xterm-256color" : terminalType);
        env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.put("TMPDIR", "/tmp");
        env.put("LANG", "C.UTF-8");
        env.put("LC_ALL", "C.UTF-8");
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
