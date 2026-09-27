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

package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Locks the user-local PATH contract of the managed
 * {@code /etc/profile.d/thothterm-ubuntu.sh} block.
 *
 * <p>ThothTerm runs bash through {@code su -m}, a non-login shell, so neither
 * {@code /etc/profile} nor {@code ~/.profile} executes and PAM's pam_env resets
 * PATH from {@code /etc/environment}. The managed block is therefore the only
 * place a user-installed CLI can become reachable, and it must add directories
 * generically rather than naming any single tool.
 */
public class UserPathIntegrationTest {
    /** Final PATH order the managed block promises, most significant first. */
    private static final String[] EXPECTED_ORDER = {
            ".local/bin",
            "bin",
            ".cargo/bin",
            ".foundry/bin",
            "go/bin",
            ".bun/bin",
            ".deno/bin",
            ".npm-global/bin",
    };

    private static final String BASE_PATH =
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private static File script() {
        File direct = new File("src/main/assets/linux/thothterm-ubuntu.sh");
        if (direct.isFile()) return direct;
        return new File("term-ubuntu/src/main/assets/linux/thothterm-ubuntu.sh");
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(script().toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The asset pins {@code HOME=/home/thoth} for the guest. To exercise the
     * block against a throwaway home, re-point exactly that line at {@code home}.
     */
    private File scriptWithHome(File home) throws Exception {
        String raw = source();
        assertTrue("asset must pin the guest HOME",
                raw.contains("export HOME=/home/thoth"));
        String patched = raw.replace("export HOME=/home/thoth",
                "export HOME=" + home.getAbsolutePath());
        File copy = new File(temp.newFolder(), "thothterm-ubuntu.sh");
        Files.write(copy.toPath(), patched.getBytes(StandardCharsets.UTF_8));
        return copy;
    }

    /** Sources the managed block and reports the resulting PATH. */
    private String pathAfterSourcing(File home, String incomingPath) throws Exception {
        assumeTrue("bash is required to exercise the managed block",
                new File("/bin/bash").canExecute());
        File copy = scriptWithHome(home);

        ProcessBuilder builder = new ProcessBuilder(
                "bash", "-c", ". \"$1\" >/dev/null 2>&1; printf '%s' \"$PATH\"",
                "bash", copy.getAbsolutePath());
        builder.environment().put("PATH", incomingPath);
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process process = builder.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        assertEquals("sourcing the managed block must succeed", 0, process.waitFor());
        return out.toString();
    }

    private static List<String> entries(String path) {
        return Arrays.asList(path.split(":", -1));
    }

    private File home(String... relativeBinDirs) throws Exception {
        File home = temp.newFolder("home");
        for (String relative : relativeBinDirs) {
            assertTrue("failed to stage " + relative,
                    new File(home, relative).mkdirs());
        }
        return home;
    }

    @Test
    public void assetHasNoTabsOrCarriageReturns() throws Exception {
        String raw = source();
        assertFalse("managed block must not use tabs", raw.contains("\t"));
        assertFalse("managed block must not use CR", raw.contains("\r"));
    }

    @Test
    public void namesNoSingleToolAsASpecialCase() throws Exception {
        // The fix must stay generic: every directory is $HOME-relative and
        // existence-gated, so no tool may be wired in by absolute path.
        String raw = source();
        assertFalse("must not hardcode an absolute per-tool path",
                raw.contains("/home/thoth/.foundry"));
    }

    @Test
    public void addsOnlyDirectoriesThatExist() throws Exception {
        File home = home(".local/bin", ".cargo/bin");
        String path = pathAfterSourcing(home, BASE_PATH);

        assertTrue("existing .local/bin must be added",
                entries(path).contains(new File(home, ".local/bin").getAbsolutePath()));
        assertTrue("existing .cargo/bin must be added",
                entries(path).contains(new File(home, ".cargo/bin").getAbsolutePath()));
        for (String absent : new String[]{".foundry/bin", "go/bin", "bin", ".bun/bin"}) {
            assertFalse("absent " + absent + " must not be injected",
                    entries(path).contains(new File(home, absent).getAbsolutePath()));
        }
    }

    @Test
    public void findsAUserInstalledCliGenerically() throws Exception {
        // Foundry is only an instance of the general case; a dummy CLI in an
        // arbitrary well-known user bin directory must behave identically.
        File home = home(".foundry/bin", ".local/bin");
        File cast = new File(home, ".foundry/bin/cast");
        File dummy = new File(home, ".local/bin/thoth-dummy-cli");
        assertTrue(cast.createNewFile() && cast.setExecutable(true));
        assertTrue(dummy.createNewFile() && dummy.setExecutable(true));

        File copy = scriptWithHome(home);
        ProcessBuilder builder = new ProcessBuilder(
                "bash", "-c",
                ". \"$1\" >/dev/null 2>&1; command -v cast; command -v thoth-dummy-cli",
                "bash", copy.getAbsolutePath());
        builder.environment().put("PATH", BASE_PATH);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        assertEquals("both CLIs must resolve", 0, process.waitFor());
        assertTrue("cast must be found: " + out,
                out.toString().contains(cast.getAbsolutePath()));
        assertTrue("dummy CLI must be found: " + out,
                out.toString().contains(dummy.getAbsolutePath()));
    }

    @Test
    public void prependsInTheDocumentedOrder() throws Exception {
        File home = home(EXPECTED_ORDER);
        List<String> path = entries(pathAfterSourcing(home, BASE_PATH));

        int previous = -1;
        for (String relative : EXPECTED_ORDER) {
            int at = path.indexOf(new File(home, relative).getAbsolutePath());
            assertTrue(relative + " must be on PATH", at >= 0);
            assertTrue(relative + " is out of order", at > previous);
            previous = at;
        }
        assertTrue("user directories must precede the system ones",
                previous < path.indexOf("/usr/bin"));
    }

    @Test
    public void appendsRatherThanReplacingTheIncomingPath() throws Exception {
        File home = home(".local/bin");
        String incoming = "/opt/user-tool/bin:" + BASE_PATH;
        List<String> path = entries(pathAfterSourcing(home, incoming));

        for (String required : entries(incoming)) {
            assertTrue("incoming entry " + required + " must survive",
                    path.contains(required));
        }
    }

    @Test
    public void isIdempotentAcrossRepeatedSourcing() throws Exception {
        File home = home(".local/bin", ".cargo/bin");
        String once = pathAfterSourcing(home, BASE_PATH);
        String twice = pathAfterSourcing(home, once);
        assertEquals("re-sourcing must not duplicate entries", once, twice);
    }

    @Test
    public void leavesNoHelperSymbolsBehind() throws Exception {
        File home = home(".local/bin");
        File copy = scriptWithHome(home);

        ProcessBuilder builder = new ProcessBuilder(
                "bash", "-c",
                ". \"$1\" >/dev/null 2>&1; "
                        + "echo \"fn=$(type -t thothterm_prepend_path)\"; "
                        + "echo \"var=${thothterm_dir-unset}\"",
                "bash", copy.getAbsolutePath());
        builder.environment().put("PATH", BASE_PATH);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        assertEquals(0, process.waitFor());
        assertTrue("helper function must not leak: " + out,
                out.toString().contains("fn="));
        assertFalse("helper function must be unset: " + out,
                out.toString().contains("fn=function"));
        assertTrue("loop variable must be unset: " + out,
                out.toString().contains("var=unset"));
    }
}
