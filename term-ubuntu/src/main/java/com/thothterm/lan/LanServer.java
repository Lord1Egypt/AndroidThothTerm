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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The LAN Mode HTTP and WebSocket endpoint.
 * <p>
 * Plain HTTP on one private LAN address. Access control, in order:
 * <ul>
 * <li>{@code Host} must be exactly the bound {@code address:port}, so a
 * DNS-rebinding page cannot reach it under another name.</li>
 * <li>State-changing requests and the WebSocket upgrade must carry exactly
 * this server's {@code Origin}, so no other site can drive them.</li>
 * <li>The terminal WebSocket requires a paired browser's token, sent in
 * {@code Sec-WebSocket-Protocol} -- never in a URL or a cookie -- and an
 * upgrade without a valid one is refused before any 101 response.</li>
 * </ul>
 * Browser credentials are bearer tokens held by the page, so there is no
 * ambient cookie for cross-site requests to ride on.
 */
final class LanServer {
    static final String PROTOCOL = "thothterm.v1";
    static final String AUTH_PREFIX = "auth.";
    static final int MAX_CONNECTIONS = 24;
    static final int HEAD_TIMEOUT_MS = 10_000;
    /** A WebSocket that sends nothing -- the page pings every 20 s -- for this long is dead. */
    static final int SOCKET_IDLE_MS = 60_000;
    static final int OPEN_TIMEOUT_MS = 10_000;
    static final long STOP_NOTICE_MS = 500;

    /** Close codes of our own (RFC 6455 reserves 4000-4999 for applications). */
    static final int CLOSE_EXITED = 4000;
    static final int CLOSE_BAD_OPEN = 4400;
    static final int CLOSE_LIMIT = 4429;
    static final int CLOSE_OFF = 4410;
    static final int CLOSE_SIGNED_OUT = 4401;

    interface Assets {
        /** The bytes of a packaged web asset, or null. */
        byte[] read(String name) throws IOException;
    }

    /** Route -> (asset, content type). Nothing outside this table is served. */
    private static final String[][] STATIC = {
            {"/", "index.html", "text/html; charset=utf-8"},
            {"/app.js", "app.js", "text/javascript; charset=utf-8"},
            {"/app.css", "app.css", "text/css; charset=utf-8"},
            {"/xterm.js", "xterm.js", "text/javascript; charset=utf-8"},
            {"/xterm.css", "xterm.css", "text/css; charset=utf-8"},
            {"/licenses.txt", "licenses.txt", "text/plain; charset=utf-8"},
    };

    private final LanAuth auth;
    private final RemoteTerminals terminals;
    private final Assets assets;
    private final LanLog log;

    private ServerSocket server;
    private String hostHeader;
    private String origin;
    private ThreadPoolExecutor workers;
    private final Set<Socket> sockets = new HashSet<>();
    private final List<Connection> webSockets = new ArrayList<>();
    private volatile boolean stopping;

    LanServer(LanAuth auth, RemoteTerminals terminals, Assets assets, LanLog log) {
        this.auth = auth;
        this.terminals = terminals;
        this.assets = assets;
        this.log = log;
    }

