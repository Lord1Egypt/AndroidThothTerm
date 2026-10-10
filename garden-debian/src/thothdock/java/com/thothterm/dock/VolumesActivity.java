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

import com.thothterm.debian.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/** Named volumes: name, driver and the Compose project that owns them. Read only. */
public class VolumesActivity extends SimpleListActivity {
    @Override
    int titleRes() {
        return R.string.volumes_title;
    }

    @Override
    int emptyRes() {
        return R.string.volumes_empty;
    }

    @Override
    Result load(ApiClient api) throws IOException {
        JSONArray volumes = api.getObject("/volumes").optJSONArray("Volumes");
        Result r = new Result();
        for (int i = 0; volumes != null && i < volumes.length(); i++) {
            JSONObject o = volumes.optJSONObject(i);
            if (o == null) continue;
            JSONObject labels = o.optJSONObject("Labels");
            String project = labels == null ? "" : labels.optString("com.docker.compose.project", "");
            r.items.add(new Item(o.optString("Name"), o.optString("Driver", "local"),
                    project.isEmpty() ? "" : "stack " + project, "created " + o.optString("CreatedAt", "")));
        }
        r.summary = String.format(Locale.US, "%d volumes", r.items.size());
        return r;
    }
}
