/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class AppRowTest {
    private static List<AppRow> parse(String json) throws Exception {
        return AppRow.parse(new JSONObject(json));
    }

    @Test
    public void parsesTheEnginesAppsView() throws Exception {
        List<AppRow> apps = parse("{\"Apps\":[{\"Kind\":\"flatpak\",\"ID\":\"org.gnome.Calculator\",\"Name\":\"Calculator\","
                + "\"Branch\":\"stable\",\"Arch\":\"aarch64\",\"Installation\":\"user:thoth\",\"Runtime\":\"org.gnome.Platform/aarch64/49\","
                + "\"SizeBytes\":12345,\"Running\":[{\"ID\":\"" + "a".repeat(32) + "\",\"Pid\":42,\"NetIP\":\"127.77.0.5\"},"
                + "{\"ID\":\"not-an-id\",\"Pid\":1}]}],\"Runtimes\":2}");
        assertEquals(1, apps.size());
        AppRow a = apps.get(0);
        assertEquals("Calculator", a.name);
        assertEquals(12345, a.sizeBytes);
        assertTrue(a.isRunning());
        assertEquals(1, a.sandboxes.size()); // the malformed sandbox id is dropped
        assertEquals("pid 42 · 127.77.0.5", a.runningDetail.get(0));
        assertEquals("flatpak run --user org.gnome.Calculator//stable\n", a.runCommand());
        assertEquals("flatpak uninstall --user org.gnome.Calculator//stable\n", a.removeCommand());
    }

    @Test
    public void systemInstallationsUseSystem() throws Exception {
        AppRow a = parse("{\"Apps\":[{\"ID\":\"org.example.Tool\",\"Installation\":\"system\",\"Branch\":\"\"}]}").get(0);
        assertEquals("flatpak run --system org.example.Tool\n", a.runCommand());
        assertFalse(a.isRunning());
    }

    /** Nothing that is not a Flatpak ID is ever typed into a shell. */
    @Test
    public void unsafeReferencesAreNeverTyped() throws Exception {
        for (String[] bad : new String[][]{
                {"org.example.Tool;rm -rf ~", "stable"},
                {"org.example.$(id)", "stable"},
                {"two.parts", "stable"},
                {"org.example.Tool", "stable && reboot"},
                {"org.example.Tool", "-x"},
                {"org.example.Tool\nid", "stable"},
                {"1org.example.Tool", "stable"}}) {
            JSONObject o = new JSONObject().put("ID", bad[0]).put("Branch", bad[1]).put("Installation", "user:thoth");
            AppRow a = AppRow.parse(new JSONObject().put("Apps", new org.json.JSONArray().put(o))).get(0);
            assertFalse(bad[0] + " / " + bad[1], a.hasSafeRef());
            assertNull(a.runCommand());
            assertNull(a.removeCommand());
        }
    }

    @Test
    public void anOlderEnginesEmptyViewIsNoApps() throws Exception {
        assertTrue(AppRow.parse(new JSONObject("{}")).isEmpty());
        assertTrue(AppRow.parse(null).isEmpty());
    }
}
