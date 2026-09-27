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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** RFC 6455 framing, server side. */
final class WebSocketCodec {
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_CLOSE = 0x8;
    static final int OP_PING = 0x9;
    static final int OP_PONG = 0xA;

    /** Largest message a browser may send: a big paste, not a file upload. */
    static final int MAX_MESSAGE = 1 << 20;

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** A violation that ends the connection with {@code closeCode}. */
    static final class ProtocolError extends IOException {
        final int closeCode;

        ProtocolError(int closeCode, String reason) {
            super(reason);
            this.closeCode = closeCode;
        }
    }

    static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    /** A complete (reassembled) message, or a control frame. */
    static final class Message {
        final int opcode;
        final byte[] payload;

        Message(int opcode, byte[] payload) {
            this.opcode = opcode;
            this.payload = payload;
        }

        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    private WebSocketCodec() {
    }

    static String acceptKey(String clientKey) {
        try {
            byte[] sha1 = MessageDigest.getInstance("SHA-1")
                    .digest((clientKey + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A valid Sec-WebSocket-Key is 16 random bytes, base64-encoded. */
    static boolean isValidKey(String key) {
        if (key == null || key.length() != 24) return false;
        try {
            return Base64.getDecoder().decode(key).length == 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Read one client frame. Client frames must be masked (RFC 6455 5.1). */
    static Frame readFrame(InputStream in, int maxPayload) throws IOException {
        int b0 = readByte(in);
        int b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        if ((b0 & 0x70) != 0) throw new ProtocolError(1002, "reserved bits set");
        int opcode = b0 & 0x0f;
        boolean control = (opcode & 0x8) != 0;
        if (opcode != OP_CONTINUATION && opcode != OP_TEXT && opcode != OP_BINARY
                && opcode != OP_CLOSE && opcode != OP_PING && opcode != OP_PONG) {
            throw new ProtocolError(1002, "unknown opcode");
        }
        if ((b1 & 0x80) == 0) throw new ProtocolError(1002, "unmasked client frame");
        long length = b1 & 0x7f;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; ++i) length = (length << 8) | readByte(in);
            if (length < 0) throw new ProtocolError(1002, "length overflow");
        }
        if (control && (!fin || length > 125)) throw new ProtocolError(1002, "bad control frame");
        if (length > maxPayload) throw new ProtocolError(1009, "message too big");
        byte[] mask = new byte[4];
        readFully(in, mask);
        byte[] payload = new byte[(int) length];
        readFully(in, payload);
        for (int i = 0; i < payload.length; ++i) payload[i] ^= mask[i & 3];
        return new Frame(fin, opcode, payload);
    }

    /**
     * Read the next message, reassembling fragments. Control frames that
     * arrive between fragments are returned on their own; {@code partial}
     * carries the fragments across such calls.
     */
    static Message readMessage(InputStream in, Partial partial) throws IOException {
        while (true) {
            Frame frame = readFrame(in, MAX_MESSAGE);
            if ((frame.opcode & 0x8) != 0) return new Message(frame.opcode, frame.payload);
            if (frame.opcode == OP_CONTINUATION) {
                if (partial.opcode < 0) throw new ProtocolError(1002, "unexpected continuation");
            } else {
                if (partial.opcode >= 0) throw new ProtocolError(1002, "interleaved message");
                partial.opcode = frame.opcode;
            }
            if (partial.data.size() + frame.payload.length > MAX_MESSAGE) {
                throw new ProtocolError(1009, "message too big");
            }
            partial.data.write(frame.payload, 0, frame.payload.length);
            if (frame.fin) {
                Message message = new Message(partial.opcode, partial.data.toByteArray());
                partial.opcode = -1;
                partial.data.reset();
                return message;
            }
        }
    }

    static final class Partial {
        int opcode = -1;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
    }

    /** Write one unmasked, unfragmented server frame. */
    static void writeFrame(OutputStream out, int opcode, byte[] payload, int offset, int length)
            throws IOException {
        byte[] head;
        if (length < 126) {
            head = new byte[]{(byte) (0x80 | opcode), (byte) length};
        } else if (length < 65536) {
            head = new byte[]{(byte) (0x80 | opcode), 126, (byte) (length >>> 8), (byte) length};
        } else {
            head = new byte[10];
            head[0] = (byte) (0x80 | opcode);
            head[1] = 127;
            for (int i = 0; i < 8; ++i) head[9 - i] = (byte) (((long) length) >>> (8 * i));
        }
        out.write(head);
        out.write(payload, offset, length);
        out.flush();
    }

    static byte[] closePayload(int code, String reason) {
        byte[] text = reason.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(text.length, 123);
        byte[] payload = new byte[2 + n];
        payload[0] = (byte) (code >>> 8);
        payload[1] = (byte) code;
        System.arraycopy(text, 0, payload, 2, n);
        return payload;
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n < 0) throw new EOFException();
            read += n;
        }
    }
}
