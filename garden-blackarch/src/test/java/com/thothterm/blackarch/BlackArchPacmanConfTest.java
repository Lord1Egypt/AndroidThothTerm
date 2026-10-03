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


package com.thothterm.blackarch;

import static com.thothterm.blackarch.RootfsTools.available;
import static com.thothterm.blackarch.RootfsTools.run;
import static com.thothterm.blackarch.RootfsTools.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * check-pacman-conf.sh: the pacman.conf an image ships keeps signature
 * enforcement and names exactly the expected repositories. The builder runs
 * the same script on the final file and refuses the image if it fails.
 */
public class BlackArchPacmanConfTest {
    /** The active lines of Arch Linux ARM's pacman.conf after the Android sandbox edit, plus the BlackArch section. */
    private static final String GOOD = ""
            + "[options]\n"
            + "HoldPkg     = pacman glibc\n"
            + "Architecture = aarch64\n"
            + "CheckSpace\n"
            + "ParallelDownloads = 5\n"
            + "DownloadUser = alpm\n"
            + "DisableSandboxFilesystem\n"
            + "#DisableSandboxSyscalls\n"
            + "SigLevel    = Required DatabaseOptional\n"
            + "LocalFileSigLevel = Optional\n"
            + "#RemoteFileSigLevel = Required\n"
            + "[core]\nInclude = /etc/pacman.d/mirrorlist\n"
            + "[extra]\nInclude = /etc/pacman.d/mirrorlist\n"
            + "[alarm]\nInclude = /etc/pacman.d/mirrorlist\n"
            + "[aur]\nInclude = /etc/pacman.d/mirrorlist\n"
            + "\n[blackarch]\n"
            + "# a comment, and a trailing one below\n"
            + "Server = https://blackarch.org/blackarch/$repo/os/$arch # primary\n";

    private File work;

    @Before
    public void setUp() throws Exception {
        assumeTrue("sh and awk are required", available("sh", "awk"));
        work = Files.createTempDirectory("pacman-conf").toFile();
    }

    @After
    public void clean() {
        if (work != null) RootfsTools.delete(work);
    }

    private RootfsTools.Result check(String conf) throws Exception {
        File f = new File(work, "pacman.conf");
        write(f, conf);
        File script = new File(BlackArchEditionTest.moduleDir(), "rootfs/check-pacman-conf.sh");
        return run(work, null, "sh", script.getPath(), f.getPath());
    }

    private void refuses(String conf, String reason) throws Exception {
        RootfsTools.Result r = check(conf);
        assertTrue("accepted: " + reason + "\n" + r.output, r.exit != 0);
        assertTrue(r.output, r.output.contains("check-pacman-conf: FAIL"));
    }

    private static String replace(String conf, String from, String to) {
        assertTrue(from, conf.contains(from));
        return conf.replace(from, to);
    }

    @Test
    public void acceptsTheExpectedConfiguration() throws Exception {
        RootfsTools.Result r = check(GOOD);
        assertEquals(r.output, 0, r.exit);
        assertTrue(r.output.contains("[core] [extra] [alarm] [aur] [blackarch]"));
    }

