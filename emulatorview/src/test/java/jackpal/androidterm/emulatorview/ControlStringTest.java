/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package jackpal.androidterm.emulatorview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * Control strings (OSC, DCS, SOS, PM, APC) are consumed up to their terminator
 * and never drawn. Arch Linux's /etc/bash.bashrc sets the window title with
 * APC ("ESC _ user@host:dir ESC \") when TERM is screen*, ThothTerm's default;
 * the engine used to draw the payload in front of every prompt.
 */
public class ControlStringTest {
    private static String render(String input) {
        TermSession session = new TermSession();
        session.setDefaultUTF8Mode(true);
        TranscriptScreen screen = new TranscriptScreen(80, 100, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        TerminalEmulator emulator = new TerminalEmulator(session, screen, 80, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, 0, bytes.length);
        return screen.getTranscriptText().trim();
    }

    @Test
    public void screensTitleStringIsNotDrawn() {
        assertEquals("$ ls", render("\u001b_thoth@localhost:~\u001b\\$ ls"));
    }

    @Test
    public void everyStringTypeIsConsumed() {
        for (String introducer : new String[]{"\u001bP", "\u001bX", "\u001b^", "\u001b_"}) {
            assertEquals(introducer, "AB", render("A" + introducer + "1;2|payload\u001b\\B"));
        }
    }

    @Test
    public void unicodeInsideAStringIsNotDrawn() {
        assertEquals("AB", render("A\u001b_thoth@thothterm:~/مرحبا\u001b\\B"));
        // Also in OSC, whose payload becomes the title instead.
        assertEquals("CD", render("C\u001b]0;~/مرحبا/日本\u0007D"));
    }

    @Test
    public void anEscapeThatIsNotAStringTerminatorEndsTheStringAndIsInterpreted() {
        // ESC [ 1 m inside an unterminated APC: the string ends, SGR applies.
        assertEquals("AB", render("A\u001b_junk\u001b[1mB"));
    }

    @Test
    public void oscStillSetsTheTitleIncludingUnicode() {
        TermSession session = new TermSession();
        session.setDefaultUTF8Mode(true);
        TranscriptScreen screen = new TranscriptScreen(80, 100, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        TerminalEmulator emulator = new TerminalEmulator(session, screen, 80, 24,
                new ColorScheme(0xffffffff, 0xff000000));
        byte[] bytes = "\u001b]0;thoth@thothterm:~/مرحبا\u0007".getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, 0, bytes.length);
        assertEquals("thoth@thothterm:~/مرحبا", session.getTitle());
        assertEquals("", screen.getTranscriptText().trim());
    }

    @Test
    public void plainTextAndC1FreeUnicodeAreUnchanged() {
        assertEquals("مرحبا ThothTerm é", render("مرحبا ThothTerm é"));
    }
}
