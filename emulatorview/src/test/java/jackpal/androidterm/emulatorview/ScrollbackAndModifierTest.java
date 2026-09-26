/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ScrollbackAndModifierTest {
    private static final int COLUMNS = 20;
    private static final int ROWS = 4;

    @Test
    public void csi3JDropsScrollbackAndKeepsTheScreenAndCursor() {
        TerminalEmulator emulator = emulator();
        feed(emulator, "l1\r\nl2\r\nl3\r\nl4\r\nl5\r\nl6\r\nl7");
        TranscriptScreen screen = emulator.getScreen();
        assertEquals(3, screen.getActiveTranscriptRows());
        String visible = visibleText(screen);
        int row = emulator.getCursorRow();
        int col = emulator.getCursorCol();

        feed(emulator, "\033[3J");

        assertEquals(0, screen.getActiveTranscriptRows());
        assertEquals(visible, visibleText(screen));
        assertEquals("l4\nl5\nl6\nl7", visible);
        assertEquals(row, emulator.getCursorRow());
        assertEquals(col, emulator.getCursorCol());
    }

    @Test
    public void newOutputAfterClearingScrollsIntoAFreshTranscript() {
        TerminalEmulator emulator = emulator();
        feed(emulator, "l1\r\nl2\r\nl3\r\nl4\r\nl5\r\nl6");
        emulator.clearScrollback();

        feed(emulator, "\r\nl7\r\nl8");

        TranscriptScreen screen = emulator.getScreen();
        assertEquals(2, screen.getActiveTranscriptRows());
        assertEquals("l3\nl4\nl5\nl6\nl7\nl8", screen.getTranscriptText().trim());
    }

    @Test
    public void clearingEmptyScrollbackIsHarmless() {
        TerminalEmulator emulator = emulator();
        feed(emulator, "only");

        feed(emulator, "\033[3J");

        assertEquals(0, emulator.getScreen().getActiveTranscriptRows());
        assertEquals("only", emulator.getScreen().getTranscriptText().trim());
    }

    @Test
    public void csi2JStillClearsOnlyTheScreen() {
        TerminalEmulator emulator = emulator();
        feed(emulator, "l1\r\nl2\r\nl3\r\nl4\r\nl5\r\nl6");

        feed(emulator, "\033[2J");

        assertEquals(2, emulator.getScreen().getActiveTranscriptRows());
    }

    @Test
    public void latchedCtrlIsDroppedByResetTransientState() {
        TermKeyListener keys = emulator().getKeyListener();
        latch(keys);
        assertTrue(keys.isCtrlActive());

        keys.resetTransientState();

        assertFalse(keys.isCtrlActive());
        assertEquals(0, keys.getCursorMode());
    }

    @Test
    public void latchedFnAndCtrlAreDroppedByRestartTerminal() {
        TerminalEmulator emulator = emulator();
        TermKeyListener keys = emulator.getKeyListener();
        latch(keys);
        keys.handleFnKey(true);
        keys.handleFnKey(false);
        keys.handleFnKey(true);
        assertTrue(keys.getCursorMode() != 0);

        emulator.reset();

        assertFalse(keys.isCtrlActive());
        assertEquals(0, keys.getCursorMode());
    }

    @Test
    public void pauseAndResumeDropALatchedModifier() {
        TermKeyListener keys = emulator().getKeyListener();
        latch(keys);
        keys.onPause();
        assertFalse(keys.isCtrlActive());

        latch(keys);
        keys.onResume();
        assertFalse(keys.isCtrlActive());
    }

    @Test
    public void modifierStillLatchesAfterAReset() {
        TermKeyListener keys = emulator().getKeyListener();
        keys.resetTransientState();

        latch(keys);

        assertTrue(keys.isCtrlActive());
    }

    /** Press, release, press: the second press locks the modifier. */
    private static void latch(TermKeyListener keys) {
        keys.handleControlKey(true);
        keys.handleControlKey(false);
        keys.handleControlKey(true);
    }

    private static TerminalEmulator emulator() {
        ColorScheme scheme = new ColorScheme(0xffffffff, 0xff000000);
        TranscriptScreen screen = new TranscriptScreen(COLUMNS, 100, ROWS, scheme);
        return new TerminalEmulator(new TermSession(), screen, COLUMNS, ROWS, scheme);
    }

    private static void feed(TerminalEmulator emulator, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        emulator.append(bytes, 0, bytes.length);
    }

    private static String visibleText(TranscriptScreen screen) {
        return screen.getSelectedText(0, 0, COLUMNS - 1, ROWS - 1).trim();
    }
}
