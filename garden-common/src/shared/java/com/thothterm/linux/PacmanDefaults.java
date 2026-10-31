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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Two pacman defaults for Arch Linux ARM under PRoot, from the device review
 * of ThothTerm Rolling on F-Droid (fdroiddata MR !51045):
 *
 * <ul>
 * <li>The image's mirrorlist enables only the geo-IP redirector, so pacman has
 * no second server when the redirect lands on a slow mirror ("Operation too
 * slow", the first {@code pacman -Syu} failed twice). The country mirrors the
 * same file lists are enabled after it as fallbacks; pacman tries servers in
 * order. Only a mirrorlist still byte-identical to the image's is rewritten:
 * one the user changed, or a newer pacman-mirrorlist, is never touched.</li>
 * <li>systemd's hwdb hook ends every upgrade that touches udev's hardware
 * database with "Failed to write database /usr/lib/udev/hwdb.bin" and "error:
 * command failed to execute correctly". There is no udev under PRoot, so the
 * hook is masked the way Arch documents: a same-named symlink to /dev/null in
 * /etc/pacman.d/hooks. A file the user put there is left alone.</li>
 * </ul>
 */
public final class PacmanDefaults {
    static final String HWDB_HOOK = "25-systemd-hwdb.hook";

    /** The mirrorlist of arch-rootfs-aarch64-03a4c669ed6f (pacman-mirrorlist 20260411-2). */
    static final String IMAGE_MIRRORLIST_SHA256 =
            "e25f0bc284287342b221377d2a0ca0203eaf13f52d369d6676c4730a8e2b1de1";

    static final String FALLBACK_NOTE =
            "## ThothTerm enabled the country mirrors below as fallbacks: pacman tries\n"
            + "## them in order when the redirector's mirror fails or is too slow.\n";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private PacmanDefaults() {
    }

    /** Masks the hwdb hook. @return true when the symlink was created now */
    public static boolean maskHwdbHook(FileOps ops, File root) throws IOException {
        File hooks = new File(root, "etc/pacman.d/hooks");
        ManagedFiles.ensureDirectories(ops, root, hooks);
        File link = new File(hooks, HWDB_HOOK);
        if (ops.type(link) != FileOps.Type.NONE) return false;
        ops.symlink("/dev/null", link);
        return true;
    }

    /** Enables the fallback mirrors in an unmodified mirrorlist. @return true when rewritten now */
    public static boolean enableFallbackMirrors(FileOps ops, File root) throws IOException {
        File list = new File(root, "etc/pacman.d/mirrorlist");
        String text = ManagedFiles.read(ops, root, list);
        if (!IMAGE_MIRRORLIST_SHA256.equals(sha256(text))) return false;
        return ManagedFiles.writeIfChanged(ops, root, list, withFallbacks(text), -1);
    }

    /** The image's mirrorlist with every commented-out country server enabled. */
    static String withFallbacks(String text) {
        StringBuilder out = new StringBuilder(text.length() + FALLBACK_NOTE.length());
        boolean noted = false;
        for (String line : text.split("\n", -1)) {
            if (!noted && line.startsWith("### Mirrors by country")) {
                out.append(FALLBACK_NOTE);
                noted = true;
            }
            out.append(line.startsWith("# Server = ") ? line.substring(2) : line).append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
