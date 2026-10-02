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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Resolves a guest path the way the guest sees it: symlinks are interpreted
 * inside the rootfs (an absolute target starts again at the rootfs, {@code ..}
 * never climbs above it), as PRoot does at run time. The app uses it whenever
 * it must look at or write a guest file from the Android side, where a plain
 * {@code java.io.File} would follow an absolute guest symlink to an Android
 * path instead.
 */
public final class GuestPaths {
    /** Linux's own limit on symlink hops in one resolution. */
    private static final int MAX_HOPS = 40;

    private GuestPaths() {
    }

    /**
     * @param followFinal whether a symlink in the last component is followed
     * @return the host file inside {@code root}, or null on a symlink loop.
     *         The file need not exist.
     */
    public static File resolve(FileOps ops, File root, String guestPath, boolean followFinal)
            throws IOException {
        Deque<String> pending = new ArrayDeque<>();
        push(pending, guestPath);
        List<String> resolved = new ArrayList<>();
        int hops = 0;
        while (!pending.isEmpty()) {
            String part = pending.pollFirst();
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!resolved.isEmpty()) resolved.remove(resolved.size() - 1);
                continue;
            }
            File candidate = toFile(root, resolved, part);
            boolean last = pending.isEmpty() || onlyDots(pending);
            if ((!last || followFinal) && ops.type(candidate) == FileOps.Type.SYMLINK) {
                if (++hops > MAX_HOPS) return null;
                String target = ops.readlink(candidate);
                if (target.startsWith("/")) resolved.clear();
                push(pending, target);
                continue;
            }
            resolved.add(part);
        }
        return toFile(root, resolved, null);
    }

    /** The guest path of a host file inside {@code root}, e.g. "/etc/hosts". */
    public static String guestPath(File root, File file) {
        String base = root.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.equals(base)) return "/";
        if (!path.startsWith(base + File.separator)) {
            throw new IllegalArgumentException(file + " is not inside " + root);
        }
        return path.substring(base.length());
    }

    private static boolean onlyDots(Deque<String> pending) {
        for (String part : pending) {
            if (!part.isEmpty() && !part.equals(".")) return false;
        }
        return true;
    }

    private static void push(Deque<String> pending, String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) pending.addFirst(parts[i]);
    }

    private static File toFile(File root, List<String> parts, String extra) {
        File file = root;
        for (String part : parts) file = new File(file, part);
        return extra == null ? file : new File(file, extra);
    }
}
