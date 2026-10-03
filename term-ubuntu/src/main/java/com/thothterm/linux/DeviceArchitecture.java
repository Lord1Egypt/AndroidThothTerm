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

import java.util.Locale;

/**
 * ARM64 detection for the Ubuntu edition.
 *
 * <p>Android's ABI list ({@code android.os.Build.SUPPORTED_ABIS}) is the
 * authoritative compatibility gate and uses ABI names such as
 * {@code arm64-v8a}. The JVM/kernel architecture name
 * ({@code System.getProperty("os.arch")}) uses different spellings such as
 * {@code aarch64}; it is only a secondary signal and must never replace the
 * Android ABI check.</p>
 *
 * <p>All comparisons are exact after trimming and lower-casing, so 32-bit ARM
 * ({@code armeabi-v7a}, {@code armv7l}) can never be misclassified as ARM64.</p>
 */
public final class DeviceArchitecture {
    public static final String ABI_ARM64 = "arm64-v8a";

    private DeviceArchitecture() {
    }

    /**
     * @return true when the Android supported-ABI list contains {@code arm64-v8a}.
     *         Null and empty lists fail safely.
     */
    public static boolean isArm64Supported(String[] supportedAbis) {
        if (supportedAbis == null || supportedAbis.length == 0) return false;
        for (String abi : supportedAbis) {
            if (abi != null && ABI_ARM64.equals(abi.trim().toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Secondary, diagnostic-only normalization of {@code os.arch}. Never the
     * Android ABI gate.
     */
    public static boolean isArm64OsArch(String osArch) {
        if (osArch == null) return false;
        String value = osArch.trim().toLowerCase(Locale.US);
        return value.equals("aarch64") || value.equals("arm64");
    }

    /** Renders an ABI list for safe, non-sensitive diagnostics. */
    public static String describe(String[] supportedAbis) {
        if (supportedAbis == null) return "null";
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < supportedAbis.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(supportedAbis[i]);
        }
        return builder.append(']').toString();
    }
}
