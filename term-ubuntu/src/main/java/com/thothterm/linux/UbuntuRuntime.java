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
    private final String resolverFile;
    private final String terminalType;

    public UbuntuRuntime(String prootPath, String loaderPath, String rootfsDir,
                         String runtimeLibDir, String prootTmpDir, String resolverFile,
                         String terminalType) {
        this.prootPath = prootPath;
        this.loaderPath = loaderPath;
        this.rootfsDir = rootfsDir;
        this.runtimeLibDir = runtimeLibDir;
        this.prootTmpDir = prootTmpDir;
        this.resolverFile = resolverFile;
        this.terminalType = terminalType;
    }

    public static UbuntuRuntime from(RootfsManager manager, String terminalType) {
        return new UbuntuRuntime(
                manager.prootPath(),
                manager.loaderPath(),
                manager.prootRootfsPath(),
                manager.runtimeLibDir().getAbsolutePath(),
                manager.prootTmpDir().getAbsolutePath(),
                AndroidNetworkResolver.get().resolverFile().getAbsolutePath(),
                terminalType);
    }

    public List<String> buildArgv() {
        // A window hangs up like a real terminal when its shell exits: the
        // rest of its session gets SIGHUP, a nohup'd job keeps running, and
        // PRoot stays only as long as such a job needs it. --kill-on-exit
        // would kill nohup'd jobs too.
        List<String> argv = baseArgv(LINUX_HOME, true, "--hangup-on-exit");
        // Drop from PRoot fake-root to the guest "thoth" account through the
        // guest's own util-linux su. Options must precede the user name;
        // "-i" is not a su option and previously made su exit immediately
        // ("invalid option -- 'i'"), which closed the session window.
        // "-m" preserves the PRoot environment (notably LD_LIBRARY_PATH, which
        // the PRoot loader needs for every later guest execve).
        argv.add("/usr/bin/su");
        argv.add("-m");
        argv.add("-s");
        argv.add("/bin/bash");
        argv.add("thoth");
        return argv;
    }

    /**
     * One-shot PRoot command that runs as fake-root in {@code /}. Used to
     * provision the admin packages.
     *
     * <p>The resolver is bound whenever the file exists. Installing from the
     * embedded packages needs no network, but a build without them installs
     * sudo from Ubuntu's archive, and apt cannot resolve a hostname without
     * {@code /etc/resolv.conf}. Binding a file that is already there costs
     * nothing in the offline case.
     */
    public List<String> buildProvisioningArgv(List<String> command) {
        // Provisioning waits for PRoot to exit, so nothing it starts may outlive it.
        List<String> argv = baseArgv("/", resolverFile != null
                && new java.io.File(resolverFile).isFile(), "--kill-on-exit");
        argv.addAll(command);
        return argv;
    }

    private List<String> baseArgv(String cwd, boolean bindResolver, String exitPolicy) {
        List<String> argv = new ArrayList<>();
        argv.add(prootPath);
        argv.add("--rootfs=" + rootfsDir);
        argv.add("--root-id");
        argv.add("--link2symlink");
        argv.add("--cwd=" + cwd);
        argv.add(exitPolicy);
        argv.add("--kernel-release=6.1.0-thothterm");
        argv.add("--bind=/dev");
        argv.add("--bind=/proc");
        argv.add("--bind=/sys");
        argv.add("--bind=/proc/mounts:/etc/mtab");
        if (bindResolver) {
            argv.add("--bind=" + resolverFile + ":/etc/resolv.conf");
        }
        return argv;
    }

    public Map<String, String> buildEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", LINUX_HOME);
        env.put("USER", "thoth");
        env.put("LOGNAME", "thoth");
        env.put("SHELL", "/bin/bash");
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

    /** Environment for the one-shot fake-root provisioning command (HOME=/root). */
    public Map<String, String> buildProvisioningEnvironment() {
        Map<String, String> env = buildEnvironment();
        env.put("HOME", "/root");
        env.put("USER", "root");
        env.put("LOGNAME", "root");
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
