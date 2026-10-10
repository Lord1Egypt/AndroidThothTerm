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

/** Images in the local store: tags, size and age. Read only. */
public class ImagesActivity extends SimpleListActivity {
    @Override
    int titleRes() {
        return R.string.images_title;
    }

    @Override
    int emptyRes() {
        return R.string.images_empty;
    }

    @Override
    Result load(ApiClient api) throws IOException {
        JSONArray images = api.getArray("/images/json");
        Result r = new Result();
        long total = 0;
        for (int i = 0; i < images.length(); i++) {
            JSONObject o = images.optJSONObject(i);
            if (o == null) continue;
            JSONArray tags = o.optJSONArray("RepoTags");
            String id = o.optString("Id", "").replace("sha256:", "");
            long size = o.optLong("Size");
            total += size;
            StringBuilder name = new StringBuilder();
            for (int t = 0; tags != null && t < tags.length(); t++) {
                if (t > 0) name.append(", ");
                name.append(tags.optString(t));
            }
            r.items.add(new Item(name.length() == 0 ? "<untagged>" : name.toString(), megabytes(size),
                    id.length() > 12 ? id.substring(0, 12) : id, ago(o.optLong("Created"))));
        }
        r.summary = String.format(Locale.US, "%d images · %s on disk", r.items.size(), megabytes(total));
        return r;
    }

    static String megabytes(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.US, "%.1f GB", bytes / (double) (1L << 30));
        return String.format(Locale.US, "%.1f MB", bytes / (double) (1L << 20));
    }

    static String ago(long unixSeconds) {
        long s = Math.max(0, System.currentTimeMillis() / 1000 - unixSeconds);
        if (s < 3600) return "created " + Math.max(1, s / 60) + " min ago";
        if (s < 86400) return "created " + s / 3600 + " h ago";
        return "created " + s / 86400 + " days ago";
    }
}
