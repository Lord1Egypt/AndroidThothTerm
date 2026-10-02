/*
 * Copyright (C) 2026 ThothTerm.
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

package jackpal.androidterm;

import jackpal.androidterm.util.TermSettings;

/**
 * What a back action does in the terminal, in order: end a text selection,
 * hide an action bar that is set to hide, then the user's "Back button"
 * setting. One decision for every way back arrives -- the back key before
 * Android 13, a predictive-back gesture or key from Android 13 on (through
 * the activity's OnBackPressedDispatcher), and the key listener that sees
 * the back key before the terminal view does -- so they cannot drift.
 * Pure: no Android types, unit-tested on the JVM.
 */
public final class BackAction {
    public enum Result {
        FINISH_TEXT_SELECTION,
        HIDE_ACTION_BAR,
        /** BACK_KEY_STOPS_SERVICE: finish, and stop the service with it. */
        STOP_SERVICE_AND_FINISH,
        /** BACK_KEY_CLOSES_ACTIVITY. */
        FINISH,
        /** BACK_KEY_CLOSES_WINDOW. */
        CLOSE_WINDOW,
        /**
         * Back sends ESC or TAB, which the terminal view does itself when it
         * receives the key; reaching the activity, back does nothing, as
         * before.
         */
        NOTHING
    }

    private BackAction() {
    }

    public static Result decide(boolean selectingText, int actionBarMode, boolean actionBarShowing,
                                int backKeyAction) {
        if (selectingText) return Result.FINISH_TEXT_SELECTION;
        if (actionBarMode == TermSettings.ACTION_BAR_MODE_HIDES && actionBarShowing) {
            return Result.HIDE_ACTION_BAR;
        }
        switch (backKeyAction) {
            case TermSettings.BACK_KEY_STOPS_SERVICE:
                return Result.STOP_SERVICE_AND_FINISH;
            case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                return Result.FINISH;
            case TermSettings.BACK_KEY_CLOSES_WINDOW:
                return Result.CLOSE_WINDOW;
            default:
                return Result.NOTHING;
        }
    }
}
