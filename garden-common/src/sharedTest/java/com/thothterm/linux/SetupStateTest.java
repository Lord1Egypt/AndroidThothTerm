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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.thothterm.linux.RootfsLifecycle.Condition;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The terminal depends only on the core setup: whatever optional
 * provisioning does or did, a healthy installation can open a terminal, and
 * nothing optional can make it unhealthy.
 */
public class SetupStateTest {
    @Test
    public void aHealthyInstallationIsUsableWhateverOptionalSetupDoes() {
        assertEquals(SetupState.TERMINAL_READY,
                SetupState.of(false, Condition.INSTALLED_HEALTHY, OptionalSetup.Status.NOT_STARTED));
        assertEquals(SetupState.OPTIONAL_SETUP_PENDING,
                SetupState.of(false, Condition.INSTALLED_HEALTHY, OptionalSetup.Status.PENDING));
        assertEquals(SetupState.OPTIONAL_SETUP_PENDING,
                SetupState.of(false, Condition.INSTALLED_HEALTHY, OptionalSetup.Status.RUNNING));
        assertEquals(SetupState.OPTIONAL_SETUP_FAILED,
                SetupState.of(false, Condition.INSTALLED_HEALTHY, OptionalSetup.Status.FAILED));
        assertEquals(SetupState.HEALTHY,
                SetupState.of(false, Condition.INSTALLED_HEALTHY, OptionalSetup.Status.DONE));
        for (OptionalSetup.Status optional : OptionalSetup.Status.values()) {
            assertTrue(optional.name(),
                    SetupState.of(false, Condition.INSTALLED_HEALTHY, optional).terminalUsable());
        }
    }

    @Test
    public void onlyAHealthyInstallationOpensATerminal() {
        for (Condition condition : Condition.values()) {
            for (OptionalSetup.Status optional : OptionalSetup.Status.values()) {
                SetupState state = SetupState.of(false, condition, optional);
                assertEquals(condition + "/" + optional,
                        condition == Condition.INSTALLED_HEALTHY, state.terminalUsable());
                // Optional provisioning never changes what the core decided.
                if (condition == Condition.NOT_INSTALLED) assertEquals(SetupState.NOT_INSTALLED, state);
                else if (condition != Condition.INSTALLED_HEALTHY) {
                    assertEquals(SetupState.REPAIR_REQUIRED, state);
                }
                assertEquals(SetupState.CORE_SETUP_RUNNING, SetupState.of(true, condition, optional));
                assertFalse(SetupState.of(true, condition, optional).terminalUsable());
            }
        }
    }

    @Test
    public void optionalSetupFailsRetriesAndSucceeds() {
        OptionalSetup setup = new OptionalSetup();
        List<OptionalSetup.Status> seen = Collections.synchronizedList(new ArrayList<>());
        setup.addListener(seen::add);
        assertEquals(OptionalSetup.Status.NOT_STARTED, setup.status());
        assertTrue(setup.queue());
        assertFalse("coalesced while queued", setup.queue());
        setup.started();
        assertFalse("coalesced while running", setup.queue());
        setup.finished(false, "sudo=ABSENT");
        assertEquals(OptionalSetup.Status.FAILED, setup.status());
        assertEquals("sudo=ABSENT", setup.failure());
        assertTrue("a failure can be retried", setup.queue());
        setup.started();
        setup.finished(true, null);
        assertEquals(OptionalSetup.Status.DONE, setup.status());
        assertNull(setup.failure());
        assertEquals(java.util.Arrays.asList(OptionalSetup.Status.PENDING, OptionalSetup.Status.RUNNING,
                OptionalSetup.Status.FAILED, OptionalSetup.Status.PENDING, OptionalSetup.Status.RUNNING,
                OptionalSetup.Status.DONE), seen);
    }

    @Test
    public void outOfOrderTransitionsAreRefused() {
        OptionalSetup setup = new OptionalSetup();
        try {
            setup.started();
            fail("started without being queued");
        } catch (IllegalStateException expected) {
            // refused
        }
        setup.queue();
        try {
            setup.finished(true, null);
            fail("finished without running");
        } catch (IllegalStateException expected) {
            // refused
        }
    }

    @Test
    public void concurrentQueuesStartExactlyOneRun() throws Exception {
        OptionalSetup setup = new OptionalSetup();
        int threads = 16;
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger queued = new AtomicInteger();
        for (int i = 0; i < threads; ++i) {
            new Thread(() -> {
                try {
                    go.await();
                    if (setup.queue()) queued.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        go.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, queued.get());
        assertEquals(OptionalSetup.Status.PENDING, setup.status());
    }
}
