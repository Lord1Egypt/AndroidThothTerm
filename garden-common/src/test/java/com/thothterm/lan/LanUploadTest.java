/*
 * Copyright (C) 2026 ThothTerm.
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

package com.thothterm.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.thothterm.upload.NioUploadFs;
import com.thothterm.upload.StagingJournal;
import com.thothterm.upload.UploadError;
import com.thothterm.upload.UploadFs;
import com.thothterm.upload.UploadNames;
import com.thothterm.upload.UploadTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Browser uploads end to end on loopback: the real server, auth, terminal
 * bookkeeping and upload engine, with in-memory PTYs whose "current
 * directory" is a temporary folder of the test's choosing.
 */
public class LanUploadTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final int FIRST_PORT = 48681;

    /** For LAN tests that do not upload. */
    static final LanUploads.Host NO_UPLOADS = new LanUploads.Host() {
        @Override
        public UploadTarget target(Pty pty) throws UploadError {
            throw new UploadError(UploadError.Code.NO_DIRECTORY, "no uploads in this test");
        }

        @Override
        public UploadFs fs() {
            return new NioUploadFs();
        }

        @Override
        public StagingJournal journal() {
            throw new AssertionError("no uploads in this test");
        }
    };

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final AtomicLong now = new AtomicLong(5_000_000);
    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());
    private final List<String> secrets = Collections.synchronizedList(new ArrayList<>());
    private final Map<Pty, File> directories = Collections.synchronizedMap(new HashMap<>());
    private final Deque<File> nextDirectories = new ArrayDeque<>();
    private final NioUploadFs fs = new NioUploadFs();
    private final List<Socket> sockets = new ArrayList<>();
    private StagingJournal journal;
    private File home;
    private LanMode mode;
    private int port;

    private final LanLog log = new LanLog() {
        @Override
        public void info(String message) {
            logLines.add(message);
        }

        @Override
        public void warn(String message) {
            logLines.add(message);
        }
    };

    @Before
    public void setUp() throws IOException {
        home = tmp.newFolder("home", "thoth");
        journal = new StagingJournal(new File(tmp.getRoot(), "app/upload-staging"));
        LanUploads.Host host = new LanUploads.Host() {
            @Override
            public UploadTarget target(Pty pty) throws UploadError {
                File dir = directories.get(pty);
                if (dir == null) throw new UploadError(UploadError.Code.NO_DIRECTORY, "unknown pty");
                return new UploadTarget(dir.getPath(), "/home/thoth/" + home.toPath().relativize(dir.toPath()),
                        022);
            }

            @Override
            public UploadFs fs() {
                return fs;
            }

            @Override
            public StagingJournal journal() {
                return journal;
            }
        };
        mode = new LanMode((columns, rows) -> {
            LanServerTest.FakePty pty = new LanServerTest.FakePty(columns, rows);
            synchronized (nextDirectories) {
                directories.put(pty, nextDirectories.isEmpty() ? home : nextDirectories.removeFirst());
            }
            return pty;
        }, host, name -> null, log, now::get, new SecureRandom());
        assertTrue(mode.start(LOOPBACK, FIRST_PORT, LanMode.PORT_ATTEMPTS));
        port = mode.status().port;
    }

    @After
    public void tearDown() throws IOException {
        for (Socket s : sockets) s.close();
        mode.stop(LanMode.Failure.NONE);
        synchronized (logLines) {
            for (String line : logLines) {
                for (String secret : secrets) {
                    assertFalse("log line leaks a secret: " + line, line.contains(secret));
                }
                assertFalse(line, line.contains("Bearer"));
                // No file names or paths in the log.
                for (String name : new String[]{"main.py", "README", "مرحبا", "upload-test", "evil",
                        "secret", ".hidden", "project", "/home/thoth"}) {
                    assertFalse("log line names a file: " + line, line.contains(name));
                }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private String host() {
        return LOOPBACK.getHostAddress() + ":" + port;
    }

    private String origin() {
        return "http://" + host();
    }

    static final class Response {
        int status;
        byte[] body = new byte[0];

        Map<String, Object> json() {
            return FlatJson.parse(new String(body, StandardCharsets.UTF_8));
        }

        String error() {
            return (String) json().get("error");
        }
    }

    /** Send a request head (and optionally part of a body); the socket stays open. */
    private Socket send(String method, String path, Map<String, String> headers, byte[] body)
            throws IOException {
        Socket s = new Socket(LOOPBACK, port);
        sockets.add(s);
        s.setSoTimeout(8000);
        StringBuilder req = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (h.getValue() != null) req.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        req.append("\r\n");
        OutputStream out = s.getOutputStream();
        out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        if (body != null) out.write(body);
        out.flush();
        return s;
    }

    private static Response response(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        Response r = new Response();
        String status = line(in);
        r.status = Integer.parseInt(status.split(" ")[1]);
        int length = 0;
        String h;
        while (!(h = line(in)).isEmpty()) {
            if (h.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
        }
        r.body = new byte[length];
        int read = 0;
        while (read < length) {
            int k = in.read(r.body, read, length - read);
            if (k < 0) throw new EOFException();
            read += k;
        }
        return r;
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != '\n') {
            if (c < 0) throw new EOFException();
            if (c != '\r') b.write(c);
        }
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private Map<String, String> headers(String token) {
        Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("Host", host());
        h.put("Origin", origin());
        if (token != null) h.put("Authorization", "Bearer " + token);
        return h;
    }

    private Response post(String path, String token, String json) throws IOException {
        Map<String, String> h = headers(token);
        return post(path, h, json);
    }

    private Response post(String path, Map<String, String> h, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        h.put("Content-Type", "application/json");
        h.put("Content-Length", Integer.toString(body.length));
        return response(send("POST", path, h, body));
    }

    private String pair() throws IOException {
        now.addAndGet(LanAuth.ATTEMPT_SPACING_MS);
        String pin = mode.status().pin.pin;
        secrets.add(pin);
        Map<String, String> h = headers(null);
        Response r = post("/api/pair", h, "{\"pin\":\"" + pin + "\"}");
        assertEquals(200, r.status);
        String token = (String) r.json().get("token");
        secrets.add(token);
        mode.newPin();
        return token;
    }

    /** Open a browser terminal whose current directory is {@code dir}; returns its id. */
    private String terminal(String token, File dir) throws IOException {
        synchronized (nextDirectories) {
            nextDirectories.add(dir);
        }
        Socket s = new Socket(LOOPBACK, port);
        sockets.add(s);
        s.setSoTimeout(8000);
        String req = "GET /ws HTTP/1.1\r\nHost: " + host() + "\r\nOrigin: " + origin()
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Protocol: "
                + LanServer.PROTOCOL + ", " + LanServer.AUTH_PREFIX + token + "\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(req.getBytes(StandardCharsets.ISO_8859_1));
        out.write(WebSocketCodecTest.clientFrame(true, WebSocketCodec.OP_TEXT,
                "{\"type\":\"open\",\"term\":null,\"cols\":80,\"rows\":24}".getBytes(StandardCharsets.UTF_8), true));
        out.flush();
        InputStream in = s.getInputStream();
        String status = line(in);
        assertTrue(status, status.contains(" 101 "));
        while (!line(in).isEmpty()) {
            // headers
        }
        int b0 = in.read();
        int n = in.read() & 0x7f;
        assertEquals(0x81, b0);
        byte[] p = new byte[n];
        int read = 0;
        while (read < n) read += in.read(p, read, n - read);
        String id = (String) FlatJson.parse(new String(p, StandardCharsets.UTF_8)).get("term");
        assertNotNull(id);
        return id;
    }

    private Response begin(String token, String term, String kind, String name, long bytes)
            throws IOException {
        return post(LanServer.UPLOAD_BEGIN, token, "{\"term\":" + FlatJson.quote(term) + ",\"kind\":\""
                + kind + "\"" + (name == null ? "" : ",\"name\":" + FlatJson.quote(name))
                + ",\"bytes\":" + bytes + "}");
    }

    private String beginOk(String token, String term, String kind, String name, long bytes,
                           String expectTarget) throws IOException {
        Response r = begin(token, term, kind, name, bytes);
        assertEquals(new String(r.body, StandardCharsets.UTF_8), 200, r.status);
        assertEquals(expectTarget, r.json().get("target"));
        return (String) r.json().get("upload");
    }

    private static String encode(String path) throws IOException {
        StringBuilder b = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (b.length() > 0) b.append('/');
            b.append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
        }
        return b.toString();
    }

    private Map<String, String> fileHeaders(String token, String upload, String encodedPath, long length) {
        Map<String, String> h = headers(token);
        h.put("Content-Type", "application/octet-stream");
        h.put(LanServer.UPLOAD_HEADER, upload);
        h.put(LanServer.PATH_HEADER, encodedPath);
        if (length >= 0) h.put("Content-Length", Long.toString(length));
        return h;
    }

    private Response put(String token, String upload, String path, byte[] data) throws IOException {
        return response(send("PUT", LanServer.UPLOAD_FILE,
                fileHeaders(token, upload, encode(path), data.length), data));
    }

    private Response finish(String token, String upload) throws IOException {
        return post(LanServer.UPLOAD_FINISH, token, "{\"upload\":" + FlatJson.quote(upload) + "}");
    }

    private File dir(String relative) throws IOException {
        File d = new File(home, relative);
        assertTrue(d.isDirectory() || d.mkdirs());
        return d;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] sha256(File file) throws Exception {
        return sha256(Files.readAllBytes(file.toPath()));
    }

    private static List<String> names(File dir) {
        String[] n = dir.list();
        Arrays.sort(n);
        return Arrays.asList(n);
    }

    private static void eventually(String what, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    private static boolean noStaging(File dir) {
        for (String n : dir.list()) if (n.startsWith(UploadNames.STAGING_PREFIX)) return false;
        return true;
    }

    // -------------------------------------------------------------- tests

    @Test
    public void eachTabUploadsIntoItsOwnTerminalsDirectory() throws Exception {
        String token = pair();
        File a = dir("upload-test/a");
        File b = dir("upload-test/b");
        String termA = terminal(token, a);
        String termB = terminal(token, b);

        byte[] mainPy = bytes("print('from A')\n");
        byte[] readme = bytes("# from B\n");
        String upA = beginOk(token, termA, "files", null, mainPy.length, "/home/thoth/upload-test/a");
        String upB = beginOk(token, termB, "files", null, readme.length, "/home/thoth/upload-test/b");
        assertEquals("main.py", put(token, upA, "main.py", mainPy).json().get("name"));
        assertEquals("README.md", put(token, upB, "README.md", readme).json().get("name"));
        assertEquals(200, finish(token, upA).status);
        assertEquals(200, finish(token, upB).status);

        assertEquals(Collections.singletonList("main.py"), names(a));
        assertEquals(Collections.singletonList("README.md"), names(b));
        assertArrayEquals(sha256(mainPy), sha256(new File(a, "main.py")));
        assertArrayEquals(sha256(readme), sha256(new File(b, "README.md")));
    }

    @Test
    public void aFolderArrivesWithItsHierarchyAndNames() throws Exception {
        String token = pair();
        File cwd = dir("upload-test");
        String term = terminal(token, cwd);
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        files.put("README.md", bytes("readme"));
        files.put("src/main.py", bytes("print(1)"));
        files.put("src/مرحبا.txt", bytes("مرحبا"));
        files.put("docs/notes.txt", bytes("notes"));
        files.put(".hidden", bytes("h"));
        files.put("مشروع جديد/résumé.txt", bytes("r"));
        files.put("日本語.txt", bytes("j"));
        files.put("file with spaces.txt", bytes("s"));
        long total = 0;
        for (byte[] v : files.values()) total += v.length;
        String up = beginOk(token, term, "folder", "project", total, "/home/thoth/upload-test");
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Response r = put(token, up, e.getKey(), e.getValue());
            assertEquals(e.getKey(), 200, r.status);
            assertEquals(e.getKey(), r.json().get("name"));
        }
        assertFalse("visible before finish", new File(cwd, "project").exists());
        Response done = finish(token, up);
        assertEquals("project", done.json().get("name"));
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            assertArrayEquals(e.getKey(), sha256(e.getValue()), sha256(new File(cwd, "project/" + e.getKey())));
        }
        assertEquals(Collections.singletonList("project"), names(cwd));
        // A second upload of the same folder is kept beside the first.
        String again = beginOk(token, term, "folder", "project", 1, "/home/thoth/upload-test");
        put(token, again, "x", bytes("x"));
        assertEquals("project (1)", finish(token, again).json().get("name"));
    }

    @Test
    public void existingFilesAreNeverOverwritten() throws Exception {
        String token = pair();
        File cwd = dir("c");
        Files.write(new File(cwd, "file.txt").toPath(), bytes("mine"));
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, 6, "/home/thoth/c");
        assertEquals("file (1).txt", put(token, up, "file.txt", bytes("one")).json().get("name"));
        assertEquals("file (2).txt", put(token, up, "file.txt", bytes("two")).json().get("name"));
        finish(token, up);
        assertEquals("mine", new String(Files.readAllBytes(new File(cwd, "file.txt").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void theBrowserCannotChooseTheDirectory() throws Exception {
        String token = pair();
        File cwd = dir("mine");
        String term = terminal(token, cwd);
        Response r = post(LanServer.UPLOAD_BEGIN, token, "{\"term\":" + FlatJson.quote(term)
                + ",\"kind\":\"files\",\"bytes\":1,\"target\":\"/etc\",\"cwd\":\"/etc\",\"path\":\"/etc\"}");
        assertEquals(200, r.status);
        assertEquals("/home/thoth/mine", r.json().get("target"));
    }

    @Test
    public void forgedPathsAreRefusedAndNothingLandsOutside() throws Exception {
        String token = pair();
        File cwd = dir("deep/inside");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "folder", "project", -1, "/home/thoth/deep/inside");
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; ++i) longName.append('a');
        String[] forged = {"../evil", "../../evil", "..", "%2e%2e/evil", "%2E%2E%2Fevil", "..%2Fevil",
                "/absolute/path", "%2Fabsolute", "a%5C..%5C..%5Cevil", "a\\b", "C%3A/evil", "C:",
                "a//b", "a/", "", "x%00y", "%zz", longName.toString(), "%252e%252e/evil",
                ".thothterm-upload-0000000000000000/x", "a%0Ab"};
        for (String p : forged) {
            Response r = response(send("PUT", LanServer.UPLOAD_FILE, fileHeaders(token, up, p, 1), bytes("x")));
            assertEquals("path " + p, 400, r.status);
            assertEquals("path " + p, "bad_name", r.error());
        }
        // The upload survives refused names and still completes.
        assertEquals(200, put(token, up, "ok.txt", bytes("ok")).status);
        assertEquals("project", finish(token, up).json().get("name"));
        assertEquals(Collections.singletonList("project"), names(cwd));
        assertEquals(Collections.singletonList("inside"), names(new File(home, "deep")));
        assertEquals(Collections.singletonList("deep"), names(home));
        assertEquals(Collections.singletonList("ok.txt"), names(new File(cwd, "project")));
    }

    @Test
    public void aSymlinkInTheDirectoryIsNeverFollowed() throws Exception {
        String token = pair();
        File cwd = dir("s");
        File outside = tmp.newFolder("outside");
        Files.createSymbolicLink(new File(cwd, "evil").toPath(), outside.toPath());
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "folder", "evil", 4, "/home/thoth/s");
        put(token, up, "test.txt", bytes("pwnd"));
        assertEquals("evil (1)", finish(token, up).json().get("name"));
        assertEquals(Collections.emptyList(), names(outside));
        String files = beginOk(token, term, "files", null, 4, "/home/thoth/s");
        assertEquals("evil (2)",
                put(token, files, "evil", bytes("pwnd")).json().get("name"));
        assertEquals(Collections.emptyList(), names(outside));
    }

    @Test
    public void everyRequestNeedsTheAuthenticatedSession() throws Exception {
        String token = pair();
        File cwd = dir("auth");
        String term = terminal(token, cwd);
        String json = "{\"term\":" + FlatJson.quote(term) + ",\"kind\":\"files\",\"bytes\":1}";

        Map<String, String> h = headers(null);
        assertEquals("no credential", 401, post(LanServer.UPLOAD_BEGIN, h, json).status);
        h = headers("not-a-token");
        assertEquals("wrong credential", 401, post(LanServer.UPLOAD_BEGIN, h, json).status);
        h = headers(token);
        h.put("Origin", "http://evil.example");
        assertEquals("foreign origin", 403, post(LanServer.UPLOAD_BEGIN, h, json).status);
        h = headers(token);
        h.remove("Origin");
        assertEquals("no origin", 403, post(LanServer.UPLOAD_BEGIN, h, json).status);
        h = headers(token);
        h.put("Host", "evil.example:" + port);
        assertEquals("wrong host", 421, post(LanServer.UPLOAD_BEGIN, h, json).status);
        h = headers(token);
        assertEquals("wrong method", 405, response(send("GET", LanServer.UPLOAD_BEGIN, h, null)).status);
        assertEquals("unknown terminal", 404, begin(token, "AAAAAAAAAAAAAAAAAAAAAA", "files", null, 1).status);

        // Another paired browser can neither use this terminal nor this upload.
        String other = pair();
        assertEquals("another browser's terminal", 404, begin(other, term, "files", null, 1).status);
        String up = beginOk(token, term, "files", null, 1, "/home/thoth/auth");
        Response stolen = response(send("PUT", LanServer.UPLOAD_FILE, fileHeaders(other, up, "x", 1), bytes("x")));
        assertEquals("another browser's upload", 404, stolen.status);
        assertEquals(404, finish(other, up).status);
        assertEquals(404, response(send("PUT", LanServer.UPLOAD_FILE,
                fileHeaders(token, "nope", "x", 1), bytes("x"))).status);
        // Only the refused requests' upload's own staging directory exists.
        assertEquals(1, names(cwd).size());
        assertFalse(noStaging(cwd));
        post(LanServer.UPLOAD_CANCEL, token, "{\"upload\":" + FlatJson.quote(up) + "}");
        assertNothingLeft(cwd);
    }

    @Test
    public void anExpiredCredentialFromAnEarlierLanModeIsRefused() throws Exception {
        String token = pair();
        mode.stop(LanMode.Failure.NONE);
        assertTrue(mode.start(LOOPBACK, FIRST_PORT, LanMode.PORT_ATTEMPTS));
        port = mode.status().port;
        String fresh = pair();
        String term = terminal(fresh, dir("x"));
        assertEquals(401, begin(token, term, "files", null, 1).status);
    }

    @Test
    public void anUnauthorisedBodyIsNeverRead() throws Exception {
        String token = pair();
        String term = terminal(token, dir("big"));
        String up = beginOk(token, term, "files", null, 1L << 30, "/home/thoth/big");
        // A 1 GiB upload whose body is never sent: each refusal must come
        // back at once, which it could not if the server read the body first.
        long start = System.currentTimeMillis();
        Map<String, String> h = fileHeaders(null, up, "x", 1L << 30);
        assertEquals(401, response(send("PUT", LanServer.UPLOAD_FILE, h, null)).status);
        h = fileHeaders(token, up, "x", 1L << 30);
        h.put("Origin", "http://evil.example");
        assertEquals(403, response(send("PUT", LanServer.UPLOAD_FILE, h, null)).status);
        h = fileHeaders(token, up, "../x", 1L << 30);
        assertEquals(400, response(send("PUT", LanServer.UPLOAD_FILE, h, null)).status);
        h = fileHeaders(token, up, "x", -1);
        assertEquals(411, response(send("PUT", LanServer.UPLOAD_FILE, h, null)).status);
        assertTrue(System.currentTimeMillis() - start < 5000);
    }

    @Test
    public void aFileTooBigForTheDeviceIsRefusedBeforeItsBody() throws Exception {
        String token = pair();
        File cwd = dir("full");
        String term = terminal(token, cwd);
        fs.freeBytes = 32L * 1024 * 1024;
        assertEquals(507, begin(token, term, "files", null, 1L << 30).status);
        String up = beginOk(token, term, "files", null, -1, "/home/thoth/full");
        Response r = response(send("PUT", LanServer.UPLOAD_FILE, fileHeaders(token, up, "x", 1L << 30), null));
        assertEquals(507, r.status);
        assertEquals("no_space", r.error());
    }

    @Test
    public void runningOutOfSpaceMidwayLeavesNothing() throws Exception {
        String token = pair();
        File cwd = dir("enospc");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, -1, "/home/thoth/enospc");
        fs.spaceLeft = 1000;
        Response r = put(token, up, "big.bin", new byte[200_000]);
        assertEquals(507, r.status);
        eventually("staging removed", () -> noStaging(cwd));
        assertEquals(Collections.emptyList(), names(cwd));
    }

    @Test
    public void oneUploadAtATimePerTerminal() throws Exception {
        String token = pair();
        String term = terminal(token, dir("busy"));
        String up = beginOk(token, term, "files", null, 1, "/home/thoth/busy");
        Response second = begin(token, term, "files", null, 1);
        assertEquals(409, second.status);
        assertEquals("busy", second.error());
        post(LanServer.UPLOAD_CANCEL, token, "{\"upload\":" + FlatJson.quote(up) + "}");
        beginOk(token, term, "files", null, 1, "/home/thoth/busy");
    }

    /** Start a 4 MiB PUT and send only its first half. */
    private Socket halfSent(String token, String up) throws IOException {
        int size = 4 << 20;
        Socket s = send("PUT", LanServer.UPLOAD_FILE, fileHeaders(token, up, encode("file.zip"), size),
                new byte[size / 2]);
        return s;
    }

    private void assertNothingLeft(File cwd) throws InterruptedException {
        eventually("staging removed", () -> noStaging(cwd));
        assertFalse(new File(cwd, "file.zip").exists());
        assertEquals(Collections.emptyList(), names(cwd));
        assertEquals(0, new StagingJournal(new File(tmp.getRoot(), "app/upload-staging")).sweep(fs));
    }

    @Test
    public void cancelStopsATransferAndLeavesNoPartialFile() throws Exception {
        String token = pair();
        File cwd = dir("cancel");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, 4 << 20, "/home/thoth/cancel");
        Socket transfer = halfSent(token, up);
        eventually("staging exists", () -> !noStaging(cwd));
        assertEquals(204, post(LanServer.UPLOAD_CANCEL, token, "{\"upload\":" + FlatJson.quote(up) + "}").status);
        assertNothingLeft(cwd);
        transfer.close();
    }

    @Test
    public void aDroppedConnectionLeavesNoPartialFile() throws Exception {
        String token = pair();
        File cwd = dir("drop");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, 4 << 20, "/home/thoth/drop");
        Socket transfer = halfSent(token, up);
        eventually("staging exists", () -> !noStaging(cwd));
        transfer.close();
        assertNothingLeft(cwd);
        assertEquals(404, put(token, up, "after.txt", bytes("x")).status);
    }

    @Test
    public void lanModeOffAbortsUploadsAndLeavesNoPartialFile() throws Exception {
        String token = pair();
        File cwd = dir("off");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, 4 << 20, "/home/thoth/off");
        halfSent(token, up);
        eventually("staging exists", () -> !noStaging(cwd));
        mode.stop(LanMode.Failure.NONE);
        assertNothingLeft(cwd);
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(LOOPBACK, port), 2000);
            fail("still listening");
        } catch (java.net.ConnectException expected) {
            // gone
        }
    }

    @Test
    public void signingOutAbortsThatBrowsersUploads() throws Exception {
        String token = pair();
        File cwd = dir("signout");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "files", null, 4 << 20, "/home/thoth/signout");
        halfSent(token, up);
        eventually("staging exists", () -> !noStaging(cwd));
        Map<String, String> h = headers(token);
        h.put("Content-Length", "0");
        assertEquals(204, response(send("POST", "/api/logout", h, null)).status);
        assertNothingLeft(cwd);
        assertEquals(401, begin(token, term, "files", null, 1).status);
    }

    @Test
    public void anUploadEndsWithItsTerminal() throws Exception {
        String token = pair();
        File cwd = dir("exit");
        String term = terminal(token, cwd);
        String up = beginOk(token, term, "folder", "project", -1, "/home/thoth/exit");
        put(token, up, "a.txt", bytes("a"));
        // The shell exits: the terminal is gone, and so is the upload.
        for (Pty pty : directories.keySet()) pty.hangUp();
        assertNothingLeft(cwd);
        assertEquals(404, finish(token, up).status);
    }

    @Test
    public void aLargeFileStreamsThroughIntact() throws Exception {
        String token = pair();
        File cwd = dir("large");
        String term = terminal(token, cwd);
        int size = 24 << 20;
        byte[] data = new byte[size];
        new java.util.Random(7).nextBytes(data);
        String up = beginOk(token, term, "files", null, size, "/home/thoth/large");
        assertEquals(200, put(token, up, "large.bin", data).status);
        finish(token, up);
        assertArrayEquals(sha256(data), sha256(new File(cwd, "large.bin")));
    }
}
