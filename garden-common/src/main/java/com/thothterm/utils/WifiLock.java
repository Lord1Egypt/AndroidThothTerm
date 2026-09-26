/*
 * Copyright (C) 2019-2023 Roumen Petrov.  All rights reserved.
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

package com.thothterm.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;


public class WifiLock {
    private static WifiManager.WifiLock lock = null;

    public static void create(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;
        try {
            lock = WifiLockCompat.create(wm, "SB-Pro WifiLock");
        } catch (Exception ignore) {
        }
    }

    public static void release() {
        if (lock == null) return;

        if (lock.isHeld())
            lock.release();
        lock = null;
    }

    public static boolean exist() {
        return lock != null;
    }

    public static boolean isHeld() {
        return (lock != null) && lock.isHeld();
    }

    public static void toggle(@SuppressWarnings("unused") Context context) {
        if (lock == null) return;

        if (lock.isHeld()) {
            lock.release();
        } else {
            lock.acquire();
        }
    }

    private static class WifiLockCompat {
        private static WifiManager.WifiLock create(WifiManager wm, String tag) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE /*API level 34*/)
                return Compat34.create(wm, tag);
            else
                return Compat12.create(wm, tag);
        }

        private static class Compat12 {
            // Explicitly suppress deprecation warnings
            // WIFI_MODE_FULL_HIGH_PERF in WifiManager has been deprecated
            @SuppressWarnings({"deprecation", "RedundantSuppression"})
            private static WifiManager.WifiLock create(WifiManager wm, String tag) {
                return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag);
            }
        }

        @RequiresApi(33)
        private static class Compat34 {
            private static WifiManager.WifiLock create(WifiManager wm, String tag) {
                // The WIFI_MODE_FULL_HIGH_PERF is deprecated in API level 34 and is automatically
                // replaced with WIFI_MODE_FULL_LOW_LATENCY with all the restrictions documented
                // on that lock. Note WIFI_MODE_FULL_LOW_LATENCY was added in API level 29.
                return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, tag);
            }
        }
    }
}
