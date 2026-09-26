/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WebSocketCodecTest {
    /** A client frame as a browser sends it: masked. */
    static byte[] clientFrame(boolean fin, int opcode, byte[] payload, boolean masked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((fin ? 0x80 : 0) | opcode);
        int maskBit = masked ? 0x80 : 0;
        if (payload.length < 126) {
            out.write(maskBit | payload.length);
        } else if (payload.length < 65536) {
            out.write(maskBit | 126);
            out.write(payload.length >>> 8);
            out.write(payload.length);
        } else {
            out.write(maskBit | 127);
            for (int i = 7; i >= 0; --i) out.write((int) (((long) payload.length) >>> (8 * i)));
        }
        byte[] mask = {0x37, (byte) 0xfa, 0x21, 0x3d};
        if (masked) out.write(mask, 0, 4);
        for (int i = 0; i < payload.length; ++i) {
            out.write(masked ? payload[i] ^ mask[i & 3] : payload[i]);
        }
        return out.toByteArray();
    }

    @Test
    public void computesTheRfcAcceptKey() {
        // RFC 6455 section 1.3.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocketCodec.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
        assertTrue(WebSocketCodec.isValidKey("dGhlIHNhbXBsZSBub25jZQ=="));
        assertFalse(WebSocketCodec.isValidKey("short"));
        assertFalse(WebSocketCodec.isValidKey(null));
        assertFalse(WebSocketCodec.isValidKey("!!!!!!!!!!!!!!!!!!!!!!=="));
    }

    @Test
    public void readsMaskedFramesOfEveryLengthForm() throws IOException {
        for (int n : new int[]{0, 5, 125, 126, 300, 65535, 65536, 70000}) {
            byte[] payload = new byte[n];
            for (int i = 0; i < n; ++i) payload[i] = (byte) (i * 7);
            WebSocketCodec.Frame frame = WebSocketCodec.readFrame(
                    new ByteArrayInputStream(clientFrame(true, WebSocketCodec.OP_BINARY, payload, true)),
                    WebSocketCodec.MAX_MESSAGE);
            assertEquals(WebSocketCodec.OP_BINARY, frame.opcode);
            assertArrayEquals(payload, frame.payload);
        }
    }

    @Test
    public void refusesUnmaskedClientFrames() throws IOException {
        try {
            WebSocketCodec.readFrame(new ByteArrayInputStream(
                    clientFrame(true, WebSocketCodec.OP_TEXT, "x".getBytes(), false)), 100);
            fail();
        } catch (WebSocketCodec.ProtocolError e) {
            assertEquals(1002, e.closeCode);
        }
    }

    @Test
    public void refusesOversizedAndMalformedFrames() throws IOException {
        expectError(clientFrame(true, WebSocketCodec.OP_BINARY, new byte[200], true), 100, 1009);
        expectError(clientFrame(false, WebSocketCodec.OP_PING, new byte[1], true), 100, 1002);
        expectError(clientFrame(true, WebSocketCodec.OP_PING, new byte[126], true), 1000, 1002);
        expectError(clientFrame(true, 0x3, new byte[1], true), 100, 1002);
        byte[] reserved = clientFrame(true, WebSocketCodec.OP_TEXT, new byte[1], true);
        reserved[0] |= 0x40;
        expectError(reserved, 100, 1002);
    }

    private static void expectError(byte[] frame, int max, int code) throws IOException {
        try {
            WebSocketCodec.readFrame(new ByteArrayInputStream(frame), max);
            fail();
        } catch (WebSocketCodec.ProtocolError e) {
            assertEquals(code, e.closeCode);
        }
    }

    @Test
    public void reassemblesFragmentsAroundControlFrames() throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(clientFrame(false, WebSocketCodec.OP_TEXT, "he".getBytes(), true));
        wire.write(clientFrame(true, WebSocketCodec.OP_PING, "p".getBytes(), true));
        wire.write(clientFrame(true, WebSocketCodec.OP_CONTINUATION, "llo".getBytes(), true));
        ByteArrayInputStream in = new ByteArrayInputStream(wire.toByteArray());
        WebSocketCodec.Partial partial = new WebSocketCodec.Partial();
        assertEquals(WebSocketCodec.OP_PING, WebSocketCodec.readMessage(in, partial).opcode);
        WebSocketCodec.Message m = WebSocketCodec.readMessage(in, partial);
        assertEquals(WebSocketCodec.OP_TEXT, m.opcode);
        assertEquals("hello", m.text());
    }

    @Test
    public void refusesAStrayContinuation() throws IOException {
        try {
            WebSocketCodec.readMessage(new ByteArrayInputStream(
                    clientFrame(true, WebSocketCodec.OP_CONTINUATION, new byte[1], true)),
                    new WebSocketCodec.Partial());
            fail();
        } catch (WebSocketCodec.ProtocolError e) {
            assertEquals(1002, e.closeCode);
        }
    }

    @Test
    public void writesUnmaskedServerFrames() throws IOException {
        for (int n : new int[]{3, 200, 70000}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            WebSocketCodec.writeFrame(out, WebSocketCodec.OP_BINARY, new byte[n], 0, n);
            byte[] b = out.toByteArray();
            assertEquals(0x82, b[0] & 0xff);
            assertEquals(0, b[1] & 0x80);
            int header = n < 126 ? 2 : n < 65536 ? 4 : 10;
            assertEquals(header + n, b.length);
        }
    }
}
