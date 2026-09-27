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

package com.thothterm.upload;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Names that arrive with an upload -- a browser's file names and folder paths,
 * a document provider's display names -- are untrusted. Each path component is
 * checked on its own; nothing is normalized, so a name is stored exactly as
 * its Unicode was given, or refused.
 */
public final class UploadNames {
    /** Linux's NAME_MAX, in bytes of UTF-8. */
    public static final int MAX_NAME_BYTES = 255;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_PATH_BYTES = 4000;
    /** Hidden staging directories in the target; never accepted as a name. */
    public static final String STAGING_PREFIX = ".thothterm-upload-";

    private static final String[] COMPRESSED_TAR = {".gz", ".bz2", ".xz", ".zst", ".lz", ".lzma", ".z"};

    private UploadNames() {
    }

    /** {@code name} if it can be one path component, else BAD_NAME. */
    public static String checkName(String name) throws UploadError {
        if (name == null || name.isEmpty()) throw bad("empty name");
        if (name.equals(".") || name.equals("..")) throw bad("dot name");
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\') throw bad("separator in name");
            if (c < 0x20 || (c >= 0x7f && c <= 0x9f)) throw bad("control character in name");
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(i + 1))) {
                    throw bad("unpaired surrogate in name");
                }
                ++i;
            } else if (Character.isLowSurrogate(c)) {
                throw bad("unpaired surrogate in name");
            }
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) throw bad("name too long");
        if (isDriveSpec(name)) throw bad("drive name");
        if (name.startsWith(STAGING_PREFIX)) throw bad("reserved name");
        // A name that only becomes "." or ".." or a path once %-decoded is
        // refused, however many times it was encoded.
        String decoded = name;
        for (int round = 0; round < 4 && decoded.indexOf('%') >= 0; ++round) {
            String next = decodeLenient(decoded);
            if (next.equals(decoded)) break;
            decoded = next;
            if (decoded.equals(".") || decoded.equals("..") || decoded.indexOf('/') >= 0
                    || decoded.indexOf('\\') >= 0 || decoded.indexOf('\0') >= 0) {
                throw bad("encoded traversal");
            }
        }
        return name;
    }

    /**
     * The components of a {@code /}-separated relative path, each checked.
     * Leading, trailing or doubled separators are refused, not tidied.
     */
    public static List<String> checkRelativePath(String path) throws UploadError {
        if (path == null || path.isEmpty()) throw bad("empty path");
        if (path.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES) throw bad("path too long");
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (true) {
            int slash = path.indexOf('/', start);
            String part = slash < 0 ? path.substring(start) : path.substring(start, slash);
            parts.add(checkName(part));
            if (parts.size() > MAX_DEPTH) throw bad("path too deep");
            if (slash < 0) break;
            start = slash + 1;
        }
        return parts;
    }

    /**
     * A path whose components were each percent-encoded (as
     * {@code encodeURIComponent} does) and joined with {@code /}: decoded
     * exactly once, strictly, then checked.
     */
    public static List<String> decodeRelativePath(String encoded) throws UploadError {
        if (encoded == null || encoded.isEmpty()) throw bad("empty path");
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (true) {
            int slash = encoded.indexOf('/', start);
            String part = slash < 0 ? encoded.substring(start) : encoded.substring(start, slash);
            parts.add(checkName(decodeStrict(part)));
            if (parts.size() > MAX_DEPTH) throw bad("path too deep");
            if (slash < 0) break;
            start = slash + 1;
        }
        int bytes = parts.size() - 1;
        for (String p : parts) bytes += p.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PATH_BYTES) throw bad("path too long");
        return parts;
    }

    /** One percent-encoded component: every {@code %} starts two hex digits; the bytes are UTF-8. */
    static String decodeStrict(String encoded) throws UploadError {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); ++i) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) throw bad("truncated escape");
                int hi = Character.digit(encoded.charAt(i + 1), 16);
                int lo = Character.digit(encoded.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) throw bad("malformed escape");
                bytes.write(hi << 4 | lo);
                i += 2;
            } else if (c > 0x20 && c < 0x7f) {
                bytes.write(c);
            } else {
                throw bad("unencoded character");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            throw bad("not UTF-8");
        }
    }

    /**
     * The {@code n}th name to try when keeping both: {@code n == 0} is the name
     * itself, then {@code "file (1).txt"}, {@code "archive (1).tar.gz"},
     * {@code ".hidden (1)"}, {@code "project (1)"}.
     */
    public static String candidate(String name, int n, boolean directory) throws UploadError {
        if (n == 0) return name;
        String stem = name;
        String extension = "";
        if (!directory) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                stem = name.substring(0, dot);
                extension = name.substring(dot);
                String lower = extension.toLowerCase(Locale.ROOT);
                for (String compressed : COMPRESSED_TAR) {
                    if (lower.equals(compressed) && stem.toLowerCase(Locale.ROOT).endsWith(".tar")
                            && stem.length() > 4) {
                        stem = stem.substring(0, stem.length() - 4);
                        extension = name.substring(stem.length());
                        break;
                    }
                }
            }
        }
        String result = stem + " (" + n + ")" + extension;
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            throw new UploadError(UploadError.Code.CONFLICT, "no room to keep both");
        }
        return result;
    }

    private static boolean isDriveSpec(String name) {
        return name.length() == 2 && name.charAt(1) == ':'
                && ((name.charAt(0) >= 'A' && name.charAt(0) <= 'Z')
                || (name.charAt(0) >= 'a' && name.charAt(0) <= 'z'));
    }

    /** %XX sequences to their bytes, anything else kept; bytes read as UTF-8. */
    private static String decodeLenient(String s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; ++i) {
            if (raw[i] == '%' && i + 2 < raw.length) {
                int hi = Character.digit(raw[i + 1], 16);
                int lo = Character.digit(raw[i + 2], 16);
                if (hi >= 0 && lo >= 0) {
                    bytes.write(hi << 4 | lo);
                    i += 2;
                    continue;
                }
            }
            bytes.write(raw[i]);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static UploadError bad(String why) {
        return new UploadError(UploadError.Code.BAD_NAME, why);
    }
}