    /**
     * Listen on {@code address}, trying {@code firstPort} and the following
     * {@code attempts - 1} ports. Returns the port actually bound.
     */
    synchronized int start(InetAddress address, int firstPort, int attempts) throws IOException {
        // bind() reports a foreign address as a BindException too; tell it apart
        // from a port conflict before trying ports.
        if (NetworkInterface.getByInetAddress(address) == null) {
            throw new IOException("address is not on a local interface");
        }
        BindException last = null;
        for (int i = 0; i < attempts; ++i) {
            ServerSocket socket = new ServerSocket();
            try {
                socket.bind(new InetSocketAddress(address, firstPort + i), 16);
                server = socket;
                break;
            } catch (BindException e) {
                socket.close();
                last = e;
            }
        }
        if (server == null) {
            throw last != null ? last : new BindException("no port");
        }
        int port = server.getLocalPort();
        String literal = address.getHostAddress();
        if (address instanceof Inet6Address) literal = "[" + literal + "]";
        hostHeader = literal + ":" + port;
        origin = "http://" + hostHeader;

        workers = new ThreadPoolExecutor(0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "LAN connection");
            t.setDaemon(true);
            return t;
        });
        Thread acceptor = new Thread(this::acceptLoop, "LAN listener");
        acceptor.setDaemon(true);
        acceptor.start();
        return port;
    }

    /** The URL a browser on the LAN opens. */
    synchronized String url() {
        return origin + "/";
    }

    synchronized int port() {
        return server == null ? -1 : server.getLocalPort();
    }

    /** Stop listening and drop every connection. Idempotent. */
    void stop() {
        List<Socket> open;
        List<Connection> live;
        synchronized (this) {
            stopping = true;
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignore) {
                    // Closing a listener cannot usefully fail.
                }
            }
            live = new ArrayList<>(webSockets);
            open = new ArrayList<>(sockets);
        }
        // Tell the pages why they were cut off, but never let a stalled peer
        // hold up OFF: whatever is still open afterwards is closed hard.
        Thread notifier = new Thread(() -> {
            for (Connection c : live) c.close(CLOSE_OFF, "LAN Mode is off");
        }, "LAN close notices");
        notifier.setDaemon(true);
        notifier.start();
        try {
            notifier.join(STOP_NOTICE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Socket s : open) closeQuietly(s);
        if (workers != null) workers.shutdownNow();
    }

    /** Drop the sockets of a browser that signed out. */
    void disconnectBrowser(int browser) {
        List<Connection> live;
        synchronized (this) {
            live = new ArrayList<>(webSockets);
        }
        for (Connection c : live) {
            if (c.browser == browser) c.close(CLOSE_SIGNED_OUT, "signed out");
        }
    }

    private void acceptLoop() {
        while (!stopping) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                if (!stopping) log.warn("LAN listener stopped: " + e.getClass().getSimpleName());
                return;
            }
            synchronized (this) {
                if (stopping) {
                    closeQuietly(socket);
                    return;
                }
                sockets.add(socket);
            }
            try {
                workers.execute(() -> serve(socket));
            } catch (RejectedExecutionException e) {
                log.warn("LAN connection refused: too many connections");
                forget(socket);
            }
        }
    }

    private void serve(Socket socket) {
        try {
            socket.setSoTimeout(HEAD_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            HttpRequest request;
            try {
                request = HttpRequest.read(in);
            } catch (HttpRequest.BadRequest e) {
                respond(out, e.status, "text/plain; charset=utf-8", bytes("Bad request"));
                return;
            }
            if (request == null) return;
            route(socket, in, out, request);
        } catch (SocketTimeoutException | SocketException e) {
            // Idle or reset peer.
        } catch (IOException e) {
            log.warn("LAN request failed: " + e.getClass().getSimpleName());
        } finally {
            forget(socket);
        }
    }

    private void route(Socket socket, InputStream in, OutputStream out, HttpRequest request)
            throws IOException {
        if (!hostHeader.equals(request.header("Host"))) {
            respond(out, 421, "text/plain; charset=utf-8", bytes("Misdirected request"));
            return;
        }
        String path = request.path;
        if (path.equals("/ws")) {
            if (!"GET".equals(request.method)) {
                respond(out, 405, "text/plain; charset=utf-8", bytes("Method not allowed"));
                return;
            }
            upgrade(socket, in, out, request);
            return;
        }
        if (path.equals("/api/pair")) {
            if (!"POST".equals(request.method)) {
                respond(out, 405, "text/plain; charset=utf-8", bytes("Method not allowed"));
            } else if (!sameOrigin(request) || !isJson(request)) {
                respond(out, 403, "text/plain; charset=utf-8", bytes("Forbidden"));
            } else {
                pair(out, request);
            }
            return;
        }
        if (path.equals("/api/session")) {
            int browser = auth.browserFor(bearer(request));
            respond(out, browser > 0 ? 204 : 401, null, new byte[0]);
            return;
        }
        if (path.equals("/api/logout")) {
            if (!"POST".equals(request.method)) {
                respond(out, 405, "text/plain; charset=utf-8", bytes("Method not allowed"));
                return;
            }
            if (!sameOrigin(request)) {
                respond(out, 403, "text/plain; charset=utf-8", bytes("Forbidden"));
                return;
            }
            String token = bearer(request);
            int browser = auth.browserFor(token);
            if (browser > 0) {
                auth.revoke(token);
                disconnectBrowser(browser);
                log.info("Browser signed out; paired browsers=" + auth.pairedBrowsers());
            }
            respond(out, 204, null, new byte[0]);
            return;
        }
        for (String[] entry : STATIC) {
            if (entry[0].equals(path)) {
                if (!"GET".equals(request.method) && !"HEAD".equals(request.method)) {
                    respond(out, 405, "text/plain; charset=utf-8", bytes("Method not allowed"));
                    return;
                }
                byte[] body = assets.read(entry[1]);
                if (body == null) {
                    respond(out, 404, "text/plain; charset=utf-8", bytes("Not found"));
                } else {
                    respond(out, 200, entry[2], "HEAD".equals(request.method) ? null : body,
                            body.length);
                }
                return;
            }
        }
        respond(out, 404, "text/plain; charset=utf-8", bytes("Not found"));
    }

    private void pair(OutputStream out, HttpRequest request) throws IOException {
        String pin;
        try {
            Object value = FlatJson.parse(new String(request.body, StandardCharsets.UTF_8)).get("pin");
            pin = value instanceof String ? (String) value : "";
        } catch (IllegalArgumentException e) {
            respond(out, 400, "application/json", bytes("{\"error\":\"bad_request\"}"));
            return;
        }
        LanAuth.PairResult result = auth.pair(pin);
        switch (result.outcome) {
            case PAIRED:
                log.info("Browser paired; paired browsers=" + auth.pairedBrowsers());
                respond(out, 200, "application/json",
                        bytes("{\"token\":" + FlatJson.quote(result.token) + "}"));
                return;
            case WRONG:
                log.warn("Pairing attempt rejected: wrong PIN; attempts left=" + result.attemptsLeft);
                respond(out, 401, "application/json",
                        bytes("{\"error\":\"wrong_pin\",\"attemptsLeft\":" + result.attemptsLeft + "}"));
                return;
            case LOCKED:
                log.warn("Pairing PIN locked after too many wrong attempts");
                respond(out, 423, "application/json", bytes("{\"error\":\"locked\"}"));
                return;
            case EXPIRED:
                respond(out, 410, "application/json", bytes("{\"error\":\"expired\"}"));
                return;
            case TOO_FAST:
                respond(out, 429, "application/json", bytes("{\"error\":\"too_fast\"}"));
                return;
            case FULL:
                respond(out, 409, "application/json", bytes("{\"error\":\"full\"}"));
                return;
            default:
                respond(out, 409, "application/json", bytes("{\"error\":\"no_pin\"}"));
        }
    }

    private void upgrade(Socket socket, InputStream in, OutputStream out, HttpRequest request)
            throws IOException {
        String key = request.header("Sec-WebSocket-Key");
        if (!request.headerHasToken("Upgrade", "websocket")
                || !request.headerHasToken("Connection", "Upgrade")
                || !"13".equals(request.header("Sec-WebSocket-Version"))
                || !WebSocketCodec.isValidKey(key)) {
            respond(out, 400, "text/plain; charset=utf-8", bytes("Bad WebSocket request"));
            return;
        }
        if (!sameOrigin(request)) {
            log.warn("Terminal WebSocket refused: foreign origin");
            respond(out, 403, "text/plain; charset=utf-8", bytes("Forbidden"));
            return;
        }
        boolean speaksOurs = false;
        String token = null;
        String offered = request.header("Sec-WebSocket-Protocol");
        if (offered != null) {
            for (String part : offered.split(",")) {
                String p = part.trim();
                if (p.equals(PROTOCOL)) speaksOurs = true;
                else if (p.startsWith(AUTH_PREFIX) && token == null) token = p.substring(AUTH_PREFIX.length());
            }
        }
        int browser = speaksOurs ? auth.browserFor(token) : -1;
        if (browser <= 0) {
            log.warn("Terminal WebSocket refused: not authenticated");
            respond(out, 401, "text/plain; charset=utf-8", bytes("Unauthorized"));
            return;
        }
        String head = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + WebSocketCodec.acceptKey(key) + "\r\n"
                + "Sec-WebSocket-Protocol: " + PROTOCOL + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        Connection connection = new Connection(socket, out, browser);
        synchronized (this) {
            if (stopping) {
                connection.close(CLOSE_OFF, "LAN Mode is off");
                return;
            }
            webSockets.add(connection);
        }
        try {
            connection.run(in);
        } finally {
            synchronized (this) {
                webSockets.remove(connection);
            }
        }
    }

    /** One authenticated terminal socket. */
    private final class Connection implements RemoteTerminals.Sink {
        private final Socket socket;
        private final OutputStream out;
        final int browser;
        private boolean closed;

        Connection(Socket socket, OutputStream out, int browser) {
            this.socket = socket;
            this.out = out;
            this.browser = browser;
        }

        void run(InputStream in) throws IOException {
            RemoteTerminals.Terminal terminal = null;
            WebSocketCodec.Partial partial = new WebSocketCodec.Partial();
            try {
                socket.setSoTimeout(OPEN_TIMEOUT_MS);
                terminal = open(in, partial);
                if (terminal == null) return;
                socket.setSoTimeout(SOCKET_IDLE_MS);
                while (true) {
                    WebSocketCodec.Message message = WebSocketCodec.readMessage(in, partial);
                    switch (message.opcode) {
                        case WebSocketCodec.OP_BINARY:
                            terminal.input(message.payload);
                            break;
                        case WebSocketCodec.OP_TEXT:
                            control(terminal, message.text());
                            break;
                        case WebSocketCodec.OP_PING:
                            send(WebSocketCodec.OP_PONG, message.payload);
                            break;
                        case WebSocketCodec.OP_CLOSE:
                            close(1000, "");
                            return;
                        default:
                            // Pong: nothing to do.
                            break;
                    }
                }
            } catch (WebSocketCodec.ProtocolError e) {
                close(e.closeCode, "protocol error");
            } catch (SocketTimeoutException e) {
                close(1001, "idle");
            } catch (IOException e) {
                // Peer went away.
            } finally {
                if (terminal != null) terminals.detach(terminal, this);
                close(1001, "");
            }
        }

        /** The first message chooses the terminal: {"type":"open","term":id|null,"cols":n,"rows":n}. */
        private RemoteTerminals.Terminal open(InputStream in, WebSocketCodec.Partial partial)
                throws IOException {
            WebSocketCodec.Message first = WebSocketCodec.readMessage(in, partial);
            Map<String, Object> m;
            try {
                if (first.opcode != WebSocketCodec.OP_TEXT) throw new IllegalArgumentException("not text");
                m = FlatJson.parse(first.text());
                if (!"open".equals(m.get("type"))) throw new IllegalArgumentException("not open");
            } catch (IllegalArgumentException e) {
                close(CLOSE_BAD_OPEN, "expected open");
                return null;
            }
            Object term = m.get("term");
            int columns = dimension(m.get("cols"), 80, 2, 1000);
            int rows = dimension(m.get("rows"), 24, 1, 500);
            RemoteTerminals.Attachment attachment;
            try {
                attachment = terminals.attach(browser, term instanceof String ? (String) term : null,
                        columns, rows, this);
            } catch (RemoteTerminals.LimitReached e) {
                log.warn("Browser terminal refused: " + e.getMessage());
                close(CLOSE_LIMIT, "too many terminals");
                return null;
            } catch (IOException e) {
                log.warn("Browser terminal could not start: " + e.getClass().getSimpleName());
                close(1011, "terminal could not start");
                return null;
            }
            send(WebSocketCodec.OP_TEXT, bytes("{\"type\":\"ready\",\"term\":"
                    + FlatJson.quote(attachment.terminal.id) + ",\"created\":" + attachment.created + "}"));
            return attachment.terminal;
        }

        private void control(RemoteTerminals.Terminal terminal, String text) throws IOException {
            Map<String, Object> m;
            try {
                m = FlatJson.parse(text);
            } catch (IllegalArgumentException e) {
                throw new WebSocketCodec.ProtocolError(1003, "bad control message");
            }
            // {"type":"ping"} only keeps the socket inside SOCKET_IDLE_MS.
            if ("resize".equals(m.get("type"))) {
                terminal.resize(dimension(m.get("cols"), 80, 2, 1000), dimension(m.get("rows"), 24, 1, 500));
            }
        }

        @Override
        public void output(byte[] data, int offset, int length) throws IOException {
            synchronized (out) {
                WebSocketCodec.writeFrame(out, WebSocketCodec.OP_BINARY, data, offset, length);
            }
        }

        @Override
        public void exited() {
            close(CLOSE_EXITED, "shell exited");
        }

        private void send(int opcode, byte[] payload) throws IOException {
            synchronized (out) {
                WebSocketCodec.writeFrame(out, opcode, payload, 0, payload.length);
            }
        }

        void close(int code, String reason) {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            try {
                send(WebSocketCodec.OP_CLOSE, WebSocketCodec.closePayload(code, reason));
            } catch (IOException ignore) {
                // Already gone.
            }
            closeQuietly(socket);
        }
    }

    private static int dimension(Object value, int fallback, int min, int max) {
        if (!(value instanceof Long)) return fallback;
        long n = (Long) value;
        return (int) Math.max(min, Math.min(max, n));
    }

    private boolean sameOrigin(HttpRequest request) {
        return origin.equals(request.header("Origin"));
    }

    private static boolean isJson(HttpRequest request) {
        String type = request.header("Content-Type");
        return type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json");
    }

    private static String bearer(HttpRequest request) {
        String value = request.header("Authorization");
        if (value == null || !value.startsWith("Bearer ")) return null;
        return value.substring("Bearer ".length()).trim();
    }

    private void respond(OutputStream out, int status, String type, byte[] body) throws IOException {
        respond(out, status, type, body, body.length);
    }

    /** Write a complete response; {@code body} may be null for HEAD. */
    private void respond(OutputStream out, int status, String type, byte[] body, int length)
            throws IOException {
        StringBuilder head = new StringBuilder(512)
                .append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
                .append("Content-Length: ").append(length).append("\r\n")
                .append("Connection: close\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("X-Frame-Options: DENY\r\n")
                .append("Referrer-Policy: no-referrer\r\n")
                .append("Cross-Origin-Resource-Policy: same-origin\r\n")
                .append("Content-Security-Policy: default-src 'none'; script-src 'self'; ")
                .append("style-src 'self' 'unsafe-inline'; connect-src 'self' ws://").append(hostHeader)
                .append("; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n");
        if (type != null) head.append("Content-Type: ").append(type).append("\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body != null) out.write(body, 0, length);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 410: return "Gone";
            case 413: return "Payload Too Large";
            case 421: return "Misdirected Request";
            case 423: return "Locked";
            case 429: return "Too Many Requests";
            case 431: return "Request Header Fields Too Large";
            case 501: return "Not Implemented";
            default: return "Status";
        }
    }

    private void forget(Socket socket) {
        synchronized (this) {
            sockets.remove(socket);
        }
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignore) {
            // Nothing more to release.
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
