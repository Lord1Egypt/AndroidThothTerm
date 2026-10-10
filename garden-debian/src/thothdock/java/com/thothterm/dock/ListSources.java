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
import java.util.Locale;

/**
 * The read-only Images and Volumes tabs: rows built from the JSON the
 * Containers screen already fetched for its summary tiles, so showing a tab
 * costs no extra request.
 */
final class ListSources {
    private ListSources() {
    }

    /** One row. */
    static final class Item {
        final String title;
        final String badge;
        final String sub;
        final String meta;

        Item(String title, String badge, String sub, String meta) {
            this.title = title;
            this.badge = badge;
            this.sub = sub;
            this.meta = meta;
        }
    }

    /** The rows of one tab and its summary line. */
    static final class Result {
        final List<Item> items = new ArrayList<>();
        String summary = "";
    }

    /** Images in the local store: tags, size and age. */
    static Result images(JSONArray images) {
        Result r = new Result();
        long total = 0;
        for (int i = 0; images != null && i < images.length(); i++) {
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

    /** Named volumes: name, driver and the Compose project that owns them. */
    static Result volumes(JSONArray volumes) {
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
