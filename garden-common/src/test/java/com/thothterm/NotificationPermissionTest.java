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

package com.thothterm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Locks the consent rule for the ongoing terminal-service notification.
 * The service must run regardless of the answer; only visibility depends on it.
 */
public class NotificationPermissionTest {
    private static final int ANDROID_12 = 32;
    private static final int ANDROID_13 = 33;
    private static final int ANDROID_16 = 36;

    @Test
    public void runtimeGatingStartsAtAndroid13() {
        assertFalse(NotificationPermission.isRuntimeGated(ANDROID_12));
        assertTrue(NotificationPermission.isRuntimeGated(ANDROID_13));
        assertTrue(NotificationPermission.isRuntimeGated(ANDROID_16));
    }

    @Test
    public void asksOnTheTargetDeviceWhenConsentIsMissing() {
        // Samsung SM-A165F, Android 16 / API 36: the reproduction case.
        assertTrue(NotificationPermission.shouldRequest(ANDROID_16, false, false));
    }

    @Test
    public void neverAsksBeforeAndroid13() {
        // The notification is posted without consent there, so a prompt is noise.
        assertFalse(NotificationPermission.shouldRequest(ANDROID_12, false, false));
        assertFalse(NotificationPermission.shouldRequest(ANDROID_12, false, true));
    }

    @Test
    public void neverAsksOnceGranted() {
        assertFalse(NotificationPermission.shouldRequest(ANDROID_16, true, false));
        assertFalse(NotificationPermission.shouldRequest(ANDROID_16, true, true));
    }

    @Test
    public void asksAtMostOnce() {
        // A second prompt would be spam; the platform suppresses it anyway.
        assertFalse(NotificationPermission.shouldRequest(ANDROID_16, false, true));
    }
}
