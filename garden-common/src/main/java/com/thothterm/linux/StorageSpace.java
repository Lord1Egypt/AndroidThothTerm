/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

/** Conservative first-run storage check kept separate for unit testing. */
final class StorageSpace {
    static final long SAFETY_MARGIN = 32L * 1024L * 1024L;

    private StorageSpace() {
    }

    static boolean isSufficient(long usableBytes, long uncompressedBytes) {
        // Some filesystems report zero when the value is unavailable; extraction
        // still has robust failure cleanup in that case.
        if (usableBytes <= 0 || uncompressedBytes <= 0) return true;
        return usableBytes >= uncompressedBytes + SAFETY_MARGIN;
    }
}
