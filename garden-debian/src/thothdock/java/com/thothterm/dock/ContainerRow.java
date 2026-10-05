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

/** One line of GET /containers/json, as the Containers screen shows it. */
final class ContainerRow {
    /** The engine's own rule for container names; anything else is never put in a shell command. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]+$");

    final String id;
    final String name;
    final String image;
    /** Docker's state word: created, running, exited, dead ... */
    final String state;
    /** Docker's human status: "Up 3 minutes", "Exited (0) 2 hours ago". */
    final String status;
    final List<String> ports;

    ContainerRow(String id, String name, String image, String state, String status, List<String> ports) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.state = state;
        this.status = status;
        this.ports = ports;
    }

    boolean isRunning() {
        return "running".equals(state);
    }

    /** True when the name may be used in a typed `docker exec` command. */
    boolean hasSafeName() {
        return SAFE_NAME.matcher(name).matches();
    }

    static List<ContainerRow> parse(JSONArray array) {
        List<ContainerRow> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("Id", "");
            if (id.isEmpty()) continue;
            JSONArray names = o.optJSONArray("Names");
            String name = names != null && names.length() > 0 ? names.optString(0, id) : id;
            if (name.startsWith("/")) name = name.substring(1);
            List<String> ports = new ArrayList<>();
            JSONArray pa = o.optJSONArray("Ports");
            for (int j = 0; pa != null && j < pa.length(); j++) {
                JSONObject p = pa.optJSONObject(j);
                if (p == null) continue;
                String type = p.optString("Type", "tcp");
                int priv = p.optInt("PrivatePort");
                int pub = p.optInt("PublicPort");
                ports.add(pub > 0
                        ? p.optString("IP", "127.0.0.1") + ":" + pub + "→" + priv + "/" + type
                        : priv + "/" + type);
            }
            rows.add(new ContainerRow(id, name, o.optString("Image", ""), o.optString("State", ""),
                    o.optString("Status", ""), ports));
        }
        return rows;
    }
}
