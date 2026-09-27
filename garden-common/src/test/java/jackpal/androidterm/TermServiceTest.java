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

package jackpal.androidterm;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TermServiceTest {
    @Test
    public void restartPolicyDoesNotRecreateKilledService() {
        assertEquals(Service.START_NOT_STICKY, TermService.restartPolicy());
    }

    @Test
    public void rejectedForegroundPromotionRequestsShutdownWithoutPropagating() {
        AtomicBoolean shutdownRequested = new AtomicBoolean();

        boolean started = TermService.StartForeground.Compat31.attempt(
                () -> {
                    throw new ForegroundServiceStartNotAllowedException("test rejection");
                },
                () -> shutdownRequested.set(true));

        assertFalse(started);
        assertTrue(shutdownRequested.get());
    }

    @Test(expected = IllegalStateException.class)
    public void unrelatedRuntimeExceptionIsNotCaught() {
        TermService.StartForeground.Compat31.attempt(
                () -> {
                    throw new IllegalStateException("unrelated");
                },
                () -> {
                    throw new AssertionError("shutdown must not be requested");
                });
    }
}
