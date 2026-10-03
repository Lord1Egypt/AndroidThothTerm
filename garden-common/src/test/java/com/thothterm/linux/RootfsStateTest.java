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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class RootfsStateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static DistroInfo image(String id, String sha) throws Exception {
        return DistroInfoTest.load(DistroInfoTest.valid("imageId", id, "sha256", sha));
    }

    @Test
    public void roundTripsAndMatches() throws Exception {
        File stateFile = new File(temporaryFolder.newFolder("linux"), "state.properties");
        DistroInfo info = image("garden-test-arm64-d88047a5c2a4", DistroInfoTest.SHA);

        RootfsState state = new RootfsState();
        state.imageId = info.imageId();
        state.distroVersion = info.distroVersion();
        state.architecture = info.architecture();
        state.imageSha256 = info.sha256();
        state.schemaVersion = info.schemaVersion();
        state.complete = true;
        state.write(stateFile);

        RootfsState read = RootfsState.read(stateFile);
        assertTrue(read.complete);
        assertEquals("garden-test-arm64-d88047a5c2a4", read.imageId);
        assertEquals(DistroInfoTest.SHA, read.imageSha256);
        assertTrue(read.matches(info));
    }

    @Test
    public void incompleteOrMismatchedStateNeverMatches() throws Exception {
        File stateFile = new File(temporaryFolder.newFolder("linux2"), "state.properties");
        DistroInfo info = image("garden-test-arm64-d88047a5c2a4", DistroInfoTest.SHA);

        RootfsState incomplete = new RootfsState();
        incomplete.imageId = info.imageId();
        incomplete.imageSha256 = info.sha256();
        incomplete.complete = false;
        incomplete.write(stateFile);
        assertFalse(RootfsState.read(stateFile).matches(info));

        RootfsState wrongSha = new RootfsState();
        wrongSha.imageId = info.imageId();
        wrongSha.imageSha256 = "deadbeef";
        wrongSha.complete = true;
        wrongSha.write(stateFile);
        assertFalse(RootfsState.read(stateFile).matches(info));

        assertFalse(RootfsState.read(new File(stateFile.getParentFile(), "absent")).matches(info));
    }

    /**
     * An app update that pins a newer image must not make a finished install
     * look missing: setup would re-extract, and extraction replaces the rootfs,
     * home directory included.
     */
    @Test
    public void aFinishedInstallStaysInstalledWhenThePinMoves() throws Exception {
        File stateFile = new File(temporaryFolder.newFolder("linux3"), "state.properties");
        DistroInfo installed = image("garden-test-arm64-d88047a5c2a4", DistroInfoTest.SHA);
        DistroInfo newerPin = image("garden-test-arm64-0123456789ab",
                "0123456789ab" + DistroInfoTest.SHA.substring(12));

        RootfsState state = new RootfsState();
        state.imageId = installed.imageId();
        state.imageSha256 = installed.sha256();
        state.schemaVersion = installed.schemaVersion() + 1;
        state.complete = true;
        state.write(stateFile);

        RootfsState read = RootfsState.read(stateFile);
        assertTrue(read.isInstalled());
        assertFalse(read.matches(newerPin));

        RootfsState unfinished = new RootfsState();
        unfinished.imageId = installed.imageId();
        unfinished.imageSha256 = installed.sha256();
        unfinished.write(stateFile);
        assertFalse(RootfsState.read(stateFile).isInstalled());
        assertFalse(RootfsState.read(new File(stateFile.getParentFile(), "absent")).isInstalled());
    }
}
