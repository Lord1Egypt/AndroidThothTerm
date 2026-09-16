/*
 * Copyright (C) 2021-2024 Roumen Petrov.  All rights reserved.
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@RequiresApi(30)
public class PermissionManageExternal {
    public static final boolean active = true;
    /* MANAGE_EXTERNAL_STORAGE permission:
     * - https://stackoverflow.com/questions/65876736/how-do-you-request-manage-external-storage-permission-in-android
     * - https://developer.android.com/training/data-storage/manage-all-files
     * Remark: Looks like there is no way native file management to pass Google policy!
     */
    private static final String PREF_FILE = "file_access";
    private static final String KEY_REQUEST_STATUS = "request_status";

    public static boolean isGranted() {
        return Environment.isExternalStorageManager();
    }

    public static void request(AppCompatActivity activity, View termView, int requestCode) {
        if (getRequestStatus(activity) != Status.UNDEFINED) return;

        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_access_all_files, null);

        new AlertDialog.Builder(activity)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        startRequest(activity, termView, requestCode))
                .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                        legacyRequest(activity, termView, requestCode))
                .setOnDismissListener(dialog ->
                        legacyRequest(activity, termView, requestCode))
                .show();
    }

    private static void startRequest(AppCompatActivity activity, View ignoredView, int ignoredRequestCode) {
        try {
            Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
            // startActivityForResult does not work here
            activity.startActivity(intent);
            setRequestStatus(activity, Status.REQUESTED);
            return;
        } catch (Exception ignore) {
        }
        try {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            activity.startActivity(intent);
            setRequestStatus(activity, Status.REQUESTED);
            return;
        } catch (Exception ignore) {
        }
        setRequestStatus(activity, Status.FAILED);
    }

    private static void legacyRequest(AppCompatActivity activity, View view, int requestCode) {
        setRequestStatus(activity, Status.REJECTED);
        Permissions.requestExternalStoragePermissions(activity, view, requestCode);
    }


    @Status
    private static int getRequestStatus(AppCompatActivity activity) {
        SharedPreferences pref = activity.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return pref.getInt(KEY_REQUEST_STATUS, Status.UNDEFINED);
    }

    private static void setRequestStatus(AppCompatActivity activity, @Status int code) {
        SharedPreferences pref = activity.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(KEY_REQUEST_STATUS, code);
        editor.apply();
    }


    @IntDef({Status.UNDEFINED, Status.REQUESTED, Status.REJECTED, Status.FAILED})
    @Retention(RetentionPolicy.SOURCE)
    @interface Status {
        int UNDEFINED = 0;
        int REQUESTED = 1;
        int REJECTED = 2;
        int FAILED = 99;
    }
}
