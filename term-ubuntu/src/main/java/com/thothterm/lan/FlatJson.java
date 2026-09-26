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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The small flat JSON objects LAN Mode exchanges with its own web page:
 * string, integer, boolean and null members only, no nesting. Anything else
 * is rejected. Kept free of org.json so it runs on the JVM in unit tests.
 */
final class FlatJson {
    private final String text;
    private int at;

    private FlatJson(String text) {
        this.text = text;
    }

    /** Parse an object; values come back as String, Long, Boolean or null. */
    static Map<String, Object> parse(String text) {
        FlatJson parser = new FlatJson(text);
        Map<String, Object> result = parser.object();
        parser.space();
        if (parser.at != text.length()) throw new IllegalArgumentException("trailing data");
        return result;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    private Map<String, Object> object() {
        space();
        expect('{');
        Map<String, Object> members = new LinkedHashMap<>();
        space();
        if (peek() == '}') {
            ++at;
            return members;
        }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            space();
            if (members.containsKey(key)) throw new IllegalArgumentException("duplicate key");
            members.put(key, value());
            space();
            char c = next();
            if (c == '}') return members;
            if (c != ',') throw new IllegalArgumentException("expected , or }");
        }
    }

    private Object value() {
        char c = peek();
        if (c == '"') return string();
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
        if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
        if (text.startsWith("null", at)) { at += 4; return null; }
        throw new IllegalArgumentException("unsupported value");
    }

    private Long number() {
        int start = at;
        if (peek() == '-') ++at;
        while (at < text.length() && Character.isDigit(text.charAt(at))) ++at;
        if (at - start > 18 || at == start || text.charAt(at - 1) == '-') {
            throw new IllegalArgumentException("bad number");
        }
        return Long.parseLong(text.substring(start, at));
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return out.toString();
            if (c < 0x20) throw new IllegalArgumentException("control character in string");
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (at + 4 > text.length()) throw new IllegalArgumentException("bad escape");
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("bad escape");
                    }
                    at += 4;
                    break;
                default:
                    throw new IllegalArgumentException("bad escape");
            }
        }
    }

    private void space() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            ++at;
        }
    }

    private char peek() {
        if (at >= text.length()) throw new IllegalArgumentException("unexpected end");
        return text.charAt(at);
    }

    private char next() {
        char c = peek();
        ++at;
        return c;
    }

    private void expect(char c) {
        if (next() != c) throw new IllegalArgumentException("expected " + c);
    }
}
