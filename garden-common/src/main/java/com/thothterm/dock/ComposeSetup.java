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

import com.thothterm.linux.RootfsManager;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Makes {@code docker compose} part of the installed app (ThothDock
 * docs/nextgen/adr/ADR-0006). The Compose client is Debian's own
 * {@code docker-compose} package, built from source by Debian and installed
 * by apt from the signed archive, so no unverified binary enters the guest
 * and the APK does not grow by the ~30 MB a bundled copy would cost.
 *
 * <p>It runs on the Engine Guard's background thread, only after the guard is
 * confirmed (so apt can never pull in a real engine), never delays the
 * terminal, and needs the network once. Without it (offline first run) it
 * does nothing and tries again at the next start. Each step is its own guest
 * command because a single one is limited to a few minutes; apt resumes where
 * an interrupted step stopped.</p>
 */
final class ComposeSetup {
    private static final String PACKAGE = "docker-compose";
    private static final int ATTEMPTS = 3;
    private static final long RETRY_MS = 60_000L;

    private ComposeSetup() {
    }

    /** Installs Compose into the guest unless it is already there. */
    static void ensure(RootfsManager rootfs) throws InterruptedException {
        File status = new File(rootfs.rootfsDir(), "var/lib/dpkg/status");
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (installed(status)) {
                ThothLog.i(LogCategory.RUNTIME, "Compose: installed");
                return;
            }
            try {
                String apt = "export DEBIAN_FRONTEND=noninteractive; ";
                rootfs.runGuestAdmin(apt + "dpkg --configure -a");
                rootfs.runGuestAdmin(apt + "apt-get update -qq");
                rootfs.runGuestAdmin(apt + "apt-get install -y -qq -o Dpkg::Options::=--force-confold " + PACKAGE);
            } catch (IOException e) {
                // No network, a dpkg lock held by the user, or a step that ran out of time.
                ThothLog.w(LogCategory.RUNTIME, "Compose install attempt " + attempt + " failed: " + e.getMessage());
            }
            if (installed(status)) {
                ThothLog.i(LogCategory.RUNTIME, "Compose: installed");
                return;
            }
            Thread.sleep(RETRY_MS);
        }
        ThothLog.w(LogCategory.RUNTIME, "Compose: not installed; will retry at the next start");
    }

    /** True when dpkg lists {@code docker-compose} as fully installed. */
    static boolean installed(File dpkgStatus) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(dpkgStatus), StandardCharsets.UTF_8))) {
            boolean match = false;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    match = false;
                } else if (line.equals("Package: " + PACKAGE)) {
                    match = true;
                } else if (match && line.startsWith("Status: ")) {
                    return line.endsWith(" installed");
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
