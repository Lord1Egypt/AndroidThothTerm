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

package com.thothterm.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import jackpal.androidterm.ShellTermSession;

/**
 * Garden Thorns for every Garden edition (docs/security/THORNS.md): only the
 * launcher is exported, editions add no component of their own, and nothing
 * outside the app can put text into a terminal.
 * tests/security/exported_components.py checks the merged manifest of the
 * built APKs as well.
 */
public class ExportedSurfaceTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final File MAIN = new File("src/main");
    private static final String[] KINDS = {"activity", "activity-alias", "service", "receiver", "provider"};

    @Test
    public void onlyTheLauncherIsExported() throws Exception {
        assertEquals(Collections.singleton("activity .linux.GardenSetupActivity"), exported(manifest()));
        Element launcher = component(manifest(), ".linux.GardenSetupActivity");
        assertTrue(childNames(launcher).contains("android.intent.category.LAUNCHER"));
        assertEquals(1, launcher.getElementsByTagName("intent-filter").getLength());
        assertEquals(1, launcher.getElementsByTagName("action").getLength());
    }

    @Test
    public void editionsAddNoComponents() throws Exception {
        File[] editions = new File("..").listFiles((dir, name) -> name.startsWith("garden-")
                && !name.equals("garden-common"));
        assertTrue(editions != null && editions.length >= 2);
        for (File edition : editions) {
            File manifest = new File(edition, "src/main/AndroidManifest.xml");
            Document doc = parse(read(manifest));
            for (String kind : KINDS) {
                assertEquals(manifest + " declares a " + kind, 0, doc.getElementsByTagName(kind).getLength());
            }
            assertEquals(0, doc.getElementsByTagName("permission").getLength());
        }
    }

    @Test
    public void noLegacyCommandEntryPoints() throws Exception {
        Document doc = manifest();
        assertEquals(0, doc.getElementsByTagName("permission").getLength());
        assertEquals(0, doc.getElementsByTagName("permission-group").getLength());
        assertEquals(0, doc.getElementsByTagName("activity-alias").getLength());
        String manifest = read(new File(MAIN, "AndroidManifest.xml")).replaceAll("(?s)<!--.*?-->", "");
        for (String token : new String[]{"RemoteInterface", "TermHere", "RunScript", "RUN_SCRIPT",
                "RunShortcut", "RUN_SHORTCUT", "AddShortcut", "FileSelection", "RemoteSession",
                "START_TERM", "action.SEND\"", "CREATE_SHORTCUT", "<package "}) {
            assertFalse("manifest names " + token, manifest.contains(token));
        }
        for (String gone : new String[]{"jackpal.androidterm.RemoteInterface",
                "jackpal.androidterm.RunScript", "jackpal.androidterm.RunShortcut",
                "jackpal.androidterm.BoundSession", "com.thothterm.RemoteActionActivity",
                "com.thothterm.RemoteSession", "com.thothterm.remote.CommandCollector",
                "com.thothterm.remote.TrustedApplications", "com.thothterm.services.CommandService",
                "com.thothterm.services.UnixSocketServer", "com.thothterm.shortcuts.AddShortcut",
                "jackpal.androidterm.util.ShortcutEncryption"}) {
            try {
                Class.forName(gone, false, getClass().getClassLoader());
                fail(gone + " is back");
            } catch (ClassNotFoundException expected) {
                // removed
            }
        }
    }

    @Test
    public void terminalServiceIsPrivate() throws Exception {
        Element service = component(manifest(), "jackpal.androidterm.TermService");
        assertEquals("false", service.getAttributeNS(ANDROID, "exported"));
        assertEquals(0, service.getElementsByTagName("intent-filter").getLength());
        assertFalse(read(new File(MAIN, "java/jackpal/androidterm/TermService.java")).contains("ITerminal"));
    }

    /** A session has no "initial command": nothing is ever typed into a new terminal. */
    @Test
    public void sessionsTakeNoInitialInput() {
        for (Constructor<?> c : ShellTermSession.class.getDeclaredConstructors()) {
            for (Class<?> p : c.getParameterTypes()) {
                assertFalse(c + " takes a " + p, p == String.class || p == CharSequence.class);
            }
        }
        for (Field f : ShellTermSession.class.getDeclaredFields()) {
            assertFalse(f.getName(), f.getName().toLowerCase().contains("command"));
        }
    }

    @Test
    public void detectsAReintroducedShareTarget() throws Exception {
        String bad = read(new File(MAIN, "AndroidManifest.xml")).replace("</application>",
                "<activity-alias android:name=\".TermHere\" android:exported=\"true\""
                        + " android:targetActivity=\".TermActivity\"><intent-filter>"
                        + "<action android:name=\"android.intent.action.SEND\"/></intent-filter>"
                        + "</activity-alias></application>");
        assertTrue(exported(parse(bad)).contains("activity-alias .TermHere"));
    }

    static TreeSet<String> exported(Document doc) {
        TreeSet<String> out = new TreeSet<>();
        for (String kind : KINDS) {
            NodeList nodes = doc.getElementsByTagName(kind);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String flag = e.getAttributeNS(ANDROID, "exported");
                boolean filters = e.getElementsByTagName("intent-filter").getLength() > 0;
                boolean isExported = flag.isEmpty() ? filters && !kind.equals("provider") : flag.equals("true");
                if (isExported) out.add(kind + " " + e.getAttributeNS(ANDROID, "name"));
            }
        }
        return out;
    }

    private static Element component(Document doc, String name) {
        for (String kind : KINDS) {
            NodeList nodes = doc.getElementsByTagName(kind);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                if (name.equals(e.getAttributeNS(ANDROID, "name"))) return e;
            }
        }
        throw new AssertionError("no component " + name);
    }

    private static List<String> childNames(Element e) {
        List<String> out = new ArrayList<>();
        NodeList all = e.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            out.add(((Element) all.item(i)).getAttributeNS(ANDROID, "name"));
        }
        return out;
    }

    private static Document manifest() throws Exception {
        return parse(read(new File(MAIN, "AndroidManifest.xml")));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
