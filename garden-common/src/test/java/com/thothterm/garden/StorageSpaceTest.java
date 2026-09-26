/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.garden;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StorageSpaceTest {
    @Test
    public void requiresImageAndSafetyMargin() {
        long image = 122122240L;
        assertFalse(StorageSpace.isSufficient(image, image));
        assertTrue(StorageSpace.isSufficient(image + StorageSpace.SAFETY_MARGIN, image));
    }

    @Test
    public void unknownFilesystemValueFallsBackToExtractorCleanup() {
        assertTrue(StorageSpace.isSufficient(0, 122122240L));
    }
}
