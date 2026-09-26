/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Map;

public class FlatJsonTest {
    @Test
    public void parsesFlatObjects() {
        Map<String, Object> m = FlatJson.parse(" {\"type\":\"open\", \"term\":null, \"cols\":120, \"rows\":-1, \"ok\":true} ");
        assertEquals("open", m.get("type"));
        assertNull(m.get("term"));
        assertEquals(120L, m.get("cols"));
        assertEquals(-1L, m.get("rows"));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertEquals(0, FlatJson.parse("{}").size());
    }

    @Test
    public void decodesEscapes() {
        assertEquals("a\"b\\c\n\u0645", FlatJson.parse("{\"k\":\"a\\\"b\\\\c\\n\\u0645\"}").get("k"));
    }

    @Test
    public void rejectsAnythingElse() {
        String[] bad = {"", "[]", "{\"a\":{}}", "{\"a\":[1]}", "{\"a\":1.5}", "{\"a\":1,}",
                "{\"a\":1} x", "{\"a\":1,\"a\":2}", "{\"a\":\"\\x\"}", "{a:1}", "{\"a\":\"\n\"}",
                "{\"a\":12345678901234567890}"};
        for (String text : bad) {
            try {
                FlatJson.parse(text);
                fail("accepted " + text);
            } catch (IllegalArgumentException expected) {
                // Rejected, as it must be.
            }
        }
    }

    @Test
    public void quotesSafely() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\\u2028\"", FlatJson.quote("a\"b\\c\n\u0001\u2028"));
        assertEquals("x\"y", FlatJson.parse("{\"k\":" + FlatJson.quote("x\"y") + "}").get("k"));
    }
}
