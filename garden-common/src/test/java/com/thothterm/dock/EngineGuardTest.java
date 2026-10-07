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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Engine Guard: the enforcement files are read out of the bundled package, so
 * what the guest enforces is exactly what ThothDock built; installation state
 * is judged against both bundled versions.
 */
public class EngineGuardTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ---- a small ar + ustar writer, to build packages the way dpkg-deb -Znone does ----

    private static void pad(ByteArrayOutputStream out, int multiple) {
        while (out.size() % multiple != 0) out.write(0);
    }

    private static byte[] tar(String[] names, byte[][] bodies, char[] types) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < names.length; i++) {
            byte[] h = new byte[512];
            byte[] n = names[i].getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(n, 0, h, 0, n.length);
            byte[] size = String.format("%011o", bodies[i].length).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(size, 0, h, 124, size.length);
            h[156] = (byte) types[i];
            out.write(h);
            out.write(bodies[i]);
            pad(out, 512);
        }
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static byte[] ar(String dataName, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        String[] names = {"debian-binary", dataName};
        byte[][] bodies = {"2.0\n".getBytes(StandardCharsets.US_ASCII), data};
        for (int i = 0; i < 2; i++) {
            out.write(String.format("%-16s%-12s%-6s%-6s%-8s%-10d`\n", names[i], "0", "0", "0", "100644",
                    bodies[i].length).getBytes(StandardCharsets.US_ASCII));
            out.write(bodies[i]);
            if (bodies[i].length % 2 != 0) out.write('\n');
        }
        return out.toByteArray();
    }

    @Test
    public void readsTheNamedFilesOutOfADeb() throws Exception {
        byte[] hook = "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8);
        byte[] odd = "x".getBytes(StandardCharsets.UTF_8);
        byte[] deb = ar("data.tar", tar(
                new String[]{"./", "./usr/lib/thothdock/engine-guard-hook", "./etc/other"},
                new byte[][]{new byte[0], hook, odd}, new char[]{'5', '0', '0'}));
        Map<String, byte[]> files = EngineGuard.DebData.files(deb, "usr/lib/thothdock/engine-guard-hook");
        assertArrayEquals(hook, files.get("usr/lib/thothdock/engine-guard-hook"));
        assertEquals(1, files.size());
    }

    @Test
    public void refusesWhatItCannotReadHonestly() throws Exception {
        byte[] plain = tar(new String[]{"./a"}, new byte[][]{"a".getBytes(StandardCharsets.UTF_8)}, new char[]{'0'});
        try {
            EngineGuard.DebData.files(ar("data.tar.xz", plain), "a");
            fail("a compressed data member must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("compressed"));
        }
        try {
            EngineGuard.DebData.files(ar("data.tar", plain), "missing");
            fail("a missing file must be an error, not an empty guard");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("missing"));
        }
        try {
            EngineGuard.DebData.files("not a deb".getBytes(StandardCharsets.UTF_8), "a");
            fail();
        } catch (IOException expected) {
            // refused
        }
        // A directory entry of the same name is not a file.
        byte[] dir = tar(new String[]{"./a"}, new byte[][]{new byte[0]}, new char[]{'5'});
        try {
            EngineGuard.DebData.files(ar("data.tar", dir), "a");
            fail("a directory is not the hook");
        } catch (IOException expected) {
            // refused
        }
    }

    /** The real guard package, built by ThothDock's own build script, yields the real hook. */
    @Test
    public void readsTheRealGuardPackage() throws Exception {
        File root = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File script = new File(root, "../third_party/thothdock/engine-guard/build.sh");
        if (!script.isFile()) script = new File(root, "third_party/thothdock/engine-guard/build.sh");
        assumeTrue("ThothDock submodule not checked out", script.isFile());
        assumeTrue("dpkg-deb not available", new File("/usr/bin/dpkg-deb").canExecute());
        File out = tmp.newFolder("guard");
        Process p = new ProcessBuilder("sh", script.getPath(), out.getPath()).redirectErrorStream(true).start();
        java.io.InputStream in = p.getInputStream();
        while (in.read() >= 0) { /* drain */ }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue());
        File[] debs = out.listFiles((d, n) -> n.startsWith("thothdock-engine-guard_") && n.endsWith(".deb"));
        assertEquals(1, debs.length);
        Map<String, byte[]> files = EngineGuard.DebData.files(Files.readAllBytes(debs[0].toPath()),
                EngineGuard.HOOK_PATH, EngineGuard.APT_CONF_PATH, EngineGuard.PIN_PATH);
        String hook = new String(files.get(EngineGuard.HOOK_PATH), StandardCharsets.UTF_8);
        // apt protocol 3: name $1, new version $6, action $9.
        assertTrue(hook.contains("new=$6 action=$9"));
        assertTrue(hook.contains("VERSION 3"));
        for (String p2 : EngineGuard.PROTECTED) assertTrue(p2, hook.contains(p2));
        String conf = new String(files.get(EngineGuard.APT_CONF_PATH), StandardCharsets.UTF_8);
        assertTrue(conf.contains("/usr/lib/thothdock/engine-guard-hook"));
        String pin = new String(files.get(EngineGuard.PIN_PATH), StandardCharsets.UTF_8);
        assertTrue(pin.contains("Pin: version 9999:*"));
        // The apt configuration must name the hook the enforcement writes.
        assertTrue(conf.contains("\"/" + EngineGuard.HOOK_PATH + "\""));
    }

    // ---- what is registered with dpkg ----------------------------------------------------

    private File status(String placeholder, String guard, String... omit) throws IOException {
        StringBuilder s = new StringBuilder();
        outer:
        for (String name : EngineGuard.PROTECTED) {
            for (String o : omit) if (o.equals(name)) continue outer;
            s.append("Package: ").append(name).append("\nStatus: install ok installed\nVersion: ")
                    .append(placeholder).append("\n\n");
        }
        s.append("Package: thothdock-engine-guard\nStatus: install ok installed\nVersion: ").append(guard).append("\n\n");
        File f = tmp.newFile();
        Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void guardInstalledNeedsEveryPlaceholderAndTheGuardPackageVersion() throws Exception {
        String v = "9999:1.0+thothdock.1", g = "1.0+thothdock.2";
        assertTrue(EngineGuard.guardInstalled(status(v, g), v, g));
        // An older guard package (its hook still has the old parser) is not enough.
        assertFalse(EngineGuard.guardInstalled(status(v, "1.0+thothdock.1"), v, g));
        assertFalse(EngineGuard.guardInstalled(status(v, g, "runc"), v, g));
        assertFalse(EngineGuard.guardInstalled(status("26.1.5-1", g), v, g));
        assertFalse(EngineGuard.guardInstalled(new File(tmp.getRoot(), "absent"), v, g));
    }
}
