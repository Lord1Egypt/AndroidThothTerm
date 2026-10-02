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

package jackpal.androidterm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * Back navigation for SDK 36 (lint GestureBackNavigation): both terminal
 * activities, ThothTerm Ubuntu's and the Garden editions', take back through
 * the activity's OnBackPressedDispatcher into one decision, and neither
 * intercepts KEYCODE_BACK in onKeyUp/onKeyDown nor overrides onBackPressed.
 */
public class BackNavigationSourceTest {
    private static final String[] TERMS = {
            "term-ubuntu/src/main/java/jackpal/androidterm/Term.java",
            "garden-common/src/main/java/jackpal/androidterm/Term.java",
    };

    @Test
    public void backGoesThroughTheDispatcherIntoOneDecision() throws Exception {
        for (String path : TERMS) {
            String source = read(path);
            String code = source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
            assertTrue(path, code.contains("getOnBackPressedDispatcher().addCallback(this, mBackCallback);"));
            assertTrue(path, code.contains("new OnBackPressedCallback(true)"));
            assertEquals(path + ": every back path calls handleBackAction()", 3,
                    count(code, "handleBackAction()"));
            assertTrue(path, code.contains("BackAction.decide("));
            assertFalse(path + " overrides onBackPressed",
                    Pattern.compile("void\\s+onBackPressed\\s*\\(").matcher(code).find());
            for (String method : new String[]{"onKeyUp", "onKeyDown", "onKeyLongPress"}) {
                String body = method(code, "public boolean " + method + "(");
                assertFalse(path + ": " + method + " intercepts KEYCODE_BACK", body.contains("KEYCODE_BACK"));
            }
            // The callback is registered once, right after super.onCreate.
            int create = code.indexOf("super.onCreate(icicle);");
            assertTrue(path, create >= 0 && code.indexOf("getOnBackPressedDispatcher()") > create);
        }
    }

    @Test
    public void bothEditionsShareOneDecision() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(new File(repo(), "term-ubuntu/src/main/java/jackpal/androidterm/BackAction.java").toPath()),
                Files.readAllBytes(new File(repo(), "garden-common/src/main/java/jackpal/androidterm/BackAction.java").toPath()));
    }

    private static String method(String code, String signature) {
        int start = code.indexOf(signature);
        if (start < 0) return "";
        int end = code.indexOf("\n    }\n", start);
        return code.substring(start, end < 0 ? code.length() : end);
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(repo(), path).toPath()), StandardCharsets.UTF_8);
    }

    private static File repo() {
        File here = new File("").getAbsoluteFile();
        return new File(here, "third_party").isDirectory() ? here : here.getParentFile();
    }
}
