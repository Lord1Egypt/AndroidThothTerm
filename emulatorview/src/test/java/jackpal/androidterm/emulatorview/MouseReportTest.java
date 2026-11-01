/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * Mouse reports. OpenCode and other full-screen TUIs ask for SGR reports
 * (DECSET 1006) and scroll their own content on wheel events; ThothTerm sent
 * only the X10 form, and only with the "send mouse events" setting on.
 */
public class MouseReportTest {
    private static String text(byte[] b) {
        return new String(b, StandardCharsets.US_ASCII);
    }

    @Test
    public void sgrWheelAndClick() {
        assertEquals("\033[<64;10;5M", text(MouseReport.encode(MouseReport.WHEEL_UP, false, 10, 5, true)));
        assertEquals("\033[<65;10;5M", text(MouseReport.encode(MouseReport.WHEEL_DOWN, false, 10, 5, true)));
        assertEquals("\033[<0;1;1M", text(MouseReport.encode(MouseReport.BUTTON_LEFT, false, 1, 1, true)));
        assertEquals("\033[<0;1;1m", text(MouseReport.encode(MouseReport.BUTTON_LEFT, true, 1, 1, true)));
    }

    @Test
    public void sgrHasNoPositionLimit() {
        assertEquals("\033[<65;300;240M", text(MouseReport.encode(MouseReport.WHEEL_DOWN, false, 300, 240, true)));
    }

    @Test
    public void x10FormAsBefore() {
        assertArrayEquals(new byte[]{'\033', '[', 'M', 32 + 64, 32 + 10, 32 + 5},
                MouseReport.encode(MouseReport.WHEEL_UP, false, 10, 5, false));
        // An X10 release is button 3.
        assertArrayEquals(new byte[]{'\033', '[', 'M', 32 + 3, 32 + 1, 32 + 1},
                MouseReport.encode(MouseReport.BUTTON_LEFT, true, 1, 1, false));
        assertNull(MouseReport.encode(MouseReport.WHEEL_UP, false, 224, 5, false));
        assertNull(MouseReport.encode(MouseReport.WHEEL_UP, false, 0, 5, true));
    }

    private static TerminalEmulator emulatorAfter(String input) {
        TermSession session = new TermSession();
        TranscriptScreen screen = new TranscriptScreen(80, 100, 24, new ColorScheme(0xffffffff, 0xff000000));
        TerminalEmulator emulator = new TerminalEmulator(session, screen, 80, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        byte[] bytes = input.getBytes(StandardCharsets.US_ASCII);
        emulator.append(bytes, 0, bytes.length);
        return emulator;
    }

    /** What OpenCode sends on start-up, and what it sends on exit. */
    @Test
    public void emulatorTracksTheModesATuiSets() {
        TerminalEmulator e = emulatorAfter("\033[?1049h\033[?1000h\033[?1002h\033[?1003h\033[?1006h");
        assertEquals(1003, e.getMouseTrackingMode());
        assertTrue(e.isMouseSgrMode());
        byte[] off = "\033[?1006l\033[?1003l\033[?1049l".getBytes(StandardCharsets.US_ASCII);
        e.append(off, 0, off.length);
        assertEquals(0, e.getMouseTrackingMode());
        assertFalse(e.isMouseSgrMode());
    }

    /** Several modes in one sequence all take effect, as in xterm. */
    @Test
    public void severalModesInOneSequence() {
        TerminalEmulator e = emulatorAfter("\033[?1049;1002;1006h");
        assertEquals(1002, e.getMouseTrackingMode());
        assertTrue(e.isMouseSgrMode());
        byte[] off = "\033[?1002;1006;1049l".getBytes(StandardCharsets.US_ASCII);
        e.append(off, 0, off.length);
        assertEquals(0, e.getMouseTrackingMode());
        assertFalse(e.isMouseSgrMode());
    }

    @Test
    public void resetClearsMouseModesLeftByAKilledProgram() {
        TerminalEmulator e = emulatorAfter("\033[?1002h\033[?1006h");
        e.reset();
        assertEquals(0, e.getMouseTrackingMode());
        assertFalse(e.isMouseSgrMode());
    }
}