    @Test
    public void acceptsTheFileTheImageActuallyShips() throws Exception {
        File shipped = new File(BlackArchEditionTest.moduleDir(), "rootfs/blackarch-repo.conf");
        String section = new String(Files.readAllBytes(shipped.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        String base = GOOD.substring(0, GOOD.indexOf("\n[blackarch]"));
        RootfsTools.Result r = check(base + section);
        assertEquals(r.output, 0, r.exit);
    }

    @Test
    public void commentedOutWeakeningsAreNotSettings() throws Exception {
        assertEquals(0, check(replace(GOOD, "#RemoteFileSigLevel = Required", "#SigLevel = Never\n#SigLevel = Optional TrustAll")).exit);
        assertEquals(0, check(replace(GOOD, "SigLevel    = Required DatabaseOptional", "SigLevel = Required DatabaseOptional # Never")).exit);
    }

    @Test
    public void refusesAnyRelaxedGlobalSigLevel() throws Exception {
        for (String level : new String[]{"Never", "Optional", "TrustAll", "Required TrustAll", "PackageOptional",
                "Required PackageNever", "DatabaseOptional", "PackageTrustAll Required", "Required DatabaseTrustAll"}) {
            refuses(replace(GOOD, "SigLevel    = Required DatabaseOptional", "SigLevel = " + level), "SigLevel = " + level);
        }
    }

    @Test
    public void refusesALostOrDuplicatedGlobalSigLevel() throws Exception {
        refuses(replace(GOOD, "SigLevel    = Required DatabaseOptional\n", ""), "no SigLevel");
        refuses(replace(GOOD, "SigLevel    = Required DatabaseOptional\n",
                "SigLevel = Required\nSigLevel = Required DatabaseOptional\n"), "a second SigLevel");
    }

    @Test
    public void refusesASigLevelOnAnyRepository() throws Exception {
        refuses(GOOD + "SigLevel = Required\n", "a SigLevel in [blackarch], even a strong one");
        refuses(GOOD + "SigLevel = Optional TrustAll\n", "a weak SigLevel in [blackarch]");
        refuses(replace(GOOD, "[core]\n", "[core]\nSigLevel = Never\n"), "a SigLevel in [core]");
    }

    @Test
    public void refusesWeakFileLevels() throws Exception {
        refuses(replace(GOOD, "LocalFileSigLevel = Optional", "LocalFileSigLevel = TrustAll"), "LocalFileSigLevel TrustAll");
        refuses(replace(GOOD, "LocalFileSigLevel = Optional", "LocalFileSigLevel = Never"), "LocalFileSigLevel Never");
        refuses(replace(GOOD, "#RemoteFileSigLevel = Required", "RemoteFileSigLevel = Required"), "RemoteFileSigLevel set");
    }

    @Test
    public void refusesAWrongOrMissingBlackArchRepository() throws Exception {
        refuses(GOOD.substring(0, GOOD.indexOf("\n[blackarch]")), "no [blackarch]");
        refuses(replace(GOOD, "https://blackarch.org/blackarch/$repo/os/$arch", "http://blackarch.org/blackarch/$repo/os/$arch"), "plain HTTP");
        refuses(replace(GOOD, "https://blackarch.org/blackarch/$repo/os/$arch", "https://blackarch.org/blackarch/$repo/os/x86_64"), "an x86_64 path");
        refuses(replace(GOOD, "https://blackarch.org/blackarch/$repo/os/$arch", "https://example.org/blackarch/$repo/os/$arch"), "another host");
        refuses(GOOD + "Include = /etc/pacman.d/blackarch-mirrorlist\n", "an extra Include");
        refuses(GOOD + "Server = https://blackarch.org/blackarch/$repo/os/$arch\n", "two Servers");
        refuses(replace(GOOD, "\n[blackarch]\n", "\n[blackarch-testing]\n"), "another repository name");
    }

    @Test
    public void refusesChangedBaseRepositories() throws Exception {
        refuses(replace(GOOD, "[alarm]\nInclude = /etc/pacman.d/mirrorlist", "[alarm]\nServer = http://example.org/$arch"), "a Server in [alarm]");
        refuses(replace(GOOD, "[aur]\nInclude = /etc/pacman.d/mirrorlist\n", ""), "a missing repository");
        refuses(replace(GOOD, "[core]\nInclude = /etc/pacman.d/mirrorlist\n[extra]\nInclude = /etc/pacman.d/mirrorlist\n",
                "[extra]\nInclude = /etc/pacman.d/mirrorlist\n[core]\nInclude = /etc/pacman.d/mirrorlist\n"), "a different order");
        refuses(GOOD + "[testing]\nInclude = /etc/pacman.d/mirrorlist\n", "an additional repository");
    }

    @Test
    public void refusesChangedOptions() throws Exception {
        refuses(replace(GOOD, "Architecture = aarch64", "Architecture = x86_64"), "the wrong architecture");
        refuses(replace(GOOD, "Architecture = aarch64", "Architecture = auto"), "architecture auto");
        refuses(replace(GOOD, "Architecture = aarch64\n", ""), "no architecture");
        refuses(replace(GOOD, "CheckSpace\n", "CheckSpace\nXferCommand = /usr/bin/curl %u\n"), "a custom downloader");
        refuses(replace(GOOD, "DisableSandboxFilesystem\n", "DisableSandbox\n"), "every sandbox off");
        refuses(replace(GOOD, "#DisableSandboxSyscalls", "DisableSandboxSyscalls"), "the syscall sandbox off");
        refuses(replace(GOOD, "DownloadUser = alpm", "DownloadUser = root"), "a root downloader");
        refuses(replace(GOOD, "DisableSandboxFilesystem\n", ""), "the Android filesystem edit missing");
    }
}
