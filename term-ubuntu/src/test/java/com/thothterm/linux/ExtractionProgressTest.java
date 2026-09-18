/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExtractionProgressTest {
    @Test
    public void reportsCompressedBytesTruthfully() {
        assertEquals(0, ExtractionProgress.percent(0, 1000));
        assertEquals(12, ExtractionProgress.percent(120, 1000));
        assertEquals(99, ExtractionProgress.percent(999, 1000));
        assertEquals(100, ExtractionProgress.percent(1000, 1000));
        assertEquals(100, ExtractionProgress.percent(1200, 1000));
    }

    @Test
    public void sequenceIsMonotonic() {
        int previous = 0;
        for (long consumed = 0; consumed <= 35092106; consumed += 32768) {
            int current = ExtractionProgress.percent(consumed, 35092106);
            assertTrue(current >= previous);
            previous = current;
        }
    }

    @Test
    public void unknownTotalStaysIndeterminateValue() {
        assertEquals(0, ExtractionProgress.percent(100, 0));
    }
}
