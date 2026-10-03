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


package com.thothterm.blackarch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs the rootfs builder's shell helpers, and the tools they need, on the build host. */
final class RootfsTools {
    private RootfsTools() {}

    static final class Result {
        final int exit;
        final String output;

        Result(int exit, String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    static boolean available(String... commands) {
        for (String c : commands) {
            try {
                if (run(null, null, "sh", "-c", "command -v " + c).exit != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    static Result run(File dir, Map<String, String> env, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (dir != null) pb.directory(dir);
        if (env != null) pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        return new Result(p.waitFor(), new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    static Result ok(File dir, Map<String, String> env, String... command) throws Exception {
        Result r = run(dir, env, command);
        if (r.exit != 0) throw new IOException(String.join(" ", command) + " failed: " + r.output);
        return r;
    }

    static String sha256(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : d.digest(Files.readAllBytes(f.toPath()))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    static void write(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        f.delete();
    }

    static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String l : text.split("\n")) if (!l.isEmpty()) out.add(l);
        return out;
    }
}
