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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One HTTP/1.1 request head and its (small) body, parsed strictly.
 * <p>
 * LAN Mode serves a handful of fixed routes to one browser at a time, so
 * anything unusual -- oversized lines, too many headers, a repeated header,
 * chunked bodies -- is refused rather than interpreted.
 */
final class HttpRequest {
    static final int MAX_LINE = 4096;
    static final int MAX_HEADERS = 64;
    static final int MAX_BODY = 4096;

    /** A request that must be answered with {@code status} and then dropped. */
    static final class BadRequest extends IOException {
        final int status;

        BadRequest(int status, String reason) {
            super(reason);
            this.status = status;
        }
    }

    final String method;
    /** The path without its query string. */
    final String path;
    private final Map<String, String> headers;
    final byte[] body;

    private HttpRequest(String method, String path, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
    }

    /** A header value, or null. Names are case-insensitive. */
    String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /** Whether a comma-separated header contains {@code token}, case-insensitively. */
    boolean headerHasToken(String name, String token) {
        String value = header(name);
        if (value == null) return false;
        for (String part : value.split(",")) {
            if (part.trim().equalsIgnoreCase(token)) return true;
        }
        return false;
    }

    /** Parse one request, or return null if the peer closed before sending one. */
    static HttpRequest read(InputStream in) throws IOException {
        String requestLine = readLine(in, true);
        if (requestLine == null) return null;
        String[] parts = requestLine.split(" ", -1);
        if (parts.length != 3 || !parts[2].startsWith("HTTP/1.")) {
            throw new BadRequest(400, "malformed request line");
        }
        String method = parts[0];
        String target = parts[1];
        if (!target.startsWith("/")) throw new BadRequest(400, "absolute or empty target");
        int query = target.indexOf('?');
        String path = query < 0 ? target : target.substring(0, query);

        Map<String, String> headers = new HashMap<>();
        for (int count = 0; ; ++count) {
            String line = readLine(in, false);
            if (line.isEmpty()) break;
            if (count >= MAX_HEADERS) throw new BadRequest(431, "too many headers");
            int colon = line.indexOf(':');
            if (colon <= 0 || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new BadRequest(400, "malformed header");
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (headers.put(name, value) != null) throw new BadRequest(400, "repeated header");
        }

        if (headers.containsKey("transfer-encoding")) {
            throw new BadRequest(501, "transfer-encoding not supported");
        }
        byte[] body = new byte[0];
        String length = headers.get("content-length");
        if (length != null) {
            int n;
            try {
                n = Integer.parseInt(length);
            } catch (NumberFormatException e) {
                throw new BadRequest(400, "malformed content-length");
            }
            if (n < 0) throw new BadRequest(400, "malformed content-length");
            if (n > MAX_BODY) throw new BadRequest(413, "body too large");
            body = new byte[n];
            int read = 0;
            while (read < n) {
                int r = in.read(body, read, n - read);
                if (r < 0) throw new BadRequest(400, "truncated body");
                read += r;
            }
        }
        return new HttpRequest(method, path, headers, body);
    }

    /**
     * One CRLF- (or LF-) terminated ISO-8859-1 line without its terminator.
     * Returns null only for a clean end of stream before the first byte of
     * a request.
     */
    private static String readLine(InputStream in, boolean firstLine) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (firstLine && line.size() == 0) return null;
                throw new BadRequest(400, "truncated request");
            }
            if (b == '\n') break;
            if (line.size() >= MAX_LINE) throw new BadRequest(431, "line too long");
            line.write(b);
        }
        byte[] bytes = line.toByteArray();
        int n = bytes.length;
        if (n > 0 && bytes[n - 1] == '\r') --n;
        return new String(bytes, 0, n, StandardCharsets.ISO_8859_1);
    }
}
