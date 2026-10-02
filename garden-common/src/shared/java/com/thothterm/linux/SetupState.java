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

/**
 * The explicit setup state of an edition's Linux environment, for the UI and
 * the log. The terminal depends only on the core: once the environment is
 * {@link RootfsLifecycle.Condition#INSTALLED_HEALTHY INSTALLED_HEALTHY} and no
 * core setup is running it is {@link #TERMINAL_READY} or later, whatever the
 * optional provisioning ({@link OptionalSetup}) is doing or did.
 */
public enum SetupState {
    NOT_INSTALLED,
    /** Download, extraction, verification, managed configuration, promotion, state record. */
    CORE_SETUP_RUNNING,
    /** The terminal can open; optional provisioning has not been queued yet. */
    TERMINAL_READY,
    /** The terminal can open; optional provisioning is queued or running. */
    OPTIONAL_SETUP_PENDING,
    /** The terminal can open; optional provisioning failed and can be retried. */
    OPTIONAL_SETUP_FAILED,
    /** The terminal can open and optional provisioning is done. */
    HEALTHY,
    /**
     * Anything else installed that is not healthy: app runtime files to
     * re-stage, a state record to finish, a damaged system, a reset waiting
     * for the user. Never resolved by optional provisioning.
     */
    REPAIR_REQUIRED;

    public static SetupState of(boolean coreRunning, RootfsLifecycle.Condition condition,
                                OptionalSetup.Status optional) {
        if (coreRunning) return CORE_SETUP_RUNNING;
        switch (condition) {
            case NOT_INSTALLED:
                return NOT_INSTALLED;
            case INSTALLED_HEALTHY:
                switch (optional) {
                    case PENDING:
                    case RUNNING:
                        return OPTIONAL_SETUP_PENDING;
                    case FAILED:
                        return OPTIONAL_SETUP_FAILED;
                    case DONE:
                        return HEALTHY;
                    case NOT_STARTED:
                    default:
                        return TERMINAL_READY;
                }
            default:
                return REPAIR_REQUIRED;
        }
    }

    /** True when a terminal may open: the core is complete. */
    public boolean terminalUsable() {
        return this == TERMINAL_READY || this == OPTIONAL_SETUP_PENDING
                || this == OPTIONAL_SETUP_FAILED || this == HEALTHY;
    }
}
