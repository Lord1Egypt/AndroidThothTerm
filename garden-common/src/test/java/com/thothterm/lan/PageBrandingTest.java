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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class PageBrandingTest {
    @Test
    public void namesTheEditionEverywhereThePageAsks() throws Exception {
        byte[] page = Files.readAllBytes(new File("src/main/assets/lan/index.html").toPath());
        String html = new String(PageBranding.apply(page, "ThothTerm Debian"), StandardCharsets.UTF_8);

        assertFalse(html.contains(PageBranding.PLACEHOLDER));
        assertFalse("the shared page must not name a distribution",
                new String(page, StandardCharsets.UTF_8).matches("(?s).*(Ubuntu|Debian).*"));
        assertEquals(2, html.split("ThothTerm Debian", -1).length - 1);
    }

    @Test
    public void escapesMarkup() {
        assertEquals("a&amp;b &lt;i&gt; &quot;x&quot; &#39;y&#39;",
                PageBranding.escape("a&b <i> \"x\" 'y'"));
    }
}
