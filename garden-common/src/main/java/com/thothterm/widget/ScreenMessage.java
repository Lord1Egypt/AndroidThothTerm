/*
 * Copyright (C) 2017-2023 Roumen Petrov.  All rights reserved.
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
package com.thothterm.widget;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.widget.Toast;


public class ScreenMessage {

    public static void show(Context context, Integer rid) {
        show(Toast.makeText(context.getApplicationContext(), rid, Toast.LENGTH_LONG));
    }

    public static void show(Context context, String text) {
        show(Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG));
    }

    private static void show(Toast toast) {
        setGravity(toast);
        toast.show();
    }

    private static void setGravity(Toast toast) {
        // toast.setGravity is no-op when called on text toasts, see library documentation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R/*API level 30*/) return;

        toast.setGravity(Gravity.CENTER, 0, 0);
    }
}
