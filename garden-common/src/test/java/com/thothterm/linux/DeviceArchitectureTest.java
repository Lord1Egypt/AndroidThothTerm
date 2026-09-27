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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceArchitectureTest {

    @Test
    public void androidAbiListDetectsArm64() {
        assertTrue(DeviceArchitecture.isArm64Supported(new String[]{"arm64-v8a"}));
        assertTrue(DeviceArchitecture.isArm64Supported(
                new String[]{"arm64-v8a", "armeabi-v7a"}));
        assertTrue(DeviceArchitecture.isArm64Supported(
                new String[]{"armeabi-v7a", "arm64-v8a", "x86_64"}));
    }

    @Test
    public void androidAbiListRejectsNonArm64() {
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"armeabi-v7a"}));
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"x86_64", "x86"}));
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"x86_64"}));
        // "arm64" is not an Android ABI name; only "arm64-v8a" is accepted.
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"arm64"}));
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"aarch64"}));
    }

    @Test
    public void androidAbiListFailsSafely() {
        assertFalse(DeviceArchitecture.isArm64Supported(new String[0]));
        assertFalse(DeviceArchitecture.isArm64Supported(null));
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{null}));
        assertFalse(DeviceArchitecture.isArm64Supported(new String[]{"  "}));
    }

    @Test
    public void osArchIsSecondarySignalOnly() {
        assertTrue(DeviceArchitecture.isArm64OsArch("aarch64"));
        assertTrue(DeviceArchitecture.isArm64OsArch("arm64"));
        assertTrue(DeviceArchitecture.isArm64OsArch("AARCH64"));
        assertFalse(DeviceArchitecture.isArm64OsArch("armv7l"));
        assertFalse(DeviceArchitecture.isArm64OsArch("arm"));
        assertFalse(DeviceArchitecture.isArm64OsArch("x86_64"));
        assertFalse(DeviceArchitecture.isArm64OsArch(null));
    }

    @Test
    public void describeRendersAbiList() {
        assertEquals("[arm64-v8a,armeabi-v7a]",
                DeviceArchitecture.describe(new String[]{"arm64-v8a", "armeabi-v7a"}));
        assertEquals("[]", DeviceArchitecture.describe(new String[0]));
        assertEquals("null", DeviceArchitecture.describe(null));
    }
}
