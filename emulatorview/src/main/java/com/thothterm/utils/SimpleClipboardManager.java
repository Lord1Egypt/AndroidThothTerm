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
import android.util.Log;


public class SimpleClipboardManager {
    private static final String TAG = "SimpleClipboardManager";

    private final ClipboardManager clip;

    public SimpleClipboardManager(Context context) {
        clip = (ClipboardManager) context.getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * The clipboard's plain-text form, or "" when it has none.
     * <p>
     * Styled clips carry their plain form alongside the markup -- ClipData's
     * own text coercion returns exactly this and only falls back to reading a
     * content URI or serialising an Intent, neither of which belongs in a
     * terminal, so those are refused here instead.
     */
    public CharSequence getText() {
        try {
            ClipData data = clip.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return "";
            ClipData.Item item = data.getItemAt(0);
            CharSequence text = item.getText();
            if (text != null) return text;
            // Nothing textual of its own: this is a URI or Intent clip, and
            // Paste is for text the user can see, not for what a URI points at.
            return "";
        } catch (RuntimeException e) {
            // Never log the clip itself, but do not hide that a read failed.
            Log.w(TAG, "clipboard read failed: " + e.getClass().getName());
        }
        return "";
    }

    public void setText(CharSequence text) {
        ClipData clipData = ClipData.newPlainText("simple text", text);
        clip.setPrimaryClip(clipData);
    }

    /**
     * Whether the clipboard holds something the terminal can paste as text.
     * <p>
     * Any "text/*" clip qualifies, not just text/plain: apps that copy styled
     * text publish it as text/html, and restricting this to text/plain left
     * Paste disabled for most of Android -- text copied from Telegram, for
     * instance, arrives as text/html even when it carries no formatting.
     * <p>
     * Deliberately asks only for the description, never for the clip itself,
     * so that merely long-pressing the terminal does not count as reading the
     * user's clipboard.
     */
    public boolean hasText() {
        if (!clip.hasPrimaryClip()) return false;
        ClipDescription descr = clip.getPrimaryClipDescription();
        if (descr == null) return false;
        return descr.hasMimeType("text/*");
    }
}
