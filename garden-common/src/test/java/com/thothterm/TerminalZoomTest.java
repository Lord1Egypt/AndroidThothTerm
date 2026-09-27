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

package com.thothterm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Locks the terminal zoom ladder. Zoom moves the existing global font-size
 * preference, so every reachable level must also be a value that preference can
 * hold.
 */
public class TerminalZoomTest {

    @Test
    public void defaultMatchesTheShippedPreferenceDefault() {
        assertEquals(10, TerminalZoom.DEFAULT_SIZE);
        assertEquals(TerminalZoom.DEFAULT_SIZE,
                TerminalZoom.clamp(TerminalZoom.DEFAULT_SIZE));
    }

    @Test
    public void boundsAreUsableOnAPhone() {
        assertEquals(6, TerminalZoom.MIN_SIZE);
        assertEquals(32, TerminalZoom.MAX_SIZE);
    }

    @Test
    public void zoomingInAndOutIsReversible() {
        int size = TerminalZoom.DEFAULT_SIZE;
        assertEquals(size, TerminalZoom.zoomOut(TerminalZoom.zoomIn(size)));
        assertEquals(size, TerminalZoom.zoomIn(TerminalZoom.zoomOut(size)));
    }

    @Test
    public void zoomingSaturatesRatherThanWrapping() {
        int size = TerminalZoom.MAX_SIZE;
        for (int i = 0; i < 10; i++) size = TerminalZoom.zoomIn(size);
        assertEquals(TerminalZoom.MAX_SIZE, size);
        assertTrue(TerminalZoom.isMax(size));

        size = TerminalZoom.MIN_SIZE;
        for (int i = 0; i < 10; i++) size = TerminalZoom.zoomOut(size);
        assertEquals(TerminalZoom.MIN_SIZE, size);
        assertTrue(TerminalZoom.isMin(size));
    }

    @Test
    public void everyStepIsReachableInBothDirections() {
        int size = TerminalZoom.MIN_SIZE;
        int steps = 1;
        while (!TerminalZoom.isMax(size)) {
            int next = TerminalZoom.zoomIn(size);
            assertTrue("zoomIn must strictly increase", next > size);
            size = next;
            steps++;
        }
        int back = 1;
        while (!TerminalZoom.isMin(size)) {
            int next = TerminalZoom.zoomOut(size);
            assertTrue("zoomOut must strictly decrease", next < size);
            size = next;
            back++;
        }
        assertEquals("the ladder must be symmetric", steps, back);
    }

    @Test
    public void legacyAndOutOfRangeValuesAreBroughtOntoTheLadder() {
        // 288 and 0 are selectable in the preference list but are not zoom levels:
        // 0 picks the bitmap renderer and 288 leaves too few columns to use.
        assertEquals(TerminalZoom.MAX_SIZE, TerminalZoom.clamp(288));
        assertEquals(TerminalZoom.MIN_SIZE, TerminalZoom.clamp(0));
        assertEquals(TerminalZoom.MIN_SIZE, TerminalZoom.clamp(-5));
        assertEquals(12, TerminalZoom.clamp(13));
    }

    @Test
    public void aPinchReturningToItsOriginRestoresTheOriginalSize() {
        // The gesture reports cumulative scale, so no drift may accumulate.
        assertEquals(TerminalZoom.DEFAULT_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, 1.0f));
    }

    @Test
    public void pinchingApartGrowsAndTogetherShrinks() {
        int base = TerminalZoom.DEFAULT_SIZE;
        assertTrue(TerminalZoom.scaled(base, 2.0f) > base);
        assertTrue(TerminalZoom.scaled(base, 0.5f) < base);
    }

    @Test
    public void pinchStaysWithinBounds() {
        assertEquals(TerminalZoom.MAX_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, 100f));
        assertEquals(TerminalZoom.MIN_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, 0.001f));
    }

    @Test
    public void degeneratePinchInputIsIgnored() {
        assertEquals(TerminalZoom.DEFAULT_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, 0f));
        assertEquals(TerminalZoom.DEFAULT_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, -1f));
        assertEquals(TerminalZoom.DEFAULT_SIZE,
                TerminalZoom.scaled(TerminalZoom.DEFAULT_SIZE, Float.NaN));
    }

    /**
     * Zoom must not invent values the font-size preference cannot store: every
     * level it can reach has to appear in entryvalues_fontsize_preference, or
     * the Settings list would show no selection after a pinch.
     */
    @Test
    public void everyZoomLevelIsAValueThePreferenceCanStore() throws Exception {
        java.io.File arrays = new java.io.File(
                "src/main/res/values/arraysNoLocalize.xml");
        if (!arrays.isFile()) {
            arrays = new java.io.File(
                    "term-ubuntu/src/main/res/values/arraysNoLocalize.xml");
        }
        assertTrue("arrays resource is missing", arrays.isFile());
        String xml = new String(java.nio.file.Files.readAllBytes(arrays.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        int from = xml.indexOf("\"entryvalues_fontsize_preference\"");
        assertTrue("font size entry values are missing", from >= 0);
        String block = xml.substring(from, xml.indexOf("</string-array>", from));

        java.util.Set<Integer> allowed = new java.util.HashSet<>();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("<item>(-?\\d+)</item>").matcher(block);
        while (m.find()) allowed.add(Integer.parseInt(m.group(1)));

        int size = TerminalZoom.MIN_SIZE;
        while (true) {
            assertTrue(size + "pt is not offered by the font size preference",
                    allowed.contains(size));
            if (TerminalZoom.isMax(size)) break;
            size = TerminalZoom.zoomIn(size);
        }
    }

    @Test
    public void extremesAreNotSimultaneouslyMinAndMax() {
        assertFalse(TerminalZoom.isMax(TerminalZoom.MIN_SIZE));
        assertFalse(TerminalZoom.isMin(TerminalZoom.MAX_SIZE));
    }
}
