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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ApiClientTest {
    private static ByteArrayInputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void aRequestIsASingleBuffer() {
        byte[] get = ApiClient.encodeRequest("GET", "/containers/json?all=1", null);
        assertEquals("GET /containers/json?all=1 HTTP/1.0\r\nHost: thothdock\r\nContent-Length: 0\r\n\r\n",
                new String(get, StandardCharsets.US_ASCII));
        byte[] post = ApiClient.encodeRequest("POST", "/x", "{\"a\":1}");
        assertEquals("POST /x HTTP/1.0\r\nHost: thothdock\r\nContent-Type: application/json\r\nContent-Length: 7\r\n\r\n{\"a\":1}",
                new String(post, StandardCharsets.US_ASCII));
    }

    /** A stream that fails a second write the way a closed socket does. */
    @Test
    public void sendNeverWritesTwice() throws Exception {
        final int[] writes = {0};
        java.io.OutputStream out = new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("single-byte write");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (++writes[0] > 1) throw new IOException("Broken pipe");
            }
        };
        java.lang.reflect.Method send = ApiClient.class.getDeclaredMethod("send",
                java.io.OutputStream.class, String.class, String.class, String.class);
        send.setAccessible(true);
        send.invoke(null, out, "POST", "/containers/x/start", null);
        assertEquals(1, writes[0]);
    }

    @Test
    public void parsesContentLengthBodies() throws Exception {
        ApiClient.Response r = ApiClient.parse(in("HTTP/1.0 200 OK\r\nContent-Type: application/json\r\nContent-Length: 5\r\n\r\nhello"));
        assertEquals(200, r.status);
        assertEquals("hello", r.text());
    }

    @Test
    public void parsesEofDelimitedAndEmptyBodies() throws Exception {
        assertEquals("abc", ApiClient.parse(in("HTTP/1.0 200 OK\r\nX: y\r\n\r\nabc")).text());
        ApiClient.Response none = ApiClient.parse(in("HTTP/1.0 204 No Content\r\n\r\n"));
        assertEquals(204, none.status);
        assertEquals(0, none.body.length);
    }

    @Test
    public void parsesChunkedBodies() throws Exception {
        String body = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6;ext=1\r\n world\r\n0\r\n\r\n";
        assertEquals("hello world", ApiClient.parse(in(body)).text());
    }

    @Test
    public void refusesMalformedAndOversizedResponses() throws Exception {
        for (String bad : new String[]{"", "garbage\r\n\r\n", "HTTP/1.0 abc OK\r\n\r\n", "HTTP/1.0 200 OK\r\nContent-Length: x\r\n\r\n",
                "HTTP/1.0 200 OK\r\nContent-Length: 99999999\r\n\r\n", "HTTP/1.0 200 OK\r\nContent-Length: 10\r\n\r\nshort",
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\n", "HTTP/1.0 200 OK\r\nno terminator"}) {
            try {
                ApiClient.parse(in(bad));
                fail("accepted " + bad.replace("\r\n", "|"));
            } catch (IOException expected) {
                // fail closed
            }
        }
        StringBuilder huge = new StringBuilder("HTTP/1.0 200 OK\r\nX: ");
        for (int i = 0; i < 70000; i++) huge.append('a');
        try {
            ApiClient.parse(in(huge + "\r\n\r\n"));
            fail("accepted a header section over the limit");
        } catch (IOException expected) {
            // fail closed
        }
    }

    private static byte[] frame(int type, String payload) {
        byte[] p = payload.getBytes(StandardCharsets.UTF_8);
        byte[] f = new byte[8 + p.length];
        f[0] = (byte) type;
        f[4] = (byte) (p.length >>> 24);
        f[5] = (byte) (p.length >>> 16);
        f[6] = (byte) (p.length >>> 8);
        f[7] = (byte) p.length;
        System.arraycopy(p, 0, f, 8, p.length);
        return f;
    }

    @Test
    public void demuxesDockerFrames() throws Exception {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(frame(1, "out\n"));
        all.write(frame(2, "err\n"));
        all.write(frame(1, ""));
        ByteArrayInputStream s = new ByteArrayInputStream(all.toByteArray());
        assertArrayEquals("out\n".getBytes(), ApiClient.Demux.next(s));
        assertArrayEquals("err\n".getBytes(), ApiClient.Demux.next(s));
        assertEquals(0, ApiClient.Demux.next(s).length);
        assertNull(ApiClient.Demux.next(s));
    }

    @Test
    public void demuxRefusesTruncatedAndOversizedFrames() throws Exception {
        byte[] f = frame(1, "hello");
        try {
            ApiClient.Demux.next(new ByteArrayInputStream(java.util.Arrays.copyOf(f, f.length - 2)));
            fail("accepted a truncated frame");
        } catch (EOFException expected) {
            // fail closed
        }
        try {
            ApiClient.Demux.next(new ByteArrayInputStream(new byte[]{1, 0, 0, 0, 0, 1}));
            fail("accepted a truncated header");
        } catch (EOFException expected) {
            // fail closed
        }
        byte[] big = {1, 0, 0, 0, (byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        try {
            ApiClient.Demux.next(new ByteArrayInputStream(big));
            fail("accepted a 2 GiB frame");
        } catch (IOException expected) {
            // refused before allocating
        }
    }
}
