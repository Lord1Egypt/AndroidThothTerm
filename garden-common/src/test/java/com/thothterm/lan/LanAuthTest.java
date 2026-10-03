/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class LanAuthTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LanAuth auth = new LanAuth(new SecureRandom(), now::get);

    /** Pairing attempts are spaced; step past the spacing before each one. */
    private LanAuth.PairResult attempt(String pin) {
        now.addAndGet(LanAuth.ATTEMPT_SPACING_MS);
        return auth.pair(pin);
    }

    private static String wrong(String pin) {
        return pin.equals("000000") ? "111111" : "000000";
    }

    @Test
    public void noPinBeforeOneIsIssued() {
        assertEquals(LanAuth.PinState.NONE, auth.pinView().state);
        assertEquals(LanAuth.PairOutcome.NO_PIN, attempt("123456").outcome);
    }

    @Test
    public void pinIsSixDigits() {
        for (int i = 0; i < 200; ++i) {
            String pin = auth.newPin().pin;
            assertTrue(pin, pin.matches("[0-9]{6}"));
        }
    }

    @Test
    public void correctPinPairsOnceAndIssuesAStrongToken() {
        String pin = auth.newPin().pin;
        LanAuth.PairResult result = attempt(pin);
        assertEquals(LanAuth.PairOutcome.PAIRED, result.outcome);
        // 32 random bytes, unpadded base64url.
        assertTrue(result.token, result.token.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(pin, result.token);
        assertTrue(auth.browserFor(result.token) > 0);
        // One-time: the same PIN cannot pair a second browser.
        assertEquals(LanAuth.PairOutcome.NO_PIN, attempt(pin).outcome);
        assertEquals(LanAuth.PinState.USED, auth.pinView().state);
        assertNull(auth.pinView().pin);
    }

    @Test
    public void wrongPinIsRejectedAndCounted() {
        String pin = auth.newPin().pin;
        LanAuth.PairResult result = attempt(wrong(pin));
        assertEquals(LanAuth.PairOutcome.WRONG, result.outcome);
        assertEquals(LanAuth.MAX_PIN_ATTEMPTS - 1, result.attemptsLeft);
        assertNull(result.token);
        assertEquals(LanAuth.PairOutcome.PAIRED, attempt(pin).outcome);
    }

    @Test
    public void tooManyWrongAttemptsLockThePin() {
        String pin = auth.newPin().pin;
        for (int i = 1; i < LanAuth.MAX_PIN_ATTEMPTS; ++i) {
            assertEquals(LanAuth.PairOutcome.WRONG, attempt(wrong(pin)).outcome);
        }
        assertEquals(LanAuth.PairOutcome.LOCKED, attempt(wrong(pin)).outcome);
        // Even the right PIN no longer works; only the phone can issue a new one.
        assertEquals(LanAuth.PairOutcome.LOCKED, attempt(pin).outcome);
        assertEquals(LanAuth.PinState.LOCKED, auth.pinView().state);
        String fresh = auth.newPin().pin;
        assertEquals(LanAuth.PairOutcome.PAIRED, attempt(fresh).outcome);
    }

    @Test
    public void attemptsAreRateLimited() {
        String pin = auth.newPin().pin;
        now.addAndGet(LanAuth.ATTEMPT_SPACING_MS);
        assertEquals(LanAuth.PairOutcome.WRONG, auth.pair(wrong(pin)).outcome);
        // Immediately again, even with the right PIN: refused, and not counted.
        LanAuth.PairResult fast = auth.pair(pin);
        assertEquals(LanAuth.PairOutcome.TOO_FAST, fast.outcome);
        assertNull(fast.token);
        assertEquals(LanAuth.MAX_PIN_ATTEMPTS - 1, fast.attemptsLeft);
        assertEquals(LanAuth.PairOutcome.PAIRED, attempt(pin).outcome);
    }

    @Test
    public void pinExpires() {
        String pin = auth.newPin().pin;
        now.addAndGet(LanAuth.PIN_TTL_MS);
        assertEquals(LanAuth.PinState.EXPIRED, auth.pinView().state);
        assertEquals(LanAuth.PairOutcome.EXPIRED, attempt(pin).outcome);
    }

    @Test
    public void malformedGuessesAreWrong() {
        auth.newPin();
        assertEquals(LanAuth.PairOutcome.WRONG, attempt("").outcome);
        assertEquals(LanAuth.PairOutcome.WRONG, attempt(null).outcome);
        assertEquals(LanAuth.PairOutcome.WRONG, attempt("12345678901234567890").outcome);
    }

    @Test
    public void tokensAreUniqueAndRevocable() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < LanAuth.MAX_BROWSERS; ++i) {
            String token = attempt(auth.newPin().pin).token;
            assertTrue(tokens.add(token));
        }
        assertEquals(LanAuth.PairOutcome.FULL, attempt(auth.newPin().pin).outcome);
        String first = tokens.iterator().next();
        auth.revoke(first);
        assertEquals(-1, auth.browserFor(first));
        auth.revokeAll();
        for (String token : tokens) assertEquals(-1, auth.browserFor(token));
        assertEquals(LanAuth.PinState.NONE, auth.pinView().state);
    }

    @Test
    public void unknownTokensAreInvalid() {
        assertEquals(-1, auth.browserFor(null));
        assertEquals(-1, auth.browserFor(""));
        assertEquals(-1, auth.browserFor(repeat("A", 43)));
        assertEquals(-1, auth.browserFor(repeat("A", 500)));
    }

    private static String repeat(String s, int n) {
        StringBuilder out = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; ++i) out.append(s);
        return out.toString();
    }
}
