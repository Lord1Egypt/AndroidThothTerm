/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.thothterm.linux;

/** Pure, idempotent migration of the small managed block in ~/.bashrc. */
final class ShellIntegration {
    private static final String LEGACY_PROMPT = "PS1='thoth@android:\\w\\$ '";
    private static final String BEGIN = "# >>> ThothTerm managed shell integration >>>";
    private static final String END = "# <<< ThothTerm managed shell integration <<<";

    private ShellIntegration() {
    }

    static String updateBashrc(String original) {
        if (original == null) original = "";
        StringBuilder updated = new StringBuilder();
        String[] lines = original.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty()) continue;
            if (!LEGACY_PROMPT.equals(line)) updated.append(line).append('\n');
        }
        if (updated.indexOf(BEGIN) < 0) {
            updated.append(BEGIN).append('\n')
                    .append("[ -r /etc/profile.d/thothterm-ubuntu.sh ] && "
                            + ". /etc/profile.d/thothterm-ubuntu.sh\n")
                    .append(END).append('\n');
        }
        return updated.toString();
    }
}
