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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;

/**
 * Android-safe log export through the Storage Access Framework. No internal
 * file path or content provider is exposed.
 */
public final class LogExporter {
    public interface Callback {
        void onFinished(boolean success);
    }

    private LogExporter() {
    }

    public static void exportAsync(Context context, Uri uri, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean success = false;
            OutputStream out = null;
            try {
                out = appContext.getContentResolver().openOutputStream(uri);
                if (out != null) {
                    ThothLog.exportTo(out);
                    out.flush();
                    success = true;
                }
            } catch (Throwable ignored) {
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            final boolean result = success;
            main.post(() -> callback.onFinished(result));
        }, "ThothLog-export").start();
    }
}
