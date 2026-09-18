/*
 * Copyright (C) 2018-2025 Roumen Petrov.  All rights reserved.
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;


public class Permissions {
    public static final int REQUEST_EXTERNAL_STORAGE = 101;
    static final String[] external_storage_permissions;

    static {
        ArrayList<String> list = new ArrayList<>();

        // On Android 11 (API Level 30) we should use permission MANAGE_EXTERNAL_STORAGE
        // but there is no way terminal application to pass Google policy requirement.
        // Android 10 (API Level 29) with android:requestLegacyExternalStorage set
        // is last that could use permission WRITE_EXTERNAL_STORAGE.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q /*API Level 29*/)
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // Implicitly granted if WRITE_EXTERNAL_STORAGE is requested.
        // Starting in API level 33, this permission has no effect.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 /*API Level 32*/) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // to use more simple checks construct a list, even empty
        external_storage_permissions = list.toArray(new String[0]);
    }


    public static boolean permissionExternalStorage(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R /*API Level 30*/) {
            if (PermissionManageExternal.isGranted())
                return true;
        }
        for (String permission : external_storage_permissions) {
            int status = ActivityCompat.checkSelfPermission(activity, permission);
            if (status != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return external_storage_permissions.length > 0;
    }

    private static boolean shouldShowExternalStorageRationale(AppCompatActivity activity) {
        for (String permission : external_storage_permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
                return true;
        }
        return false;
    }

    private static void requestPermissionExternalStorage(AppCompatActivity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, external_storage_permissions, requestCode);
    }

    public static void requestExternalStorage(AppCompatActivity activity, View view, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R /*API Level 30*/) {
            if (PermissionManageExternal.active) {
                PermissionManageExternal.request(activity, view, requestCode);
                return;
            }
        }
        requestExternalStoragePermissions(activity, view, requestCode);
    }

    public static void requestExternalStoragePermissions(AppCompatActivity activity, View view, int requestCode) {
        if (external_storage_permissions.length == 0) return;
        if (shouldShowExternalStorageRationale(activity))
            Snackbar.make(view,
                            R.string.message_external_storage_rationale,
                            Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok,
                            v -> requestPermissionExternalStorage(activity, requestCode))
                    .show();
        else
            requestPermissionExternalStorage(activity, requestCode);
    }

    public static boolean isPermissionGranted(int[] grantResults) {
        // Note if request is cancelled, the result arrays are empty.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return grantResults.length > 0; // i.e. false by default
    }
}
