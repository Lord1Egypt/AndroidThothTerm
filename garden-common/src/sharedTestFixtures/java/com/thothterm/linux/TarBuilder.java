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

package com.thothterm.linux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Builds tar streams byte by byte for extractor tests, including the hostile
 * shapes no well-behaved tar tool would write. Names go into the ustar header
 * as raw bytes; {@link #pax} puts a name into a pax extended header instead.
 */
public final class TarBuilder {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public TarBuilder file(String name, String content) {
        return file(name, content.getBytes(UTF8), 0644);
    }

    public TarBuilder file(String name, byte[] content, int mode) {
        return entry(name.getBytes(UTF8), '0', null, content, mode);
    }

    public TarBuilder dir(String name, int mode) {
        return entry(name.getBytes(UTF8), '5', null, new byte[0], mode);
    }

    public TarBuilder symlink(String name, String target) {
        return entry(name.getBytes(UTF8), '2', target.getBytes(UTF8), new byte[0], 0777);
    }

    public TarBuilder hardlink(String name, String target) {
        return entry(name.getBytes(UTF8), '1', target.getBytes(UTF8), new byte[0], 0644);
    }

    public TarBuilder fifo(String name) {
        return entry(name.getBytes(UTF8), '6', null, new byte[0], 0644);
    }

    /** A regular file whose ustar name field holds exactly these bytes. */
    public TarBuilder rawNameFile(byte[] name, String content) {
        return entry(name, '0', null, content.getBytes(UTF8), 0644);
    }

    /** A pax extended header that renames the next entry, then a file entry. */
    public TarBuilder pax(String path, String content) {
        byte[] record = paxRecord("path", path.getBytes(UTF8));
        entry("PaxHeaders/x".getBytes(UTF8), 'x', null, record, 0644);
        return entry("placeholder".getBytes(UTF8), '0', null, content.getBytes(UTF8), 0644);
    }

    /** A pax header whose path value is these raw (possibly malformed) bytes. */
    public TarBuilder paxRaw(byte[] path, String content) {
        byte[] record = paxRecord("path", path);
        entry("PaxHeaders/x".getBytes(UTF8), 'x', null, record, 0644);
        return entry("placeholder".getBytes(UTF8), '0', null, content.getBytes(UTF8), 0644);
    }

    public byte[] build() {
        ByteArrayOutputStream copy = new ByteArrayOutputStream();
        byte[] body = out.toByteArray();
        copy.write(body, 0, body.length);
        copy.write(new byte[1024], 0, 1024);
        return copy.toByteArray();
    }

    private static byte[] paxRecord(String key, byte[] value) {
        byte[] keyBytes = (" " + key + "=").getBytes(UTF8);
        int base = keyBytes.length + value.length + 1;
        int length = base + Integer.toString(base).length();
        if (Integer.toString(length).length() != Integer.toString(base).length()) length++;
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        byte[] len = Integer.toString(length).getBytes(UTF8);
        record.write(len, 0, len.length);
        record.write(keyBytes, 0, keyBytes.length);
        record.write(value, 0, value.length);
        record.write('\n');
        return record.toByteArray();
    }

    private TarBuilder entry(byte[] name, char type, byte[] link, byte[] data, int mode) {
        byte[] header = new byte[512];
        System.arraycopy(name, 0, header, 0, Math.min(name.length, 100));
        ascii(header, 100, String.format("%07o", mode));
        ascii(header, 108, String.format("%07o", 0));
        ascii(header, 116, String.format("%07o", 0));
        ascii(header, 124, String.format("%011o", data.length));
        ascii(header, 136, String.format("%011o", 1_700_000_000L));
        for (int i = 148; i < 156; i++) header[i] = ' ';
        header[156] = (byte) type;
        if (link != null) System.arraycopy(link, 0, header, 157, Math.min(link.length, 100));
        ascii(header, 257, "ustar");
        ascii(header, 263, "00");
        long sum = 0;
        for (byte b : header) sum += b & 0xFF;
        ascii(header, 148, String.format("%06o", sum));
        header[154] = 0;
        header[155] = ' ';
        out.write(header, 0, 512);
        if (data.length > 0) {
            out.write(data, 0, data.length);
            int pad = (512 - (data.length % 512)) % 512;
            out.write(new byte[pad], 0, pad);
        }
        return this;
    }

    private static void ascii(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(Charset.forName("US-ASCII"));
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }
}
