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

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The regular terminal keeps its documented legacy integrations, and only
 * those (docs/security/THORNS.md). Adding an exported component, or dropping
 * the permission from RunScript, fails here first;
 * tests/security/exported_components.py pins the built APK the same way.
 */
public class IntegrationSurfaceTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final File MAIN = new File("src/main");
    private static final String[] KINDS = {"activity", "activity-alias", "service", "receiver", "provider"};

    @Test
    public void exportedComponentsAreTheDocumentedOnes() throws Exception {
        TreeSet<String> expected = new TreeSet<>(Arrays.asList(
                "activity .TermActivity",
                "activity jackpal.androidterm.RemoteInterface",
                "activity-alias .TermHere",
                "activity jackpal.androidterm.RunScript permission=${applicationId}.permission.RUN_SCRIPT",
                "activity jackpal.androidterm.RunShortcut",
                "service jackpal.androidterm.TermService",
                "activity .shortcuts.AddShortcut",
                "activity .shortcuts.FileSelection"));
        assertEquals(expected, exported(manifest()));
    }

    @Test
    public void runScriptPermissionNeedsTheUser() throws Exception {
        NodeList permissions = manifest().getElementsByTagName("permission");
        assertEquals(1, permissions.getLength());
        Element p = (Element) permissions.item(0);
        assertEquals("${applicationId}.permission.RUN_SCRIPT", p.getAttributeNS(ANDROID, "name"));
        assertEquals("dangerous", p.getAttributeNS(ANDROID, "protectionLevel"));
    }

    @Test
    public void addOnsAreTrustedBySignatureNotByName() throws IOException {
        String trusted = read("java/com/thothterm/remote/TrustedApplications.java");
        assertTrue(trusted.contains("checkSignatures(context.getPackageName(), component.getPackageName())"));
        assertTrue(trusted.contains("!= PackageManager.SIGNATURE_MATCH"));
        String collector = read("java/com/thothterm/remote/CommandCollector.java");
        assertTrue(collector.contains("if (!isCommandName(cmd)) continue;"));
    }

    @Test
    public void commandSocketRefusesOtherAppsWithoutStopping() throws IOException {
        String server = read("java/com/thothterm/services/UnixSocketServer.java");
        int check = server.indexOf("uid != android.os.Process.myUid()");
        assertTrue(check > 0);
        String refusal = server.substring(check, server.indexOf("Random random", check));
        assertTrue(refusal, refusal.contains("continue;"));
        assertFalse(refusal, refusal.contains("return"));
    }

    private static TreeSet<String> exported(Document doc) {
        TreeSet<String> out = new TreeSet<>();
        for (String kind : KINDS) {
            NodeList nodes = doc.getElementsByTagName(kind);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String flag = e.getAttributeNS(ANDROID, "exported");
                boolean filters = e.getElementsByTagName("intent-filter").getLength() > 0;
                boolean isExported = flag.isEmpty() ? filters && !kind.equals("provider") : flag.equals("true");
                if (!isExported) continue;
                String permission = e.getAttributeNS(ANDROID, "permission");
                out.add(kind + " " + e.getAttributeNS(ANDROID, "name")
                        + (permission.isEmpty() ? "" : " permission=" + permission));
            }
        }
        return out;
    }

    private static Document manifest() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new File(MAIN, "AndroidManifest.xml"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(MAIN, path).toPath()), StandardCharsets.UTF_8);
    }
}
