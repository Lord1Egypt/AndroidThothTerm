/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TranscriptSelectionTest {
    private static final int COLUMNS = 40;

    @Test
    public void selectedArabicAndEnglishRemainInLogicalOrder() {
        TranscriptScreen screen = screen();
        String text = "مرحبا ThothTerm 123";
        int end = write(screen, 0, text);

        assertEquals(text, screen.getSelectedText(0, 0, end - 1, 0));
    }

    @Test
    public void wrappedSelectionDoesNotInsertANewline() {
        TranscriptScreen screen = new TranscriptScreen(8, 100, 4,
                new ColorScheme(0xffffffff, 0xff000000));
        int firstEnd = write(screen, 0, "wrapped-");
        int secondEnd = write(screen, 1, "line");
        screen.setLineWrap(0);

        assertEquals("wrapped-line",
                screen.getSelectedText(0, 0, secondEnd - 1, 1));
        assertEquals(8, firstEnd);
    }

    @Test
    public void wideCharactersAreCopiedOnce() {
        TranscriptScreen screen = screen();
        String text = "A😀B";
        int end = write(screen, 0, text);

        assertEquals(text, screen.getSelectedText(0, 0, end - 1, 0));
    }

    @Test
    public void combiningCharactersStayAttachedInClipboardText() {
        TranscriptScreen screen = screen();
        String text = "e\u0301";
        int end = write(screen, 0, text);

        assertEquals(text, screen.getSelectedText(0, 0, end - 1, 0));
    }

    @Test
    public void multilineSelectionPreservesLogicalLineBreak() {
        TranscriptScreen screen = screen();
        write(screen, 0, "English");
        int end = write(screen, 1, "العربية 123");

        assertEquals("English\nالعربية 123",
                screen.getSelectedText(0, 0, end - 1, 1));
    }

    @Test
    public void wordBoundsRecognizeArabicLetters() {
        TranscriptScreen screen = screen();
        write(screen, 0, "abc مرحبا 123");

        assertArrayEquals(new int[] {4, 8}, screen.getWordBounds(0, 6));
    }

    @Test
    public void bidiVisualAndLogicalCellsRoundTrip() {
        TranscriptScreen screen = screen();
        write(screen, 0, "abc مرحبا 123");

        for (int logical = 0; logical < 13; logical++) {
            int visual = screen.visualColumnForLogical(0, logical);
            assertEquals(logical, screen.logicalColumnForVisual(0, visual));
        }
    }

    @Test
    public void selectionBoundariesKeepSingleCellHandlesApart() {
        char[] line = "x".toCharArray();

        assertEquals(0, PaintRenderer.visualSelectionBoundary(line, 0, true));
        assertEquals(1, PaintRenderer.visualSelectionBoundary(line, 0, false));
    }

    private static TranscriptScreen screen() {
        return new TranscriptScreen(COLUMNS, 100, 4,
                new ColorScheme(0xffffffff, 0xff000000));
    }

    private static int write(TranscriptScreen screen, int row, String text) {
        int column = 0;
        int lastWidth = 1;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int width = Math.max(0, UnicodeTranscript.charWidth(codePoint));
            int targetColumn = width == 0 ? column - lastWidth : column;
            screen.set(targetColumn, row, codePoint, TextStyle.kNormalTextStyle);
            column += width;
            if (width > 0) lastWidth = width;
            offset += Character.charCount(codePoint);
        }
        return column;
    }
}
