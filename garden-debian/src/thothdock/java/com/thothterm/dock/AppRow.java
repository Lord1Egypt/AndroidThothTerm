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

package com.thothterm.dock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One installed Flatpak app from GET /thothdock/apps, and the terminal
 * commands the Apps tab types for it. Running and removing stay flatpak's own
 * commands; only stopping a running sandbox goes through the engine.
 */
final class AppRow {
    /**
     * Flatpak's own rules: an application ID is 3+ dot-separated elements of
     * [A-Za-z0-9_-] (no leading digit), a branch is [A-Za-z0-9_.-]. Anything
     * else is never typed into a terminal.
     */
    private static final Pattern APP_ID =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*){2,}");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private static final Pattern SANDBOX_ID = Pattern.compile("[0-9a-f]{32}");

    final String kind;
    final String id;
    final String name;
    final String branch;
    final String arch;
    final String installation;
    final String runtime;
    final long sizeBytes;
    /** IDs of this app's running sandboxes. */
    final List<String> sandboxes = new ArrayList<>();
    /** "pid N · address" for each running sandbox. */
    final List<String> runningDetail = new ArrayList<>();

    private AppRow(JSONObject o) {
        kind = o.optString("Kind", "flatpak");
        id = o.optString("ID", "");
        name = o.optString("Name", id);
        branch = o.optString("Branch", "");
        arch = o.optString("Arch", "");
        installation = o.optString("Installation", "");
        runtime = o.optString("Runtime", "");
        sizeBytes = o.optLong("SizeBytes");
        JSONArray running = o.optJSONArray("Running");
        for (int i = 0; running != null && i < running.length(); i++) {
            JSONObject r = running.optJSONObject(i);
            if (r == null || !SANDBOX_ID.matcher(r.optString("ID")).matches()) continue;
            sandboxes.add(r.optString("ID"));
            String ip = r.optString("NetIP", "");
            runningDetail.add("pid " + r.optInt("Pid") + (ip.isEmpty() ? "" : " · " + ip));
        }
    }

    static List<AppRow> parse(JSONObject view) {
        List<AppRow> out = new ArrayList<>();
        JSONArray apps = view == null ? null : view.optJSONArray("Apps");
        for (int i = 0; apps != null && i < apps.length(); i++) {
            JSONObject o = apps.optJSONObject(i);
            if (o != null) out.add(new AppRow(o));
        }
        return out;
    }

    boolean isRunning() {
        return !sandboxes.isEmpty();
    }

    /** True when the ID and branch are safe to type into a shell. */
    boolean hasSafeRef() {
        return APP_ID.matcher(id).matches() && (branch.isEmpty() || BRANCH.matcher(branch).matches());
    }

    private String installFlag() {
        return installation.startsWith("user:") ? "--user " : "--system ";
    }

    private String ref() {
        return branch.isEmpty() ? id : id + "//" + branch;
    }

    /** What Run types, or null when the reference is not safe to type. */
    String runCommand() {
        return hasSafeRef() ? "flatpak run " + installFlag() + ref() + "\n" : null;
    }

    /** What Remove types, or null when the reference is not safe to type. */
    String removeCommand() {
        return hasSafeRef() ? "flatpak uninstall " + installFlag() + ref() + "\n" : null;
    }
}
