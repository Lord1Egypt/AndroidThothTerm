/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LAN Mode end to end on loopback: the real server, auth and terminal
 * bookkeeping, with an echoing in-memory PTY in place of PRoot.
 */
public class LanServerTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final int FIRST_PORT = 47681;

    private final AtomicLong now = new AtomicLong(5_000_000);
    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());
    private final List<FakePty> ptys = Collections.synchronizedList(new ArrayList<>());
    private final List<String> secrets = Collections.synchronizedList(new ArrayList<>());
    private LanMode mode;

    private final LanLog log = new LanLog() {
        @Override
        public void info(String message) {
            logLines.add(message);
        }

        @Override
        public void warn(String message) {
            logLines.add(message);
        }
    };

    private final LanServer.Assets assets = name -> {
        switch (name) {
            case "index.html": return "<!doctype html><title>LAN</title>".getBytes(StandardCharsets.UTF_8);
            case "xterm.js": return "var x;".getBytes(StandardCharsets.UTF_8);
            case "fonts/cascadia-mono-arabic-400-normal.woff2": return new byte[]{'w', 'O', 'F', '2'};
            default: return null;
        }
    };

    /**
     * The shell's output as the master reads it. Unlike PipedInputStream it
     * does not break when the thread that last wrote to it ends -- the
     * server's connection threads come and go.
     */
    static final class ShellOutput extends InputStream {
        private final java.util.concurrent.LinkedBlockingQueue<byte[]> chunks =
                new java.util.concurrent.LinkedBlockingQueue<>();
        private static final byte[] EOF = new byte[0];
        private byte[] current;
        private int offset;

        void write(byte[] b, int off, int len) {
            chunks.add(java.util.Arrays.copyOfRange(b, off, off + len));
        }

        /** The shell side is gone: readers see end of file. */
        void end() {
            chunks.add(EOF);
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                while (current == null || offset == current.length) {
                    current = chunks.take();
                    offset = 0;
                    if (current == EOF) {
                        chunks.add(EOF);
                        return -1;
                    }
                }
            } catch (InterruptedException e) {
                throw new java.io.InterruptedIOException();
            }
            int n = Math.min(len, current.length - offset);
            System.arraycopy(current, offset, b, off, n);
            offset += n;
            return n;
        }
    }

    /** Echoes input back as output; "exit\r" ends the shell. */
    static final class FakePty implements Pty {
        private final ShellOutput shellOut = new ShellOutput();
        private final CountDownLatch exited = new CountDownLatch(1);
        final List<int[]> resizes = Collections.synchronizedList(new ArrayList<>());
        volatile boolean hungUp;

        FakePty(int columns, int rows) {
            resizes.add(new int[]{columns, rows});
        }

        private final OutputStream keyboard = new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                shellOut.write(b, off, len);
                line.append(new String(b, off, len, StandardCharsets.UTF_8));
                if (line.toString().contains("exit\r")) exited.countDown();
                // The shell exits but a nohup'd job keeps PRoot alive: the
                // terminal's output ends while waitFor() does not return.
                if (line.toString().contains("nohup-exit\r")) shellOut.end();
            }
        };

        @Override
        public InputStream input() {
            return shellOut;
        }

        @Override
        public OutputStream output() {
            return keyboard;
        }

        @Override
        public void resize(int columns, int rows) {
            resizes.add(new int[]{columns, rows});
        }

        @Override
        public int waitFor() {
            try {
                exited.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }

        @Override
        public void hangUp() {
            hungUp = true;
            exited.countDown();
            shellOut.end();
        }

        @Override
        public int pid() {
            return 4242;
        }
    }

    @Before
    public void setUp() {
        mode = newMode();
    }

    private LanMode newMode() {
        return new LanMode((columns, rows) -> {
            FakePty pty = new FakePty(columns, rows);
            ptys.add(pty);
            return pty;
        }, assets, log, now::get, new SecureRandom());
    }

    @After
    public void tearDown() {
        mode.stop(LanMode.Failure.NONE);
        // No secret may ever reach the log: not a PIN, not a token, not terminal data.
        synchronized (logLines) {
            for (String line : logLines) {
                for (String secret : secrets) {
                    assertFalse("log line leaks a secret: " + line, line.contains(secret));
                }
                assertFalse(line, line.contains("Bearer"));
                assertFalse(line, line.contains("typed-secret"));
            }
        }
    }

    private int start() {
        assertTrue(mode.start(LOOPBACK, FIRST_PORT, LanMode.PORT_ATTEMPTS));
        return mode.status().port;
    }

    private String host(int port) {
        return LOOPBACK.getHostAddress() + ":" + port;
    }

    private String pin() {
        String pin = mode.status().pin.pin;
        secrets.add(pin);
        return pin;
    }

    // ---------------------------------------------------------------- HTTP

    static final class Response {
        int status;
        final Map<String, String> headers = new java.util.HashMap<>();
        byte[] body = new byte[0];

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private Response http(int port, String method, String path, String origin, String contentType,
                          String authorization, String body, String hostHeader) throws IOException {
        try (Socket s = new Socket(LOOPBACK, port)) {
            s.setSoTimeout(5000);
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder req = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
            if (hostHeader != null) req.append("Host: ").append(hostHeader).append("\r\n");
            if (origin != null) req.append("Origin: ").append(origin).append("\r\n");
            if (contentType != null) req.append("Content-Type: ").append(contentType).append("\r\n");
            if (authorization != null) req.append("Authorization: ").append(authorization).append("\r\n");
            if (body != null) req.append("Content-Length: ").append(payload.length).append("\r\n");
            req.append("\r\n");
            OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(payload);
            out.flush();
            return readResponse(s.getInputStream(), true);
        }
    }

    private static Response readResponse(InputStream in, boolean withBody) throws IOException {
        Response r = new Response();
        String status = line(in);
        r.status = Integer.parseInt(status.split(" ")[1]);
        String h;
        while (!(h = line(in)).isEmpty()) {
            int c = h.indexOf(':');
            r.headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
        }
        if (withBody) {
            int n = Integer.parseInt(r.headers.getOrDefault("content-length", "0"));
            r.body = new byte[n];
            int read = 0;
            while (read < n) {
                int k = in.read(r.body, read, n - read);
                if (k < 0) throw new EOFException();
                read += k;
            }
        }
        return r;
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != '\n') {
            if (c < 0) throw new EOFException();
            if (c != '\r') b.write(c);
        }
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private Response pair(int port, String pin) throws IOException {
        now.addAndGet(LanAuth.ATTEMPT_SPACING_MS);
        return http(port, "POST", "/api/pair", "http://" + host(port), "application/json", null,
                "{\"pin\":\"" + pin + "\"}", host(port));
    }

    private String pairedToken(int port) throws IOException {
        Response r = pair(port, pin());
        assertEquals(200, r.status);
        String token = (String) FlatJson.parse(r.text()).get("token");
        secrets.add(token);
        return token;
    }

    // ----------------------------------------------------------- WebSocket

    final class Ws implements AutoCloseable {
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        final Response handshake;

        Ws(int port, String origin, String protocols) throws IOException {
            socket = new Socket(LOOPBACK, port);
            socket.setSoTimeout(5000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            StringBuilder req = new StringBuilder("GET /ws HTTP/1.1\r\n")
                    .append("Host: ").append(host(port)).append("\r\n")
                    .append("Upgrade: websocket\r\nConnection: Upgrade\r\n")
                    .append("Sec-WebSocket-Version: 13\r\n")
                    .append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n");
            if (origin != null) req.append("Origin: ").append(origin).append("\r\n");
            if (protocols != null) req.append("Sec-WebSocket-Protocol: ").append(protocols).append("\r\n");
            out.write(req.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            handshake = readResponse(in, false);
        }

        void send(int opcode, byte[] payload) throws IOException {
            out.write(WebSocketCodecTest.clientFrame(true, opcode, payload, true));
            out.flush();
        }

        void sendText(String text) throws IOException {
            send(WebSocketCodec.OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
        }

        /** The next server frame: {opcode, payload}. */
        Object[] frame() throws IOException {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) throw new EOFException();
            assertEquals("server frames are unmasked", 0, b1 & 0x80);
            long n = b1 & 0x7f;
            if (n == 126) n = (in.read() << 8) | in.read();
            else if (n == 127) {
                n = 0;
                for (int i = 0; i < 8; ++i) n = (n << 8) | in.read();
            }
            byte[] p = new byte[(int) n];
            int read = 0;
            while (read < n) {
                int k = in.read(p, read, (int) n - read);
                if (k < 0) throw new EOFException();
                read += k;
            }
            return new Object[]{b0 & 0x0f, p};
        }

        Map<String, Object> ready() throws IOException {
            Object[] f = frame();
            assertEquals(WebSocketCodec.OP_TEXT, f[0]);
            Map<String, Object> m = FlatJson.parse(new String((byte[]) f[1], StandardCharsets.UTF_8));
            assertEquals("ready", m.get("type"));
            return m;
        }

        /** Read binary output until it contains {@code expected}. */
        String outputUntil(String expected) throws IOException {
            StringBuilder seen = new StringBuilder();
            while (!seen.toString().contains(expected)) {
                Object[] f = frame();
                assertEquals("expected output, got opcode " + f[0], WebSocketCodec.OP_BINARY, f[0]);
                seen.append(new String((byte[]) f[1], StandardCharsets.UTF_8));
            }
            return seen.toString();
        }

        /** Read until a close frame; returns its code. */
        int closeCode() throws IOException {
            while (true) {
                Object[] f = frame();
                if ((int) f[0] == WebSocketCodec.OP_CLOSE) {
                    byte[] p = (byte[]) f[1];
                    return ((p[0] & 0xff) << 8) | (p[1] & 0xff);
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private Ws ws(int port, String token) throws IOException {
        return new Ws(port, "http://" + host(port), LanServer.PROTOCOL + ", " + LanServer.AUTH_PREFIX + token);
    }

    private static void eventually(String what, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    private static void assertRefused(int port) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(LOOPBACK, port), 2000);
            fail("something still listens on " + port);
        } catch (ConnectException expected) {
            // Nothing listening.
        }
    }

    // --------------------------------------------------------------- tests

    @Test
    public void isOffByDefaultWithNothingListening() throws IOException {
        assertFalse(mode.isOn());
        LanMode.Status status = mode.status();
        assertFalse(status.on);
        assertEquals(-1, status.port);
        assertEquals(null, status.pin);
        assertEquals(0, mode.terminalPids().length);
        assertRefused(FIRST_PORT);
    }

    @Test
    public void onListensAndOffStopsListening() throws IOException {
        int port = start();
        assertTrue(mode.isOn());
        assertEquals("http://" + host(port) + "/", mode.status().url);
        Response page = http(port, "GET", "/", null, null, null, null, host(port));
        assertEquals(200, page.status);
        assertTrue(page.text().contains("<title>LAN</title>"));
        mode.stop(LanMode.Failure.NONE);
        assertFalse(mode.isOn());
        assertRefused(port);
    }

    @Test
    public void servesOnlyTheFixedAssetsWithStrictHeaders() throws IOException {
        int port = start();
        Response js = http(port, "GET", "/xterm.js", null, null, null, null, host(port));
        assertEquals(200, js.status);
        assertTrue(js.headers.get("content-type").startsWith("text/javascript"));
        String csp = js.headers.get("content-security-policy");
        assertTrue(csp, csp.contains("default-src 'none'") && csp.contains("script-src 'self'")
                && csp.contains("frame-ancestors 'none'"));
        assertEquals("nosniff", js.headers.get("x-content-type-options"));
        assertEquals("DENY", js.headers.get("x-frame-options"));
        assertEquals("no-store", js.headers.get("cache-control"));
        for (String path : new String[]{"/index.html/../../etc/passwd", "/../lan/index.html",
                "/app.js", "/assets/xterm.js", "/xterm.js/", "/api"}) {
            assertEquals(path, 404, http(port, "GET", path, null, null, null, null, host(port)).status);
        }
        assertEquals(405, http(port, "DELETE", "/", null, null, null, null, host(port)).status);
    }

    @Test
    public void servesOnlyWellFormedFontNames() throws IOException {
        int port = start();
        Response font = http(port, "GET", "/fonts/cascadia-mono-arabic-400-normal.woff2", null, null, null, null, host(port));
        assertEquals(200, font.status);
        assertEquals("font/woff2", font.headers.get("content-type"));
        assertTrue(font.headers.get("content-security-policy").contains("font-src 'self'"));
        for (String path : new String[]{"/fonts/missing.woff2", "/fonts/../index.html", "/fonts/..%2Findex.html",
                "/fonts/a/b.woff2", "/fonts/UPPER.woff2", "/fonts/x.ttf", "/fonts/.woff2", "/fonts/"}) {
            assertEquals(path, 404, http(port, "GET", path, null, null, null, null, host(port)).status);
        }
    }

    @Test
    public void refusesAnyOtherHostName() throws IOException {
        int port = start();
        // DNS rebinding arrives under the attacker's name, or no name at all.
        assertEquals(421, http(port, "GET", "/", null, null, null, null, "evil.example:" + port).status);
        assertEquals(421, http(port, "GET", "/", null, null, null, null, "localhost:" + port).status);
        assertEquals(421, http(port, "GET", "/", null, null, null, null, null).status);
    }

    @Test
    public void pairingRequiresTheExactOriginAndJson() throws IOException {
        int port = start();
        String pin = pin();
        String body = "{\"pin\":\"" + pin + "\"}";
        now.addAndGet(LanAuth.ATTEMPT_SPACING_MS);
        assertEquals(403, http(port, "POST", "/api/pair", "http://evil.example", "application/json",
                null, body, host(port)).status);
        assertEquals(403, http(port, "POST", "/api/pair", null, "application/json",
                null, body, host(port)).status);
        assertEquals(403, http(port, "POST", "/api/pair", "http://" + host(port), "text/plain",
                null, body, host(port)).status);
        assertEquals(405, http(port, "GET", "/api/pair", null, null, null, null, host(port)).status);
        // None of those consumed the PIN.
        assertEquals(200, pair(port, pin).status);
    }

    @Test
    public void wrongExpiredLockedAndReusedPinsFail() throws IOException {
        int port = start();
        String pin = pin();
        String wrong = pin.equals("000000") ? "111111" : "000000";

        Response r = pair(port, wrong);
        assertEquals(401, r.status);
        assertEquals(4L, FlatJson.parse(r.text()).get("attemptsLeft"));
        assertFalse(r.text().contains("token"));

        // Too fast: no clock advance before the next attempt.
        assertEquals(429, http(port, "POST", "/api/pair", "http://" + host(port), "application/json",
                null, "{\"pin\":\"" + pin + "\"}", host(port)).status);

        assertEquals(200, pair(port, pin).status);
        assertEquals(409, pair(port, pin).status);   // one-time

        mode.newPin();
        String second = pin();
        now.addAndGet(LanAuth.PIN_TTL_MS);
        assertEquals(410, pair(port, second).status);  // expired

        mode.newPin();
        String third = pin();
        String wrong3 = third.equals("000000") ? "111111" : "000000";
        for (int i = 1; i < LanAuth.MAX_PIN_ATTEMPTS; ++i) assertEquals(401, pair(port, wrong3).status);
        assertEquals(423, pair(port, wrong3).status);
        assertEquals(423, pair(port, third).status);   // locked, even with the right PIN
        assertEquals(400, pair(port, "\"}").status);
    }

    @Test
    public void unauthenticatedWebSocketsAreRefusedBeforeUpgrade() throws IOException {
        int port = start();
        String token = pairedToken(port);
        String origin = "http://" + host(port);
        try (Ws none = new Ws(port, origin, null)) {
            assertEquals(401, none.handshake.status);
        }
        try (Ws protoOnly = new Ws(port, origin, LanServer.PROTOCOL)) {
            assertEquals(401, protoOnly.handshake.status);
        }
        try (Ws forged = new Ws(port, origin, LanServer.PROTOCOL + ", auth.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
            assertEquals(401, forged.handshake.status);
        }
        try (Ws noProtocol = new Ws(port, origin, LanServer.AUTH_PREFIX + token)) {
            assertEquals(401, noProtocol.handshake.status);
        }
        // A valid token from a foreign page is still refused (cross-site WebSocket hijacking).
        try (Ws foreign = new Ws(port, "http://evil.example", LanServer.PROTOCOL + ", auth." + token)) {
            assertEquals(403, foreign.handshake.status);
        }
        try (Ws noOrigin = new Ws(port, null, LanServer.PROTOCOL + ", auth." + token)) {
            assertEquals(403, noOrigin.handshake.status);
        }
        assertTrue("no terminal was created", ptys.isEmpty());
        assertEquals(204, http(port, "GET", "/api/session", null, null, "Bearer " + token, null, host(port)).status);
        assertEquals(401, http(port, "GET", "/api/session", null, null, "Bearer nope", null, host(port)).status);
        assertEquals(401, http(port, "GET", "/api/session", null, null, null, null, host(port)).status);
    }

    @Test
    public void pairedBrowserGetsADedicatedRealTerminal() throws Exception {
        int port = start();
        String token = pairedToken(port);
        try (Ws ws = ws(port, token)) {
            assertEquals(101, ws.handshake.status);
            assertEquals(LanServer.PROTOCOL, ws.handshake.headers.get("sec-websocket-protocol"));
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":132,\"rows\":40}");
            Map<String, Object> ready = ws.ready();
            assertEquals(Boolean.TRUE, ready.get("created"));
            assertEquals(1, ptys.size());
            assertArrayEquals(new int[]{132, 40}, ptys.get(0).resizes.get(0));

            ws.send(WebSocketCodec.OP_BINARY, "typed-secret مرحبا\u0003".getBytes(StandardCharsets.UTF_8));
            assertTrue(ws.outputUntil("\u0003").contains("typed-secret مرحبا"));

            ws.sendText("{\"type\":\"resize\",\"cols\":100,\"rows\":30}");
            ws.sendText("{\"type\":\"ping\"}");
            eventually("resize", () -> ptys.get(0).resizes.size() == 2);
            assertArrayEquals(new int[]{100, 30}, ptys.get(0).resizes.get(1));
            assertEquals(1, mode.status().terminals);
            assertEquals(1, mode.status().connectedTerminals);

            // A second tab gets a terminal of its own.
            try (Ws second = ws(port, token)) {
                second.sendText("{\"type\":\"open\",\"term\":" + FlatJson.quote((String) ready.get("term"))
                        + ",\"cols\":80,\"rows\":24}");
                assertEquals(Boolean.TRUE, second.ready().get("created"));
                assertEquals(2, ptys.size());
            }
        }
    }

    @Test
    public void reattachesWithinGraceThenHangsUp() throws Exception {
        int port = start();
        String token = pairedToken(port);
        String term;
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            term = (String) ws.ready().get("term");
            ws.send(WebSocketCodec.OP_BINARY, "before\n".getBytes(StandardCharsets.UTF_8));
            ws.outputUntil("before");
        }
        eventually("detach", () -> mode.status().connectedTerminals == 0);
        assertEquals(1, mode.status().terminals);

        try (Ws again = ws(port, token)) {
            again.sendText("{\"type\":\"open\",\"term\":" + FlatJson.quote(term) + ",\"cols\":90,\"rows\":20}");
            Map<String, Object> ready = again.ready();
            assertEquals(Boolean.FALSE, ready.get("created"));
            assertEquals(term, ready.get("term"));
            assertEquals(1, ptys.size());
            again.send(WebSocketCodec.OP_BINARY, "after\n".getBytes(StandardCharsets.UTF_8));
            again.outputUntil("after");
        }
        eventually("detach", () -> mode.status().connectedTerminals == 0);
        assertFalse(ptys.get(0).hungUp);
        now.addAndGet(RemoteTerminals.GRACE_MS + 1);
        eventually("grace expiry", () -> ptys.get(0).hungUp);
        assertEquals(0, mode.status().terminals);

        try (Ws late = ws(port, token)) {
            late.sendText("{\"type\":\"open\",\"term\":" + FlatJson.quote(term) + ",\"cols\":80,\"rows\":24}");
            assertEquals(Boolean.TRUE, late.ready().get("created"));
        }
    }

    @Test
    public void anotherBrowserCannotTakeOverATerminal() throws Exception {
        int port = start();
        String first = pairedToken(port);
        mode.newPin();
        String second = pairedToken(port);
        String term;
        try (Ws ws = ws(port, first)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            term = (String) ws.ready().get("term");
        }
        eventually("detach", () -> mode.status().connectedTerminals == 0);
        try (Ws other = ws(port, second)) {
            other.sendText("{\"type\":\"open\",\"term\":" + FlatJson.quote(term) + ",\"cols\":80,\"rows\":24}");
            Map<String, Object> ready = other.ready();
            assertEquals(Boolean.TRUE, ready.get("created"));
            assertFalse(term.equals(ready.get("term")));
        }
    }

    @Test
    public void shellExitClosesTheBrowserTerminal() throws Exception {
        int port = start();
        String token = pairedToken(port);
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            ws.ready();
            ws.send(WebSocketCodec.OP_BINARY, "exit\r".getBytes(StandardCharsets.UTF_8));
            assertEquals(LanServer.CLOSE_EXITED, ws.closeCode());
        }
        eventually("terminal removal", () -> mode.status().terminals == 0);
    }

    @Test
    public void terminalEndsWhenItsOutputEndsEvenIfPtyLivesOn() throws Exception {
        int port = start();
        String token = pairedToken(port);
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            ws.ready();
            ws.send(WebSocketCodec.OP_BINARY, "nohup-exit\r".getBytes(StandardCharsets.UTF_8));
            assertEquals(LanServer.CLOSE_EXITED, ws.closeCode());
        }
        eventually("terminal removal", () -> mode.status().terminals == 0);
        assertTrue("the PTY is hung up and released", ptys.get(0).hungUp);
    }

    @Test
    public void offDropsBrowsersHangsUpTerminalsAndInvalidatesCredentials() throws Exception {
        int port = start();
        String token = pairedToken(port);
        Ws ws = ws(port, token);
        ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
        ws.ready();
        assertEquals(1, mode.terminalPids().length);

        mode.stop(LanMode.Failure.NONE);
        assertEquals(LanServer.CLOSE_OFF, ws.closeCode());
        ws.close();
        assertTrue(ptys.get(0).hungUp);
        assertEquals(0, mode.terminalPids().length);
        assertRefused(port);

        // ON again: a new generation. The old token is worthless and a new PIN is needed.
        int again = start();
        try (Ws old = ws(again, token)) {
            assertEquals(401, old.handshake.status);
        }
        assertEquals(401, http(again, "GET", "/api/session", null, null, "Bearer " + token, null, host(again)).status);
        String fresh = pairedToken(again);
        assertFalse(fresh.equals(token));
    }

    @Test
    public void signOutRevokesTheTokenAndDropsItsSockets() throws Exception {
        int port = start();
        String token = pairedToken(port);
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            ws.ready();
            assertEquals(403, http(port, "POST", "/api/logout", "http://evil.example", null,
                    "Bearer " + token, null, host(port)).status);
            assertEquals(204, http(port, "POST", "/api/logout", "http://" + host(port), null,
                    "Bearer " + token, null, host(port)).status);
            assertEquals(LanServer.CLOSE_SIGNED_OUT, ws.closeCode());
        }
        try (Ws after = ws(port, token)) {
            assertEquals(401, after.handshake.status);
        }
    }

    @Test
    public void limitsTerminalsPerBrowser() throws Exception {
        int port = start();
        String token = pairedToken(port);
        List<Ws> open = new ArrayList<>();
        try {
            for (int i = 0; i < RemoteTerminals.MAX_PER_BROWSER; ++i) {
                Ws ws = ws(port, token);
                open.add(ws);
                ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
                ws.ready();
            }
            try (Ws extra = ws(port, token)) {
                extra.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
                assertEquals(LanServer.CLOSE_LIMIT, extra.closeCode());
            }
        } finally {
            for (Ws ws : open) ws.close();
        }
    }

    @Test
    public void protocolViolationsEndTheConnection() throws Exception {
        int port = start();
        String token = pairedToken(port);
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"nonsense\"}");
            assertEquals(LanServer.CLOSE_BAD_OPEN, ws.closeCode());
        }
        try (Ws ws = ws(port, token)) {
            ws.sendText("{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}");
            ws.ready();
            ws.out.write(WebSocketCodecTest.clientFrame(true, WebSocketCodec.OP_BINARY,
                    "x".getBytes(StandardCharsets.UTF_8), false));
            ws.out.flush();
            assertEquals(1002, ws.closeCode());
        }
    }

    @Test
    public void takesTheNextPortWhenTheFirstIsBusyAndSaysSoWhenAllAre() throws IOException {
        try (ServerSocket busy = new ServerSocket()) {
            busy.bind(new InetSocketAddress(LOOPBACK, FIRST_PORT));
            assertFalse(mode.start(LOOPBACK, FIRST_PORT, 1));
            assertEquals(LanMode.Failure.PORT_IN_USE, mode.status().failure);
            assertFalse(mode.isOn());

            assertTrue(mode.start(LOOPBACK, FIRST_PORT, 3));
            assertEquals(FIRST_PORT + 1, mode.status().port);
            assertTrue(mode.status().url.endsWith(":" + (FIRST_PORT + 1) + "/"));
        }
    }

    @Test
    public void refusesAnAddressThatIsNotLocal() throws Exception {
        assertFalse(mode.start(InetAddress.getByName("192.0.2.1"), FIRST_PORT, 1));
        assertEquals(LanMode.Failure.START_FAILED, mode.status().failure);
    }

    @Test
    public void pinsAreVisibleOnlyWhileUsable() throws IOException {
        int port = start();
        assertNotNull(mode.status().pin.pin);
        pairedToken(port);
        assertEquals(LanAuth.PinState.USED, mode.status().pin.state);
        assertEquals(null, mode.status().pin.pin);
        assertEquals(1, mode.status().pairedBrowsers);
    }
}
