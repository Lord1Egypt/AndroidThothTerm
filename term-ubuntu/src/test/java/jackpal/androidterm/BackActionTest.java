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

import static org.junit.Assert.assertEquals;

import jackpal.androidterm.BackAction.Result;
import jackpal.androidterm.util.TermSettings;

import org.junit.Test;

/** The terminal's back semantics, as one decision (see {@link BackAction}). */
public class BackActionTest {
    private static final int HIDES = TermSettings.ACTION_BAR_MODE_HIDES;
    private static final int[] SETTINGS = {
            TermSettings.BACK_KEY_STOPS_SERVICE, TermSettings.BACK_KEY_CLOSES_WINDOW,
            TermSettings.BACK_KEY_CLOSES_ACTIVITY, TermSettings.BACK_KEY_SENDS_ESC,
            TermSettings.BACK_KEY_SENDS_TAB};

    @Test
    public void aTextSelectionEndsFirst() {
        for (int setting : SETTINGS) {
            for (int mode = 0; mode <= HIDES; mode++) {
                assertEquals(Result.FINISH_TEXT_SELECTION, BackAction.decide(true, mode, true, setting));
                assertEquals(Result.FINISH_TEXT_SELECTION, BackAction.decide(true, mode, false, setting));
            }
        }
    }

    @Test
    public void thenAnActionBarThatHidesIsHidden() {
        for (int setting : SETTINGS) {
            assertEquals(Result.HIDE_ACTION_BAR, BackAction.decide(false, HIDES, true, setting));
        }
    }

    @Test
    public void anActionBarThatDoesNotHideIsLeftAlone() {
        for (int mode = 0; mode < HIDES; mode++) {
            assertEquals(Result.CLOSE_WINDOW,
                    BackAction.decide(false, mode, true, TermSettings.BACK_KEY_CLOSES_WINDOW));
        }
        assertEquals(Result.CLOSE_WINDOW,
                BackAction.decide(false, HIDES, false, TermSettings.BACK_KEY_CLOSES_WINDOW));
    }

    @Test
    public void thenTheBackButtonSetting() {
        assertEquals(Result.STOP_SERVICE_AND_FINISH,
                BackAction.decide(false, HIDES, false, TermSettings.BACK_KEY_STOPS_SERVICE));
        assertEquals(Result.FINISH,
                BackAction.decide(false, HIDES, false, TermSettings.BACK_KEY_CLOSES_ACTIVITY));
        assertEquals(Result.CLOSE_WINDOW,
                BackAction.decide(false, HIDES, false, TermSettings.BACK_KEY_CLOSES_WINDOW));
    }

    /** ESC and TAB are sent by the terminal view itself; reaching the activity, back does nothing. */
    @Test
    public void backAsAKeyDoesNothingHere() {
        assertEquals(Result.NOTHING, BackAction.decide(false, 0, false, TermSettings.BACK_KEY_SENDS_ESC));
        assertEquals(Result.NOTHING, BackAction.decide(false, 0, false, TermSettings.BACK_KEY_SENDS_TAB));
        assertEquals(Result.NOTHING, BackAction.decide(false, 0, false, 99));
    }
}
