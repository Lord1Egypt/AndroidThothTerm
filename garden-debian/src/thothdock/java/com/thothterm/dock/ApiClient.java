/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
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

package com.thothterm.dock;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A client for the ThothDock API on its Unix socket -- the same Docker Engine
 * API the Docker CLI uses. The Containers screen keeps no container state of
 * its own: everything it shows and every action it takes goes through here.
 *
 * <p>Requests are HTTP/1.0 so the server closes the connection after the
 * response and streaming bodies are delimited by EOF (no chunking to parse).</p>
 */
final class ApiClient {
    /** Responses larger than this are refused. */
    static final int MAX_BODY = 8 << 20;
    private static final int MAX_HEADER = 64 << 10;

    static final class Response {
        final int status;
        final byte[] body;

        Response(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** An error answered by the API ({"message": ...}) or a failed request. */
    static final class ApiException extends IOException {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final String socketPath;

    ApiClient(String socketPath) {
        this.socketPath = socketPath;
    }

    private LocalSocket connect(int timeoutMs) throws IOException {
        LocalSocket s = new LocalSocket();
        try {
            s.connect(new LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM));
            s.setSoTimeout(timeoutMs);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        return s;
    }

    private static void send(OutputStream out, String method, String path, String json) throws IOException {
        byte[] body = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder();
        h.append(method).append(' ').append(path).append(" HTTP/1.0\r\nHost: thothdock\r\n");
        if (json != null) h.append("Content-Type: application/json\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    Response request(String method, String path, String json, int timeoutMs) throws IOException {
        try (LocalSocket s = connect(timeoutMs)) {
            send(s.getOutputStream(), method, path, json);
            return parse(s.getInputStream());
        }
    }

    JSONObject getObject(String path) throws IOException {
        Response r = ok(request("GET", path, null, 8000));
        try {
            return new JSONObject(r.text());
        } catch (JSONException e) {
            throw new IOException("unreadable response from " + path);
        }
    }

    JSONArray getArray(String path) throws IOException {
        Response r = ok(request("GET", path, null, 8000));
        try {
            return new JSONArray(r.text());
        } catch (JSONException e) {
            throw new IOException("unreadable response from " + path);
        }
    }

    /** POST or DELETE an action; throws {@link ApiException} with the daemon's message on failure. */
    void act(String method, String path, int timeoutMs) throws IOException {
        Response r = request(method, path, null, timeoutMs);
        if (r.status == 304) return; // already in the requested state
        ok(r);
    }

    static Response ok(Response r) throws ApiException {
        if (r.status >= 200 && r.status < 300) return r;
        String msg = "HTTP " + r.status;
        try {
            msg = new JSONObject(r.text()).optString("message", msg);
        } catch (JSONException ignored) {
        }
        throw new ApiException(r.status, msg);
    }

    /** Opens the log stream of a container: the last {@code tail} lines, then live output. */
    LogStream openLogs(String id, boolean tty, int tail) throws IOException {
        LocalSocket s = connect(0);
        try {
            send(s.getOutputStream(), "GET", "/containers/" + id + "/logs?stdout=1&stderr=1&follow=1&tail=" + tail, null);
            InputStream in = s.getInputStream();
            int status = readHead(in, null);
            if (status != 200) {
                throw new ApiException(status, "logs: HTTP " + status);
            }
            return new LogStream(s, in, tty);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    /** A running log stream; {@link #close} from any thread ends a blocked read. */
    static final class LogStream implements java.io.Closeable {
        private final LocalSocket socket;
        private final InputStream in;
        private final boolean tty;

        LogStream(LocalSocket socket, InputStream in, boolean tty) {
            this.socket = socket;
            this.in = in;
            this.tty = tty;
        }

        /** The next piece of output, or null at the end of the stream. */
        byte[] next() throws IOException {
            return tty ? readRaw(in) : Demux.next(in);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] readRaw(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int n = in.read(buf);
        if (n < 0) return null;
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    /** Docker's stream framing: 8-byte header [type,0,0,0,size(BE32)] then the payload. */
    static final class Demux {
        /** One frame's payload is never larger than this; a bigger one ends the stream. */
        static final int MAX_FRAME = 1 << 20;

        static byte[] next(InputStream in) throws IOException {
            byte[] hdr = new byte[8];
            int got = 0;
            while (got < 8) {
                int n = in.read(hdr, got, 8 - got);
                if (n < 0) {
                    if (got == 0) return null;
                    throw new EOFException("truncated frame header");
                }
                got += n;
            }
            long size = ((hdr[4] & 0xffL) << 24) | ((hdr[5] & 0xffL) << 16) | ((hdr[6] & 0xffL) << 8) | (hdr[7] & 0xffL);
            if (size > MAX_FRAME) throw new IOException("log frame of " + size + " bytes refused");
            byte[] payload = new byte[(int) size];
            int off = 0;
            while (off < payload.length) {
                int n = in.read(payload, off, payload.length - off);
                if (n < 0) throw new EOFException("truncated frame");
                off += n;
            }
            return payload;
        }
    }

    /**
     * Reads the status line and headers; returns the status. Headers are
     * lower-cased into {@code headers} (name: value lines) when it is non-null.
     */
    static int readHead(InputStream in, StringBuilder headers) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0; // matching \r\n\r\n
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new EOFException("connection closed before the response headers ended");
            head.write(b);
            if (head.size() > MAX_HEADER) throw new IOException("response headers too large");
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = b == '\r' ? 1 : 0;
        }
        String text = head.toString("ISO-8859-1");
        String[] lines = text.split("\r\n");
        String[] status = lines[0].split(" ", 3);
        if (status.length < 2 || !status[0].startsWith("HTTP/")) throw new IOException("malformed status line");
        int code;
        try {
            code = Integer.parseInt(status[1]);
        } catch (NumberFormatException e) {
            throw new IOException("malformed status code");
        }
        if (headers != null) {
            for (int i = 1; i < lines.length; i++) headers.append(lines[i].toLowerCase(Locale.ROOT)).append('\n');
        }
        return code;
    }

    /** Parses a whole HTTP response: Content-Length, chunked, or EOF-delimited body. */
    static Response parse(InputStream in) throws IOException {
        StringBuilder headers = new StringBuilder();
        int status = readHead(in, headers);
        String h = headers.toString();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (h.contains("transfer-encoding: chunked")) {
            while (true) {
                String line = readLine(in);
                int semi = line.indexOf(';');
                long size;
                try {
                    size = Long.parseLong((semi >= 0 ? line.substring(0, semi) : line).trim(), 16);
                } catch (NumberFormatException e) {
                    throw new IOException("malformed chunk size");
                }
                if (size == 0) break;
                if (body.size() + size > MAX_BODY) throw new IOException("response too large");
                copy(in, body, size);
                readLine(in);
            }
        } else {
            long length = -1;
            for (String line : h.split("\n")) {
                if (line.startsWith("content-length:")) {
                    try {
                        length = Long.parseLong(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        throw new IOException("malformed Content-Length");
                    }
                }
            }
            if (length > MAX_BODY) throw new IOException("response too large");
            if (length >= 0) {
                copy(in, body, length);
            } else {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (body.size() + n > MAX_BODY) throw new IOException("response too large");
                    body.write(buf, 0, n);
                }
            }
        }
        return new Response(status, body.toByteArray());
    }

    private static void copy(InputStream in, ByteArrayOutputStream out, long count) throws IOException {
        byte[] buf = new byte[8192];
        while (count > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, count));
            if (n < 0) throw new EOFException("response body truncated");
            out.write(buf, 0, n);
            count -= n;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("truncated chunked body");
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
            if (sb.length() > 64) throw new IOException("malformed chunk header");
        }
        return sb.toString();
    }
}
