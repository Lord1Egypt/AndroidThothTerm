/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpRequestTest {
    private static HttpRequest parse(String text) throws IOException {
        return HttpRequest.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void parsesARequest() throws IOException {
        HttpRequest r = parse("POST /api/pair?x=1 HTTP/1.1\r\nHost: 192.168.1.5:7681\r\n"
                + "Content-Type: application/json\r\nContent-Length: 4\r\n\r\nabcd");
        assertEquals("POST", r.method);
        assertEquals("/api/pair", r.path);
        assertEquals("192.168.1.5:7681", r.header("host"));
        assertEquals("abcd", new String(r.body, StandardCharsets.UTF_8));
    }

    @Test
    public void readsCommaSeparatedTokens() throws IOException {
        HttpRequest r = parse("GET /ws HTTP/1.1\r\nConnection: keep-alive, Upgrade\r\n\r\n");
        assertTrue(r.headerHasToken("Connection", "upgrade"));
    }

    @Test
    public void returnsNullOnACleanClose() throws IOException {
        assertNull(parse(""));
    }

    @Test
    public void refusesWhatItDoesNotUnderstand() throws IOException {
        expect("GARBAGE\r\n\r\n", 400);
        expect("GET http://evil/ HTTP/1.1\r\n\r\n", 400);
        expect("GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n", 400);
        expect("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", 501);
        expect("POST / HTTP/1.1\r\nContent-Length: 99999\r\n\r\n", 413);
        expect("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n", 400);
        expect("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc", 400);
        expect("GET /" + repeat("a", HttpRequest.MAX_LINE) + " HTTP/1.1\r\n\r\n", 431);
        StringBuilder many = new StringBuilder("GET / HTTP/1.1\r\n");
        for (int i = 0; i <= HttpRequest.MAX_HEADERS; ++i) many.append("X-").append(i).append(": v\r\n");
        expect(many.append("\r\n").toString(), 431);
        expect("GET / HTTP/1.1\r\nHost: a\r\n", 400);
    }

    private static void expect(String text, int status) throws IOException {
        try {
            parse(text);
            fail("accepted " + text.substring(0, Math.min(40, text.length())));
        } catch (HttpRequest.BadRequest e) {
            assertEquals(status, e.status);
        }
    }

    private static String repeat(String s, int n) {
        StringBuilder out = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; ++i) out.append(s);
        return out.toString();
    }
}
