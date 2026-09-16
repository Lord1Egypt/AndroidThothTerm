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

package com.thothterm.utils;


import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;


public class SimpleClipboardManager {
    private final ClipboardManager clip;

    public SimpleClipboardManager(Context context) {
        clip = (ClipboardManager) context.getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public CharSequence getText() {
        try {
            ClipData data = clip.getPrimaryClip();
            if (data == null) return "";
            ClipData.Item item = data.getItemAt(0);
            return item.getText();
        } catch (RuntimeException ignore) {
        }
        return "";
    }

    public void setText(CharSequence text) {
        ClipData clipData = ClipData.newPlainText("simple text", text);
        clip.setPrimaryClip(clipData);
    }

    public boolean hasText() {
        if (!clip.hasPrimaryClip()) return false;
        ClipDescription descr = clip.getPrimaryClipDescription();
        if (descr == null) return false;
        return descr.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
    }
}
