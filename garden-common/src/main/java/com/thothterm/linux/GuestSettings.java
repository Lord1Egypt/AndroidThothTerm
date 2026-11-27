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

import android.content.SharedPreferences;

/**
 * The guest identity the user chose in Settings: the name of the Linux
 * account every window logs into and the guest's host name. Both are read
 * from the default shared preferences; a missing or invalid value falls back
 * to the built-in default, so the guest is always left with a usable name.
 */
public final class GuestSettings {
    /** Preference key: the guest account's login name. */
    public static final String KEY_USER_NAME = "guest_username";
    /** Preference key: the guest's host name. */
    public static final String KEY_HOST_NAME = "guest_hostname";

    private GuestSettings() {
    }

    /** The account name the user wants, or the default when none valid is set. */
    public static String userName(SharedPreferences prefs) {
        String name = prefs.getString(KEY_USER_NAME, null);
        return isValidUserName(name) ? name : GuestConfig.DEFAULT_USER;
    }

    /** The host name the user wants, or the default when none valid is set. */
    public static String hostName(SharedPreferences prefs) {
        String name = prefs.getString(KEY_HOST_NAME, null);
        return isValidHostName(name) ? name : GuestConfig.DEFAULT_HOSTNAME;
    }

    /** True for a name the guest account may have (lower-case login name, not root). */
    public static boolean isValidUserName(String name) {
        return GuestConfig.isValidUserName(name);
    }

    /** True for a name the guest may carry as its host name (one RFC 1123 label). */
    public static boolean isValidHostName(String name) {
        return GuestConfig.isValidHostName(name);
    }
}
