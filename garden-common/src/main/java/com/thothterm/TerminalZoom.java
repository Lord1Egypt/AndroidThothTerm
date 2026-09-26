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

/**
 * Zoom policy for the terminal, expressed purely in font-size points.
 *
 * <p>Zoom is deliberately not a second preference model: it moves the existing
 * global {@code fontsize} preference, so the Settings list and a pinch gesture
 * always describe the same state. The steps are the rungs of that preference's
 * own list, which keeps every zoom level selectable from Settings too.
 *
 * <p>The ladder stops well short of the preference's 288pt ceiling. Past roughly
 * 32pt a phone-width terminal has so few columns that ordinary output stops
 * being usable, and 0 ("4 x 8 pixels") selects the bitmap renderer rather than a
 * scalable face, so neither belongs in a zoom range.
 */
public final class TerminalZoom {
    /** Rungs shared with {@code entryvalues_fontsize_preference}, ascending. */
    private static final int[] STEPS = {6, 7, 8, 9, 10, 12, 14, 16, 20, 24, 28, 32};

    /** Matches {@code pref_fontsize_default}. */
    public static final int DEFAULT_SIZE = 10;

    public static final int MIN_SIZE = STEPS[0];
    public static final int MAX_SIZE = STEPS[STEPS.length - 1];

    private TerminalZoom() {
    }

    /** Brings any stored value, including legacy ones, onto the nearest rung. */
    public static int clamp(int size) {
        if (size <= MIN_SIZE) return MIN_SIZE;
        if (size >= MAX_SIZE) return MAX_SIZE;
        return nearest(size);
    }

    public static int zoomIn(int size) {
        int at = indexOfNearest(size);
        return STEPS[Math.min(at + 1, STEPS.length - 1)];
    }

    public static int zoomOut(int size) {
        int at = indexOfNearest(size);
        return STEPS[Math.max(at - 1, 0)];
    }

    public static boolean isMax(int size) {
        return clamp(size) == MAX_SIZE;
    }

    public static boolean isMin(int size) {
        return clamp(size) == MIN_SIZE;
    }

    /**
     * Maps a pinch to a rung. {@code factor} is the gesture's cumulative scale
     * since it began, so the result depends only on where the fingers started
     * and where they are now — a pinch that returns to its origin returns the
     * original size rather than drifting.
     */
    public static int scaled(int baseSize, float factor) {
        if (factor <= 0f || Float.isNaN(factor)) return clamp(baseSize);
        return clamp(Math.round(clamp(baseSize) * factor));
    }

    private static int nearest(int size) {
        return STEPS[indexOfNearest(size)];
    }

    private static int indexOfNearest(int size) {
        int best = 0;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < STEPS.length; i++) {
            int gap = Math.abs(STEPS[i] - size);
            // On an exact tie prefer the lower rung, so zoomIn/zoomOut from a
            // midpoint stay symmetric instead of both moving the same way.
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }
}
