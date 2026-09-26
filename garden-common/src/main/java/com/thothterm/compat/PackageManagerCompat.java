/*
 * Copyright (C) 2022-2025 Roumen Petrov.  All rights reserved.
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

package com.thothterm.compat;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.List;

import androidx.annotation.RequiresApi;


public class PackageManagerCompat {

    public static PackageInfo getPackageInfo(PackageManager pm, String name)
            throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU /*API level 33*/)
            return Compat33.getPackageInfo(pm, name);
        else
            return Compat1.getPackageInfo(pm, name);
    }

    public static ActivityInfo getActivityInfo(PackageManager pm, ComponentName name)
            throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU /*API level 33*/)
            return Compat33.getActivityInfo(pm, name);
        else
            return Compat1.getActivityInfo(pm, name);
    }

    public static List<ResolveInfo> queryIntentActivities(PackageManager pm, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU /*API level 33*/)
            return Compat33.queryIntentActivities(pm, intent);
        else
            return Compat1.queryIntentActivities(pm, intent);
    }

    @RequiresApi(33)
    private static class Compat33 {
        private static PackageInfo getPackageInfo(PackageManager pm, String name)
                throws PackageManager.NameNotFoundException {
            PackageManager.PackageInfoFlags flags = PackageManager.PackageInfoFlags.of(0);
            return pm.getPackageInfo(name, flags);
        }

        private static ActivityInfo getActivityInfo(PackageManager pm, ComponentName name)
                throws PackageManager.NameNotFoundException {
            PackageManager.ComponentInfoFlags flags = PackageManager.ComponentInfoFlags.of(0);
            return pm.getActivityInfo(name, flags);
        }

        private static List<ResolveInfo> queryIntentActivities(PackageManager pm, Intent intent) {
            PackageManager.ResolveInfoFlags flags = PackageManager.ResolveInfoFlags.of(0);
            return pm.queryIntentActivities(intent, flags);
        }
    }

    private static class Compat1 {
        // Explicitly suppress deprecation warnings
        // "getPackageInfo(String,int) in PackageManager has been deprecated"
        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        private static PackageInfo getPackageInfo(PackageManager pm, String name)
                throws PackageManager.NameNotFoundException {
            return pm.getPackageInfo(name, 0);
        }

        // Explicitly suppress deprecation warnings
        // "getActivityInfo(ComponentName,int) in PackageManager has been deprecated"
        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        private static ActivityInfo getActivityInfo(PackageManager pm, ComponentName name)
                throws PackageManager.NameNotFoundException {
            return pm.getActivityInfo(name, 0);
        }

        // Explicitly suppress deprecation warnings
        // "queryIntentActivities(Intent,int) in PackageManager has been deprecated"
        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        private static List<ResolveInfo> queryIntentActivities(PackageManager pm, Intent intent) {
            return pm.queryIntentActivities(intent, 0);
        }
    }
}
