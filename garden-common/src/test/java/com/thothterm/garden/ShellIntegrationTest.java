/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.garden;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShellIntegrationTest {
    @Test
    public void migrationPreservesUserContentAndRemovesLegacyPrompt() {
        String result = ShellIntegration.updateBashrc(
                "alias ll='ls -al'\nPS1='thoth@android:\\w\\$ '\n");
        assertTrue(result.contains("alias ll='ls -al'"));
        assertTrue(result.contains("/etc/profile.d/thothterm-ubuntu.sh"));
        assertFalse(result.contains("thoth@android"));
    }

    @Test
    public void updateIsIdempotent() {
        String once = ShellIntegration.updateBashrc("# user file\n");
        assertEquals(once, ShellIntegration.updateBashrc(once));
    }
}
