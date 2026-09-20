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

/**
 * Guards the provenance of the PRoot runtime.
 *
 * <p>F-Droid only distributes binaries it built itself, so the runtime has to
 * come from sources in this repository at pinned revisions. These checks fail if
 * a submodule is re-pointed, a pin is edited in one place but not the other, or
 * a download creeps back into the build.
 */
public class ProotSourceBuildTest {
    private static final String PROOT_COMMIT = "7266fb3e8516535682f5a9c8f3a7e70f6506eddb";
    private static final String SHMEM_COMMIT = "7f0bd7e25dbdd146265aff7c6a890029e374622d";

    private static File repoRoot() {
        File here = new File("").getAbsoluteFile();
        // Tests run from either the module or the repository root.
        if (new File(here, "third_party").isDirectory()) return here;
        return here.getParentFile();
    }

    private static String read(String relative) throws Exception {
        File file = new File(repoRoot(), relative);
        assertTrue("missing " + relative, file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void submodulesArePinnedToTheAuditedRevisions() throws Exception {
        String modules = read(".gitmodules");
        assertTrue("proot submodule is missing",
                modules.contains("third_party/proot"));
        assertTrue("libandroid-shmem submodule is missing",
                modules.contains("third_party/libandroid-shmem"));
        assertTrue("proot must come from termux/proot",
                modules.contains("termux/proot.git"));

        String script = read("term-ubuntu/tools/build-proot.sh");
        assertTrue("build script must pin the proot commit",
                script.contains(PROOT_COMMIT));
        assertTrue("build script must pin the libandroid-shmem commit",
                script.contains(SHMEM_COMMIT));
        assertTrue("build script must verify the pins at build time",
                script.contains("check_commit"));
    }

    @Test
    public void tallocIsVendoredAsSource() throws Exception {
        // talloc publishes release tarballs but no git repository, so the two
        // files PRoot needs are vendored verbatim instead of submoduled.
        String talloc = read("third_party/talloc/talloc.c");
        assertTrue("vendored talloc.c looks wrong", talloc.contains("talloc_named"));
        assertTrue("talloc.h must be vendored too",
                new File(repoRoot(), "third_party/talloc/talloc.h").isFile());
    }

    @Test
    public void theProotBuildDownloadsNothing() throws Exception {
        String script = read("term-ubuntu/tools/build-proot.sh");
        for (String forbidden : new String[]{"curl ", "wget ", "git clone", "git fetch"}) {
            assertFalse("the PRoot build must not fetch anything: " + forbidden,
                    script.contains(forbidden));
        }
    }

    @Test
    public void everyPatchIsDocumented() throws Exception {
        File patches = new File(repoRoot(), "term-ubuntu/patches");
        File[] files = patches.listFiles((dir, name) -> name.endsWith(".patch"));
        assertTrue("patch directory is missing", files != null);
        for (File patch : files) {
            String text = new String(Files.readAllBytes(patch.toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(patch.getName() + " needs a Subject: line",
                    text.contains("Subject:"));
            assertTrue(patch.getName() + " must record the upstream it applies to",
                    text.contains("Upstream:"));
        }
    }

    @Test
    public void theUbuntuPayloadStagesOutsideTheSharedSourceSet() throws Exception {
        // Anything under src/main reaches every flavour, including fdroid.
        String script = read("term-ubuntu/tools/prepare-assets.sh");
        assertTrue("the rootfs must stage into the full flavour",
                script.contains("src/full/assets/ubuntu"));
        assertTrue("the admin packages must stage into the full flavour",
                script.contains("src/full/assets/sudo"));
        assertFalse("no distribution payload may stage into src/main",
                script.contains("src/main/assets/sudo"));
        assertFalse("the rootfs archive must not stage into src/main",
                script.contains("src/main/assets/ubuntu/ubuntu-base"));
    }

    @Test
    public void prepareAssetsNoLongerFetchesAPrebuiltRuntime() throws Exception {
        String script = read("term-ubuntu/tools/prepare-assets.sh");
        assertFalse("the prebuilt PRoot bundle must no longer be fetched",
                script.contains("ProotX-Assets-Support"));
    }

    @Test
    public void everyPinnedDownloadIsChecksummedAndFailsClosed() throws Exception {
        String script = read("term-ubuntu/tools/prepare-assets.sh");
        assertTrue("downloads must be checksum verified",
                script.contains("checksum mismatch"));
        assertFalse("no floating 'latest' URL may be used",
                script.contains("/latest/"));
        assertTrue("the Ubuntu URL must be pinned to an exact release",
                script.contains("releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz"));
        assertTrue("the Ubuntu archive must have a pinned sha256",
                script.contains("UBUNTU_SHA=\""));

        // The runtime reads the same pin, so the two must agree or the fdroid
        // flavour would download something the full flavour never embedded.
        String properties = read("term-ubuntu/src/main/assets/ubuntu/image.properties");
        assertTrue("image.properties must carry the same source URL",
                properties.contains(
                        "releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz"));
        assertTrue("image.properties must carry the upstream sha256",
                properties.contains("upstreamSha256="));
    }
}
