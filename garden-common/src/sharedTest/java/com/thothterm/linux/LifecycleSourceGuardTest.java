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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * Guards the managers themselves, which need Android to run: neither edition's
 * RootfsManager may delete or rename over the installed rootfs on its own,
 * and both must take every promotion through {@link RootfsLifecycle}.
 */
public class LifecycleSourceGuardTest {
    private static final String[] MANAGERS = {
            "garden-common/src/main/java/com/thothterm/linux/RootfsManager.java",
            "term-ubuntu/src/main/java/com/thothterm/linux/RootfsManager.java",
    };

    @Test
    public void managersNeverDeleteOrReplaceTheRootfsThemselves() throws Exception {
        for (String path : MANAGERS) {
            String source = read(path);
            assertFalse(path + " deletes the rootfs",
                    Pattern.compile("deleteTree\\([^;]*rootfsDir").matcher(source).find());
            assertFalse(path + " renames over the rootfs",
                    Pattern.compile("renameTo\\(\\s*rootfsDir").matcher(source).find());
            assertTrue(path, source.contains("RootfsLifecycle.promoteFreshInstall(fileOps, layout)"));
            assertTrue(path, source.contains("RootfsLifecycle.replaceSystemKeepingHome(fileOps, layout)"));
            assertTrue(path, source.contains("RootfsLifecycle.recover(fileOps, layout)"));
            assertTrue(path + " must decide from the lifecycle",
                    source.contains("RootfsLifecycle.assess("));
            assertFalse(path + " must not gate readiness on the pin",
                    source.contains("state.matches(image)"));
        }
    }

    @Test
    public void sessionStartNeverRunsGuestProvisioningSynchronously() throws Exception {
        for (String path : MANAGERS) {
            String source = read(path);
            int start = source.indexOf("public synchronized void prepareSession()");
            int end = source.indexOf("\n    }\n", start);
            String body = source.substring(start, end);
            assertFalse(path + " provisions on the UI thread", body.contains("runProvisioning("));
            assertFalse(path + " provisions on the UI thread", body.contains("ensureRealSudo("));
        }
    }

    /**
     * The terminal never waits for optional provisioning: setup reaches
     * TERMINAL_READY and hands off before anything optional is queued, and
     * the optional work never extracts, resets or deletes anything.
     */
    @Test
    public void theTerminalNeverWaitsForOptionalProvisioning() throws Exception {
        for (String path : MANAGERS) {
            String source = read(path);
            String prepare = body(source, "private void runPrepare()");
            for (String call : new String[]{"prepareAdminToolsOffline(", "ensureRealSudo(",
                    "installBundledSudo(", "finishInterruptedSudo(", "runProvisioning(",
                    "forceSudoSetuid("}) {
                assertFalse(path + ": runPrepare runs optional provisioning: " + call,
                        prepare.contains(call));
            }
            assertFalse(path, source.contains("prepareAdminToolsOffline"));
            // The one guest command setup itself runs: on Arch, an offline
            // pacman keyring (pacman-key --init/--populate from the image's
            // own keyring package) is part of the install record, as in
            // finishNewSystem. It needs no network.
            assertFalse(path, prepare.replace("if (isPacman()) ensurePacmanKeyringNow();", "")
                    .contains("ensurePacmanKeyringNow("));
            int ready = prepare.indexOf("SetupTimeline.Stage.TERMINAL_READY");
            int complete = prepare.indexOf("notifyComplete();");
            int handoff = prepare.indexOf("SetupTimeline.Stage.TERMINAL_HANDOFF");
            int optional = prepare.indexOf("scheduleAdminTools(\"after setup\"");
            assertTrue(path + ": TERMINAL_READY, then the handoff, then optional setup",
                    ready > 0 && ready < complete && complete < handoff && handoff < optional);

            String run = body(source, "private void runOptionalSetup(");
            for (String forbidden : new String[]{"stageVerifiedArchive(", "installFresh(",
                    "resetSystemKeepingHome(", "replaceSystemKeepingHome(", "deleteTree(",
                    "requestReset(", "writeState(", "setupRootfs("}) {
                assertFalse(path + ": optional setup must not " + forbidden, run.contains(forbidden));
            }
            assertTrue(path + ": optional setup reports its outcome",
                    run.contains("optionalSetup.finished(ok, why)"));
        }
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("no " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }

    @Test
    public void bothEditionsUseTheSharedExtractor() {
        assertFalse(new File(repo(), "term-ubuntu/src/main/java/com/thothterm/linux/TarballExtractor.java").exists());
        assertFalse(new File(repo(), "garden-common/src/main/java/com/thothterm/linux/TarballExtractor.java").exists());
        assertTrue(new File(repo(), "garden-common/src/shared/java/com/thothterm/linux/TarballExtractor.java").isFile());
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(repo(), path).toPath()), StandardCharsets.UTF_8);
    }

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }
}
