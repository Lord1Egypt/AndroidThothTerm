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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.Charset;

public class RootfsStateTest {
    private static final Charset ASCII = Charset.forName("US-ASCII");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static ImageInfo image(String id, String sha) throws Exception {
        String text = "imageId=" + id + "\n"
                + "ubuntuVersion=26.04.1\n"
                + "architecture=aarch64\n"
                + "upstreamSha256=" + sha + "\n"
                + "compressedSize=35092106\n"
                + "schemaVersion=1\n";
        return ImageInfo.load(new ByteArrayInputStream(text.getBytes(ASCII)));
    }

    @Test
    public void roundTripsAndMatches() throws Exception {
        File stateFile = new File(temporaryFolder.newFolder("linux"), "state.properties");
        ImageInfo info = image("ubuntu-26.04.1-base-arm64", "5a19");

        RootfsState state = new RootfsState();
        state.imageId = info.imageId();
        state.ubuntuVersion = info.ubuntuVersion();
        state.architecture = info.architecture();
        state.imageSha256 = info.upstreamSha256();
        state.schemaVersion = info.schemaVersion();
        state.complete = true;
        state.write(stateFile);

        RootfsState read = RootfsState.read(stateFile);
        assertTrue(read.complete);
        assertEquals("ubuntu-26.04.1-base-arm64", read.imageId);
        assertEquals("5a19", read.imageSha256);
        assertTrue(read.matches(info));
    }

    @Test
    public void incompleteOrMismatchedStateNeverMatches() throws Exception {
        File stateFile = new File(temporaryFolder.newFolder("linux2"), "state.properties");
        ImageInfo info = image("ubuntu-26.04.1-base-arm64", "5a19");

        RootfsState incomplete = new RootfsState();
        incomplete.imageId = info.imageId();
        incomplete.imageSha256 = info.upstreamSha256();
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
}
