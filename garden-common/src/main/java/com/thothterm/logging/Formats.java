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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Locale-independent timestamp formatting. {@link SimpleDateFormat} is not
 * thread-safe, so one instance is kept per thread.
 */
final class Formats {
    private static final ThreadLocal<SimpleDateFormat> TIME =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
                }
            };

    private static final ThreadLocal<SimpleDateFormat> FULL =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                }
            };

    private static final ThreadLocal<SimpleDateFormat> FILE_STAMP =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
                }
            };

    private Formats() {
    }

    static String time(long timestamp) {
        return TIME.get().format(new Date(timestamp));
    }

    static String full(long timestamp) {
        return FULL.get().format(new Date(timestamp));
    }

    static String fileStamp(long timestamp) {
        return FILE_STAMP.get().format(new Date(timestamp));
    }
}
