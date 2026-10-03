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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Deterministic versions of Codex QA round 3's chmod race: the path is swapped
 * for a symlink to an outside object at the exact points where the 9677a88
 * implementation was exposed. The outside object must keep its mode.
 * {@code FileOpsContract.chmodSwapRacesNeverReachTheTarget} runs the same
 * property as a free-running race, also against AndroidFileOps on a device.
 */
public class JvmFileOpsRaceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Codex's interleaving: the swap happens right after one of the
     * implementation's lstat observations -- the 9677a88 fallback re-checked
     * the type and then chmodded the path, and the outside sentinel went
     * 0600 -> 0640. How many observations an implementation makes is its own
     * business, so the scenario is replayed with the swap after the 1st, the
     * 2nd, ... observation, until one runs without reaching it.
     */
    @Test
    public void swapAfterAnyTypeObservationNeverReachesTheTarget() throws Exception {
        int swapsMade = 0;
        for (int swapAt = 1; ; swapAt++) {
            Path dir = temporaryFolder.newFolder("codex-" + swapAt).toPath();
            final Path checked = file(dir, "checked", 0200);
            final Path aside = dir.resolve("aside");
            final Path outside = file(dir, "outside", 0600);
            final int at = swapAt;
            final int[] observed = {0};
            final boolean[] swapped = {false};
            JvmFileOps ops = new JvmFileOps() {
                @Override
                public Type type(File f) throws IOException {
                    Type type = super.type(f);
                    if (f.toPath().equals(checked) && ++observed[0] == at) {
                        swapForSymlink(checked, aside, outside);
                        swapped[0] = true;
                    }
                    return type;
                }
            };
            try {
                ops.chmodNoFollow(checked.toFile(), 0640);
            } catch (IOException refused) {
                // refused: ELOOP from the no-follow open, or the helper's fstat
            }
            assertEquals("outside sentinel, swap after observation " + swapAt, 0600, mode(outside));
            if (!swapped[0]) break;
            swapsMade++;
            assertEquals("the swapped-away file, swap after observation " + swapAt, 0200, mode(aside));
        }
        assertTrue("the swap ran at least once", swapsMade > 0);
    }

    /** The swap at the seam between the failed read-only open and the O_PATH chmod. */
    @Test
    public void swapBeforeTheDescriptorChmodNeverReachesTheTarget() throws Exception {
        Path dir = temporaryFolder.newFolder("seam").toPath();
        final Path checked = file(dir, "checked", 0000);
        final Path aside = dir.resolve("aside");
        final Path outside = file(dir, "outside", 0600);
        final int[] swaps = {0};
        JvmFileOps ops = new JvmFileOps() {
            @Override
            protected void beforeDescriptorChmod(File f) throws IOException {
                swapForSymlink(checked, aside, outside);
                swaps[0]++;
            }
        };
        try {
            ops.chmodNoFollow(checked.toFile(), 0640);
            fail("chmod went ahead on a path that is now a symlink");
        } catch (IOException expected) {
            // the helper's descriptor is the symlink itself: refused
        }
        assertEquals(1, swaps[0]);
        assertEquals(0600, mode(outside));
        assertEquals(0000, mode(aside));
    }

    /** The same for an owner-unreadable directory swapped for a symlink to an outside directory. */
    @Test
    public void directorySwapBeforeTheDescriptorChmodNeverReachesTheTarget() throws Exception {
        Path dir = temporaryFolder.newFolder("dir-seam").toPath();
        final Path checked = Files.createDirectory(dir.resolve("checked"));
        Files.setPosixFilePermissions(checked, ExtractorSecurityCases.JvmPermissions.of(0300));
        final Path aside = dir.resolve("aside");
        final Path outside = Files.createDirectory(dir.resolve("outside"));
        Files.setPosixFilePermissions(outside, ExtractorSecurityCases.JvmPermissions.of(0700));
        JvmFileOps ops = new JvmFileOps() {
            @Override
            protected void beforeDescriptorChmod(File f) throws IOException {
                swapForSymlink(checked, aside, outside);
            }
        };
        try {
            ops.chmodNoFollow(checked.toFile(), 01777);
            fail("chmod went ahead on a path that is now a symlink");
        } catch (IOException expected) {
            // refused
        }
        assertEquals(0700, mode(outside));
        assertEquals(0300, mode(aside));
    }

    /** Without a swap the descriptor path is what applies the mode (0000 -> 0110, as Arch's helper). */
    @Test
    public void descriptorChmodAppliesTheMaskedMode() throws Exception {
        Path f = file(temporaryFolder.newFolder("plain").toPath(), "helper", 0000);
        new JvmFileOps().chmodNoFollow(f.toFile(), 04110);
        assertEquals(0110, mode(f));
    }

    private static void swapForSymlink(Path path, Path aside, Path target) throws IOException {
        Files.move(path, aside, StandardCopyOption.ATOMIC_MOVE);
        Files.createSymbolicLink(path, target);
    }

    private static Path file(Path dir, String name, int mode) throws IOException {
        Path f = Files.write(dir.resolve(name), name.getBytes("UTF-8"));
        Files.setPosixFilePermissions(f, ExtractorSecurityCases.JvmPermissions.of(mode));
        return f;
    }

    private static int mode(Path p) throws IOException {
        return ((Integer) Files.getAttribute(p, "unix:mode", LinkOption.NOFOLLOW_LINKS)) & 07777;
    }
}
