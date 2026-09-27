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


package com.thothterm.lan;

import java.nio.charset.StandardCharsets;

/**
 * Puts the edition's product name into the LAN page. The page is shared by
 * every Garden edition and carries {@value #PLACEHOLDER} where the name goes.
 */
final class PageBranding {
    static final String PLACEHOLDER = "{{EDITION}}";

    private PageBranding() {
    }

    static byte[] apply(byte[] page, String edition) {
        String html = new String(page, StandardCharsets.UTF_8);
        return html.replace(PLACEHOLDER, escape(edition)).getBytes(StandardCharsets.UTF_8);
    }

    /** HTML text escaping; the name lands in element content only. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}
