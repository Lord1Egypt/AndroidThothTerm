/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.thothterm.linux;

/** Pure progress math for the pinned, compressed Ubuntu archive. */
final class ExtractionProgress {
    private ExtractionProgress() {
    }

    static int percent(long consumedBytes, long totalBytes) {
        if (consumedBytes <= 0 || totalBytes <= 0) return 0;
        if (consumedBytes >= totalBytes) return 100;
        return (int) Math.min(99L, consumedBytes * 100L / totalBytes);
    }
}
