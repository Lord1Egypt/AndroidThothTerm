/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import java.nio.charset.StandardCharsets;

/**
 * The bytes of one xterm mouse report. Buttons: 0 = left, 64/65 = wheel up/down.
 * Coordinates are 1-based cells.
 */
final class MouseReport {
    static final int BUTTON_LEFT = 0;
    static final int WHEEL_UP = 64;
    static final int WHEEL_DOWN = 65;

    private MouseReport() {
    }

    /**
     * {@code sgr}: the SGR form (DECSET 1006), ESC [ &lt; b ; x ; y M, with a
     * final m for a release; any position. Otherwise the X10 form, ESC [ M
     * followed by three bytes of value + 32, where a release is button 3 and
     * positions above 223 cannot be sent. Returns null when the report cannot
     * be encoded.
     */
    static byte[] encode(int button, boolean release, int x, int y, boolean sgr) {
        if (x < 1 || y < 1 || button < 0) return null;
        if (sgr) {
            return ("\033[<" + button + ";" + x + ";" + y + (release ? "m" : "M"))
                    .getBytes(StandardCharsets.US_ASCII);
        }
        int code = release ? 3 : button;
        if (code > 255 - 32 || x > 255 - 32 || y > 255 - 32) return null;
        return new byte[]{'\033', '[', 'M', (byte) (32 + code), (byte) (32 + x), (byte) (32 + y)};
    }
}
