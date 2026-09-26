/*
 * Copyright (C) 2026 ThothTerm.
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

/**
 * Pure, idempotent edit of the small managed block in the guest's ~/.bashrc.
 * The session shell is a non-login shell, so this block is what brings in the
 * Garden profile script.
 */
final class ShellIntegration {
    static final String PROFILE_SCRIPT = "/etc/profile.d/thothterm-garden.sh";
    private static final String BEGIN = "# >>> ThothTerm managed shell integration >>>";
    private static final String END = "# <<< ThothTerm managed shell integration <<<";

    private ShellIntegration() {
    }

    static String updateBashrc(String original) {
        if (original == null) original = "";
        if (original.contains(BEGIN)) return original;
        StringBuilder updated = new StringBuilder(original);
        if (updated.length() > 0 && updated.charAt(updated.length() - 1) != '\n') {
            updated.append('\n');
        }
        return updated.append(BEGIN).append('\n')
                .append("[ -r " + PROFILE_SCRIPT + " ] && . " + PROFILE_SCRIPT + "\n")
                .append(END).append('\n')
                .toString();
    }
}
