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

package com.thothterm.logging;

/**
 * Structured log categories. Active categories describe the terminal edition.
 * Reserved categories exist so the future Linux edition can log immediately
 * without inventing new names; no events are emitted for them today.
 */
public final class LogCategory {
    public static final String APP = "APP";
    public static final String UI = "UI";
    public static final String SESSION = "SESSION";
    public static final String PTY = "PTY";
    public static final String SHELL = "SHELL";
    public static final String INSTALLER = "INSTALLER";
    public static final String STORAGE = "STORAGE";
    public static final String NETWORK = "NETWORK";

    public static final String RUNTIME = "RUNTIME";
    public static final String LINUX = "LINUX";
    public static final String ROOTFS = "ROOTFS";
    public static final String PROOT = "PROOT";
    public static final String WEB = "WEB";
    public static final String SECURITY = "SECURITY";

    public static final String[] ACTIVE = {
            APP, UI, SESSION, PTY, SHELL, INSTALLER, STORAGE, NETWORK
    };

    public static final String[] RESERVED = {
            RUNTIME, LINUX, ROOTFS, PROOT, WEB, SECURITY
    };

    private static final String[] ALL = {
            APP, UI, SESSION, PTY, SHELL, INSTALLER, STORAGE, NETWORK,
            RUNTIME, LINUX, ROOTFS, PROOT, WEB, SECURITY
    };

    private LogCategory() {
    }

    public static String[] all() {
        return ALL.clone();
    }

    public static boolean isKnown(String category) {
        if (category == null) return false;
        for (String known : ALL) {
            if (known.equals(category)) return true;
        }
        return false;
    }
}
