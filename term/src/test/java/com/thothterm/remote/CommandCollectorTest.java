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
package com.thothterm.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Add-on command names become alias lines in the shell's startup. */
public class CommandCollectorTest {
    @Test
    public void plainCommandNamesAreAccepted() {
        for (String name : new String[]{"ls", "gpg2", "ssh-keygen", "t1p_x.y", "c++", "7z"}) {
            assertTrue(name, CommandCollector.isCommandName(name));
        }
    }

    @Test
    public void anythingTheShellWouldParseIsRefused() {
        for (String name : new String[]{null, "", "a b", "a'b", "a\"b", "a;b",
                "$(x)", "`x`", "-x", "a|b", "a&b", "a/b", "é"}) {
            assertFalse(String.valueOf(name), CommandCollector.isCommandName(name));
        }
        for (char c = 0; c <= 0x7f; c = c == 0x1f ? 0x7f : (char) (c + 1)) {
            assertFalse("control " + (int) c, CommandCollector.isCommandName("a" + c + "b"));
        }
    }
}
