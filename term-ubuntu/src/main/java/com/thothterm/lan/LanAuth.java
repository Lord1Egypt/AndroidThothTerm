/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm.lan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Pairing and browser credentials for one LAN Mode run.
 * <p>
 * The phone shows a short one-time PIN. A browser that submits it gets a
 * long random token and never needs the PIN again; the PIN is consumed by the
 * first success, dies after {@link #PIN_TTL_MS}, and is locked after
 * {@link #MAX_PIN_ATTEMPTS} wrong guesses, after which only the phone can
 * issue a new one. A new instance is created every time LAN Mode is turned
 * on, so turning it off (or Exit, or process death) invalidates everything.
 * <p>
 * Only SHA-256 digests of tokens are kept, and nothing here is persisted or
 * logged.
 */
final class LanAuth {
    static final long PIN_TTL_MS = 120_000;
    static final int MAX_PIN_ATTEMPTS = 5;
    /** Minimum spacing between pairing attempts, whoever makes them. */
    static final long ATTEMPT_SPACING_MS = 1_000;
    static final int MAX_BROWSERS = 4;
    static final int PIN_DIGITS = 6;
    private static final int TOKEN_BYTES = 32;

    interface Clock {
        long nowMs();
    }

    enum PairOutcome { PAIRED, WRONG, EXPIRED, LOCKED, NO_PIN, TOO_FAST, FULL }

    static final class PairResult {
        final PairOutcome outcome;
        /** The new browser token; only for {@link PairOutcome#PAIRED}. */
        final String token;
        final int attemptsLeft;

        private PairResult(PairOutcome outcome, String token, int attemptsLeft) {
            this.outcome = outcome;
            this.token = token;
            this.attemptsLeft = attemptsLeft;
        }
    }

    enum PinState { NONE, ACTIVE, USED, EXPIRED, LOCKED }

    /** What the phone screen shows about pairing. */
    static final class PinView {
        final PinState state;
        /** The PIN itself, only while {@link PinState#ACTIVE}. */
        final String pin;
        final long expiresAtMs;

        private PinView(PinState state, String pin, long expiresAtMs) {
            this.state = state;
            this.pin = pin;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final SecureRandom random;
    private final Clock clock;

    private String pin;
    private long pinExpiresAtMs;
    private int attemptsLeft;
    private PinState pinState = PinState.NONE;
    private long lastAttemptMs = Long.MIN_VALUE / 2;
    private int nextBrowser = 1;
    /** SHA-256(token) as hex -> browser id. */
    private final Map<String, Integer> browsers = new HashMap<>();

    LanAuth(SecureRandom random, Clock clock) {
        this.random = random;
        this.clock = clock;
    }

    /** Issue a fresh PIN, replacing any earlier one. */
    synchronized PinView newPin() {
        StringBuilder digits = new StringBuilder(PIN_DIGITS);
        for (int i = 0; i < PIN_DIGITS; ++i) digits.append((char) ('0' + random.nextInt(10)));
        pin = digits.toString();
        pinExpiresAtMs = clock.nowMs() + PIN_TTL_MS;
        attemptsLeft = MAX_PIN_ATTEMPTS;
        pinState = PinState.ACTIVE;
        return pinView();
    }

    synchronized PinView pinView() {
        expireIfDue();
        return new PinView(pinState, pinState == PinState.ACTIVE ? pin : null, pinExpiresAtMs);
    }

    synchronized PairResult pair(String submitted) {
        long now = clock.nowMs();
        if (now - lastAttemptMs < ATTEMPT_SPACING_MS) {
            return new PairResult(PairOutcome.TOO_FAST, null, attemptsLeft);
        }
        lastAttemptMs = now;
        expireIfDue();
        switch (pinState) {
            case NONE:
            case USED:
                return new PairResult(PairOutcome.NO_PIN, null, 0);
            case EXPIRED:
                return new PairResult(PairOutcome.EXPIRED, null, 0);
            case LOCKED:
                return new PairResult(PairOutcome.LOCKED, null, 0);
            default:
                break;
        }
        if (!matchesPin(submitted)) {
            if (--attemptsLeft <= 0) {
                pinState = PinState.LOCKED;
                pin = null;
                return new PairResult(PairOutcome.LOCKED, null, 0);
            }
            return new PairResult(PairOutcome.WRONG, null, attemptsLeft);
        }
        if (browsers.size() >= MAX_BROWSERS) {
            return new PairResult(PairOutcome.FULL, null, attemptsLeft);
        }
        pinState = PinState.USED;
        pin = null;
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        // Unpadded base64url: a valid HTTP token, as the WebSocket handshake needs.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        browsers.put(digest(token), nextBrowser++);
        return new PairResult(PairOutcome.PAIRED, token, 0);
    }

    /** The browser id a token was issued to, or -1 when it is not valid now. */
    synchronized int browserFor(String token) {
        if (token == null || token.isEmpty() || token.length() > 128) return -1;
        Integer id = browsers.get(digest(token));
        return id == null ? -1 : id;
    }

    synchronized void revoke(String token) {
        if (token != null) browsers.remove(digest(token));
    }

    synchronized int pairedBrowsers() {
        return browsers.size();
    }

    /** Forget every credential and the PIN; the object is useless afterwards. */
    synchronized void revokeAll() {
        browsers.clear();
        pin = null;
        pinState = PinState.NONE;
    }

    private void expireIfDue() {
        if (pinState == PinState.ACTIVE && clock.nowMs() >= pinExpiresAtMs) {
            pinState = PinState.EXPIRED;
            pin = null;
        }
    }

    private boolean matchesPin(String submitted) {
        if (submitted == null || pin == null) return false;
        byte[] expected = pin.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = submitted.getBytes(StandardCharsets.US_ASCII);
        // Constant time for equal lengths; a length mismatch reveals only
        // that the guess was not six characters.
        return MessageDigest.isEqual(expected, actual);
    }

    static String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
