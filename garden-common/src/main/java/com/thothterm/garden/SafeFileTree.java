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

package com.thothterm.garden;

import java.io.File;
import java.io.IOException;

/**
 * Deletes an app-private tree safely.
 *
 * <ul>
 *   <li>operates only beneath the exact known root (lexical containment);</li>
 *   <li>never follows symbolic links — the link itself is deleted, not its
 *       target;</li>
 *   <li>restores owner rwx on directories before descending/removing so a tree
 *       left with restrictive modes by a failed extraction can still be
 *       cleaned;</li>
 *   <li>deletes children before parents and fails explicitly if anything cannot
 *       be removed.</li>
 * </ul>
 */
public final class SafeFileTree {
    /** Owner rwx: enough to list, descend, and unlink while cleaning. */
    public static final int OWNER_RWX = 0700;

    private SafeFileTree() {
    }

    public static void deleteTree(FileOps ops, File root, File node) throws IOException {
        if (root == null || node == null) return;
        deleteNode(ops, root.getAbsolutePath(), node);
    }

    private static void deleteNode(FileOps ops, String rootPath, File node) throws IOException {
        String path = node.getAbsolutePath();
        if (!(path.equals(rootPath) || path.startsWith(rootPath + File.separator))) {
            throw new IOException("Refusing to delete outside the staging root: " + node);
        }

        if (ops.isSymlink(node)) {
            deleteNodeEntry(node);
            return;
        }
        if (!ops.exists(node)) return;

        if (ops.isDirectory(node)) {
            ops.setMode(node, OWNER_RWX);
            File[] children = node.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteNode(ops, rootPath, child);
                }
            }
            deleteNodeEntry(node);
        } else {
            deleteNodeEntry(node);
        }
    }

    private static void deleteNodeEntry(File file) throws IOException {
        if (!file.delete()) {
            throw new IOException("Cannot delete: " + file);
        }
    }
}
