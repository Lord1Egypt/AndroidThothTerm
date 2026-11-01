/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * SGR colours. TUIs such as OpenCode send 24-bit colour (38;2;r;g;b), which the
 * engine used to read as five separate SGR codes: a 0 reset the style, 30-47
 * picked palette colours and 7 turned on inverse, so text could end up drawn in
 * its own background colour. 24-bit colours are now mapped to the nearest
 * xterm-256 colour, the same approximation tmux uses on 256-colour terminals.
 */
public class SgrColorTest {
    /** The style of the first character drawn by {@code input}. */
    private static int styleOf(String input) {
        TermSession session = new TermSession();
        session.setDefaultUTF8Mode(true);
        TranscriptScreen screen = new TranscriptScreen(80, 100, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        TerminalEmulator emulator = new TerminalEmulator(session, screen, 80, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, 0, bytes.length);
        GrowableIntArray colors = new GrowableIntArray(16);
        String text = screen.getTranscriptText(colors);
        assertEquals("X", text.substring(0, 1));
        return colors.at(0);
    }

    private static final String ESC = "\u001b";

    @Test
    public void paletteColoursStillWork() {
        int s = styleOf(ESC + "[31;42mX");
        assertEquals(1, TextStyle.decodeForeColor(s));
        assertEquals(2, TextStyle.decodeBackColor(s));
        s = styleOf(ESC + "[38;5;208;48;5;17mX");
        assertEquals(208, TextStyle.decodeForeColor(s));
        assertEquals(17, TextStyle.decodeBackColor(s));
    }

    @Test
    public void trueColourMapsToTheNearestXterm256Colour() {
        // Pure cube corners and a grey are exact; an in-between value takes the nearest.
        int s = styleOf(ESC + "[38;2;255;0;0;48;2;0;0;255mX");
        assertEquals(196, TextStyle.decodeForeColor(s));
        assertEquals(21, TextStyle.decodeBackColor(s));
        assertEquals(244, TextStyle.decodeForeColor(styleOf(ESC + "[38;2;128;128;128mX")));
        assertEquals(44, TextStyle.decodeForeColor(styleOf(ESC + "[38;2;0;200;220mX")));
    }

    @Test
    public void trueColourComponentsAreNotReadAsSgrCodes() {
        // 0 would reset, 7 would set inverse, 40-47 would set a background colour.
        int s = styleOf(ESC + "[1;48;2;40;7;0mX");
        assertEquals(TextStyle.fxBold, TextStyle.decodeEffect(s));
        assertEquals(TextStyle.ciForeground, TextStyle.decodeForeColor(s));
        assertEquals(233, TextStyle.decodeBackColor(s)); // near-black: grey 18 beats cube 0
    }

    @Test
    public void colonSeparatedColoursWork() {
        assertEquals(196, TextStyle.decodeForeColor(styleOf(ESC + "[38:2::255:0:0mX")));
        assertEquals(196, TextStyle.decodeForeColor(styleOf(ESC + "[38:2:255:0:0mX")));
        assertEquals(208, TextStyle.decodeForeColor(styleOf(ESC + "[38:5:208mX")));
    }

    @Test
    public void aTruncatedColourChangesNothingAndKeepsLaterCodes() {
        int s = styleOf(ESC + "[38;2;255mX");
        assertEquals(TextStyle.ciForeground, TextStyle.decodeForeColor(s));
        assertEquals(TextStyle.fxNormal, TextStyle.decodeEffect(s));
    }

    @Test
    public void manyParametersDoNotCrash() {
        StringBuilder seq = new StringBuilder(ESC + "[1");
        for (int i = 0; i < 40; i++) seq.append(";4");
        styleOf(seq.append("mX").toString());
    }
}
