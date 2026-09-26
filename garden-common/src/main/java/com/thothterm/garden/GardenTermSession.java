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

package com.thothterm.garden;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import jackpal.androidterm.ShellTermSession;
import jackpal.androidterm.util.TermSettings;

/**
 * A terminal session whose process is the packaged PRoot runtime entering the
 * distro rootfs. Reuses the existing PTY/session architecture.
 */
public class GardenTermSession extends ShellTermSession {

    public GardenTermSession(TermSettings settings) throws IOException {
        super(settings, prepareSession());
    }

    /** Refreshes managed guest state and the resolver; no initial command is typed. */
    private static String prepareSession() throws IOException {
        RootfsManager.get().prepareSession();
        AndroidNetworkResolver.get().refresh();
        if (!AndroidNetworkResolver.get().resolverFile().isFile()) {
            throw new IOException("The guest resolver could not be prepared");
        }
        return "";
    }

    @Override
    protected ArrayList<String> buildArgv(TermSettings settings) {
        GardenRuntime runtime = GardenRuntime.from(RootfsManager.get(), settings.getTermType());
        ThothLog.i(LogCategory.PROOT, "Runtime launch requested");
        return new ArrayList<>(runtime.buildArgv());
    }

    @Override
    protected Map<String, String> buildEnvironment(TermSettings settings) {
        return GardenRuntime.from(RootfsManager.get(), settings.getTermType()).buildEnvironment();
    }

    /** PRoot changes into the guest home itself; the host side starts in the runtime tree. */
    @Override
    protected String workingDirectory() {
        return RootfsManager.get().prootTmpDir().getParent();
    }
}
