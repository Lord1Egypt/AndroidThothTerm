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

package com.thothterm.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UploadBatchTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private NioUploadFs fs;
    private StagingJournal journal;
    private File journalFile;
    private File cwd;
    private UploadTarget target;

    @Before
    public void setUp() throws IOException {
        fs = new NioUploadFs();
        journalFile = new File(tmp.getRoot(), "app/upload-staging");
        journal = new StagingJournal(journalFile);
        cwd = tmp.newFolder("home", "thoth", "projects", "test");
        target = new UploadTarget(cwd.getPath(), "/home/thoth/projects/test", 022);
    }

    @Test
    public void filesLandInTheTargetWithTheirBytesAndTheSessionsUmask() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        byte[] main = "print('hi')\n".getBytes(StandardCharsets.UTF_8);
        byte[] arabic = "مرحبا بالعالم\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("main.py", batch.receive(path("main.py"), main.length, in(main), null));
        assertEquals("مرحبا.txt", batch.receive(path("مرحبا.txt"), arabic.length, in(arabic), null));
        assertNull(batch.finish());

        assertArrayEquals(main, read("main.py"));
        assertArrayEquals(arabic, read("مرحبا.txt"));
        assertEquals("rw-r--r--", mode("main.py"));
        assertOnly("main.py", "مرحبا.txt");
        assertEquals(main.length + arabic.length, batch.batchBytes());
        assertJournalEmpty();
    }

    @Test
    public void anExistingFileIsKeptAndTheUploadGetsANewName() throws Exception {
        Files.write(new File(cwd, "file.txt").toPath(), "mine".getBytes(StandardCharsets.UTF_8));
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        assertEquals("file (1).txt", batch.receive(path("file.txt"), 3, in("one"), null));
        assertEquals("file (2).txt", batch.receive(path("file.txt"), 3, in("two"), null));
        batch.finish();
        assertEquals("mine", text("file.txt"));
        assertEquals("one", text("file (1).txt"));
        assertEquals("two", text("file (2).txt"));
    }

    @Test
    public void aFolderArrivesWholeWithItsHierarchy() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        batch.receive(path("README.md"), 2, in("hi"), null);
        batch.receive(path("src", "main.py"), 4, in("code"), null);
        batch.receive(path("src", "مرحبا.txt"), 5, in("salam"), null);
        batch.receive(path("docs", "notes.txt"), 5, in("notes"), null);
        batch.receive(path(".hidden"), 1, in("h"), null);
        batch.makeDirectory(path("empty", "nested"));
        // Nothing is visible under the final name until finish().
        assertFalse(new File(cwd, "project").exists());
        assertEquals("project", batch.finish());

        assertEquals("code", text("project/src/main.py"));
        assertEquals("salam", text("project/src/مرحبا.txt"));
        assertEquals("notes", text("project/docs/notes.txt"));
        assertEquals("h", text("project/.hidden"));
        assertTrue(new File(cwd, "project/empty/nested").isDirectory());
        assertEquals("rwxr-xr-x", mode("project/src"));
        assertOnly("project");
        assertJournalEmpty();
    }

    @Test
    public void aFolderNeverMergesIntoAnExistingOne() throws Exception {
        File existing = new File(cwd, "project/src");
        assertTrue(existing.mkdirs());
        Files.write(new File(existing, "main.py").toPath(), "old".getBytes(StandardCharsets.UTF_8));
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        batch.receive(path("src", "main.py"), 3, in("new"), null);
        assertEquals("project (1)", batch.finish());
        assertEquals("old", text("project/src/main.py"));
        assertEquals("new", text("project (1)/src/main.py"));
    }

    @Test
    public void aSymlinkInTheTargetIsNeverFollowed() throws Exception {
        File outside = tmp.newFolder("outside");
        File outsideFile = new File(outside, "secret");
        Files.write(outsideFile.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(cwd, "evil").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(cwd, "evil.txt").toPath(), outsideFile.toPath());

        UploadBatch folder = begin(UploadBatch.Kind.FOLDER, "evil");
        folder.receive(path("test.txt"), 3, in("pwn"), null);
        assertEquals("evil (1)", folder.finish());

        UploadBatch files = begin(UploadBatch.Kind.FILES, null);
        assertEquals("evil (1).txt", files.receive(path("evil.txt"), 3, in("pwn"), null));
        files.finish();

        assertEquals(Collections.singletonList("secret"), Arrays.asList(outside.list()));
        assertEquals("keep", new String(Files.readAllBytes(outsideFile.toPath()), StandardCharsets.UTF_8));
        assertTrue(Files.isSymbolicLink(new File(cwd, "evil").toPath()));
        assertEquals("pwn", text("evil (1)/test.txt"));
    }

    @Test
    public void traversalInAnUploadedPathIsRefused() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        for (List<String> bad : Arrays.asList(path(".."), path("..", "x"), path("a", ".."),
                path("/etc"), path("a/b"), path(""), Collections.<String>emptyList())) {
            try {
                batch.receive(bad, 1, in("x"), null);
                fail("accepted " + bad);
            } catch (UploadError e) {
                assertEquals(UploadError.Code.BAD_NAME, e.code);
            }
        }
        // A refused name is refused before anything is written; the batch lives on.
        assertTrue(batch.isOpen());
        batch.receive(path("ok.txt"), 1, in("x"), null);
        assertEquals("project", batch.finish());
        assertOnly("project");
        assertEquals(Collections.singletonList("ok.txt"), Arrays.asList(new File(cwd, "project").list()));
    }

    @Test
    public void aFileAndAFolderWithOneNameFailTheUpload() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        batch.receive(path("x"), 1, in("x"), null);
        try {
            batch.receive(path("x", "y"), 1, in("y"), null);
            fail("merged a folder into a file");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.BAD_NAME, e.code);
        }
        assertAbandoned(batch);
    }

    @Test
    public void aTransferInProgressIsNeverVisibleUnderItsName() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        CountDownLatch halfway = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        InputStream slow = new PausingStream(1 << 20, 1 << 19, halfway, resume);
        AtomicReference<Object> outcome = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                outcome.set(batch.receive(path("big.bin"), 1 << 20, slow, null));
            } catch (UploadError e) {
                outcome.set(e);
            }
        });
        t.start();
        assertTrue(halfway.await(10, TimeUnit.SECONDS));
        assertFalse(new File(cwd, "big.bin").exists());
        String[] names = cwd.list();
        assertEquals(1, names.length);
        assertTrue(names[0].startsWith(UploadNames.STAGING_PREFIX));
        resume.countDown();
        t.join(10_000);
        assertEquals("big.bin", outcome.get());
        batch.finish();
        assertEquals(1 << 20, new File(cwd, "big.bin").length());
    }

    @Test
    public void cancelStopsTheTransferAndLeavesNothing() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        batch.receive(path("done.txt"), 4, in("done"), null);
        CountDownLatch halfway = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        PausingStream slow = new PausingStream(1 << 20, 1 << 19, halfway, never);
        AtomicReference<Object> outcome = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                outcome.set(batch.receive(path("big.bin"), 1 << 20, slow, null));
            } catch (UploadError e) {
                outcome.set(e);
            }
        });
        t.start();
        assertTrue(halfway.await(10, TimeUnit.SECONDS));
        batch.cancel();
        t.join(10_000);
        assertFalse(t.isAlive());
        assertTrue(slow.closed);
        assertEquals(UploadError.Code.CANCELLED, ((UploadError) outcome.get()).code);
        assertOnly();
        assertJournalEmpty();
        try {
            batch.finish();
            fail("finished a cancelled batch");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.CANCELLED, e.code);
        }
    }

    @Test
    public void cancellingAFilesBatchKeepsTheFilesAlreadyFinished() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        batch.receive(path("first.txt"), 5, in("first"), null);
        batch.cancel();
        assertOnly("first.txt");
        assertJournalEmpty();
    }

    @Test
    public void aTruncatedSourceLeavesNoFile() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        try {
            batch.receive(path("file.zip"), 100, in("only a few bytes"), null);
            fail("accepted a short file");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.IO, e.code);
        }
        assertAbandoned(batch);
    }

    @Test
    public void moreBytesThanAnnouncedLeaveNoFile() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        try {
            batch.receive(path("file.zip"), 2, in("too long"), null);
            fail("accepted a long file");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.IO, e.code);
        }
        assertAbandoned(batch);
    }

    @Test
    public void aFullDeviceFailsCleanly() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        fs.spaceLeft = 100_000;
        try {
            batch.receive(path("big.bin"), 1 << 20, new PausingStream(1 << 20, -1, null, null), null);
            fail("wrote past a full device");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.NO_SPACE, e.code);
        }
        assertAbandoned(batch);
    }

    @Test
    public void notEnoughFreeSpaceIsRefusedUpFront() throws Exception {
        fs.freeBytes = UploadBatch.RESERVE_BYTES + 1000;
        try {
            begin(UploadBatch.Kind.FILES, null, 2000);
            fail("started without room");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.NO_SPACE, e.code);
        }
        begin(UploadBatch.Kind.FILES, null, 1000).cancel();
        assertOnly();
    }

    @Test
    public void anUnwritableOrMissingTargetFailsClearly() throws Exception {
        Files.setPosixFilePermissions(cwd.toPath(), PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            begin(UploadBatch.Kind.FILES, null);
            fail("uploaded into a read-only directory");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.NOT_WRITABLE, e.code);
        } finally {
            Files.setPosixFilePermissions(cwd.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        target = new UploadTarget(new File(cwd, "gone").getPath(), "/gone", 022);
        try {
            begin(UploadBatch.Kind.FILES, null);
            fail("uploaded into a missing directory");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.DIRECTORY_GONE, e.code);
        }
        assertJournalEmpty();
    }

    @Test
    public void aLargeFileStreamsThroughIntact() throws Exception {
        long size = 48L * 1024 * 1024 + 12345;
        UploadBatch batch = begin(UploadBatch.Kind.FILES, null);
        PausingStream source = new PausingStream(size, -1, null, null);
        long[] lastProgress = new long[2];
        batch.receive(path("large.bin"), size, source, (item, all) -> {
            lastProgress[0] = item;
            lastProgress[1] = all;
        });
        batch.finish();
        assertEquals(size, lastProgress[0]);
        assertEquals(size, lastProgress[1]);
        assertEquals(size, new File(cwd, "large.bin").length());
        assertArrayEquals(source.digest(), sha256(new File(cwd, "large.bin")));
    }

    @Test
    public void aDeadRunsStagingIsRemovedAtTheNextStart() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        batch.receive(path("half.bin"), 3, in("abc"), null);
        // The process dies here: no finish, no cancel.
        File stray = new File(cwd, "not-ours");
        assertTrue(stray.mkdir());
        StagingJournal next = new StagingJournal(journalFile);
        assertEquals(1, next.sweep(fs));
        assertOnly("not-ours");
        assertEquals(0, new StagingJournal(journalFile).sweep(fs));
    }

    @Test
    public void theSweepOnlyRemovesStagingNames() throws Exception {
        File victim = new File(cwd, "important");
        assertTrue(victim.mkdir());
        journal.add(victim.getPath());
        assertEquals(0, new StagingJournal(journalFile).sweep(fs));
        assertTrue(victim.isDirectory());
    }

    @Test
    public void aStagingDirectoryThatBecameALinkIsNotFollowed() throws Exception {
        UploadBatch batch = begin(UploadBatch.Kind.FOLDER, "project");
        String[] names = cwd.list();
        File staging = new File(cwd, names[0]);
        File outside = tmp.newFolder("outside2");
        Files.write(new File(outside, "keep").toPath(), new byte[]{1});
        // Replaced behind our back by a link to somewhere else.
        deleteTree(staging.toPath());
        Files.createSymbolicLink(staging.toPath(), outside.toPath());
        batch.cancel();
        assertTrue(new File(outside, "keep").isFile());
    }

    // ------------------------------------------------------------ helpers

    private UploadBatch begin(UploadBatch.Kind kind, String folder) throws UploadError {
        return begin(kind, folder, -1);
    }

    private UploadBatch begin(UploadBatch.Kind kind, String folder, long bytes) throws UploadError {
        return UploadBatch.begin(fs, journal, target, kind, folder, bytes);
    }

    private void assertAbandoned(UploadBatch batch) throws Exception {
        assertFalse(batch.isOpen());
        assertOnly();
        assertJournalEmpty();
        try {
            batch.receive(path("more.txt"), 1, in("x"), null);
            fail("an abandoned batch took more");
        } catch (UploadError e) {
            assertEquals(UploadError.Code.CANCELLED, e.code);
        }
    }

    private void assertOnly(String... expected) {
        String[] names = cwd.list();
        Arrays.sort(names);
        String[] want = expected.clone();
        Arrays.sort(want);
        assertEquals(Arrays.asList(want), Arrays.asList(names));
    }

    private void assertJournalEmpty() {
        assertEquals(0, new StagingJournal(journalFile).sweep(fs));
    }

    private static List<String> path(String... parts) {
        return Arrays.asList(parts);
    }

    private static InputStream in(String s) {
        return in(s.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream in(byte[] b) {
        return new ByteArrayInputStream(b);
    }

    private byte[] read(String name) throws IOException {
        return Files.readAllBytes(new File(cwd, name).toPath());
    }

    private String text(String name) throws IOException {
        return new String(read(name), StandardCharsets.UTF_8);
    }

    private String mode(String name) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(cwd, name).toPath()));
    }

    private static byte[] sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) md.update(buffer, 0, n);
        }
        return md.digest();
    }

    private static void deleteTree(Path p) throws IOException {
        if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (java.nio.file.DirectoryStream<Path> s = Files.newDirectoryStream(p)) {
                for (Path c : s) deleteTree(c);
            }
        }
        Files.delete(p);
    }

    /**
     * Deterministic pseudo-random bytes; optionally blocks once at
     * {@code pauseAt} until {@code resume} opens, and stops blocking when
     * closed, like a socket.
     */
    static final class PausingStream extends InputStream {
        private final long size;
        private final long pauseAt;
        private final CountDownLatch paused;
        private final CountDownLatch resume;
        private final MessageDigest md;
        private long position;
        private int state = 0x12345678;
        volatile boolean closed;

        PausingStream(long size, long pauseAt, CountDownLatch paused, CountDownLatch resume) throws Exception {
            this.size = size;
            this.pauseAt = pauseAt;
            this.paused = paused;
            this.resume = resume;
            this.md = MessageDigest.getInstance("SHA-256");
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) throw new IOException("closed");
            if (position >= size) return -1;
            if (pauseAt >= 0 && position >= pauseAt && paused != null && paused.getCount() > 0) {
                paused.countDown();
                while (!closed) {
                    try {
                        if (resume.await(20, TimeUnit.MILLISECONDS)) break;
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
                if (closed) throw new IOException("closed");
            }
            int n = (int) Math.min(len, size - position);
            if (pauseAt >= 0 && position < pauseAt) n = (int) Math.min(n, pauseAt - position);
            for (int i = 0; i < n; ++i) {
                state = state * 1103515245 + 12345;
                b[off + i] = (byte) (state >>> 24);
            }
            md.update(b, off, n);
            position += n;
            return n;
        }

        @Override
        public void close() {
            closed = true;
        }

        byte[] digest() throws Exception {
            return ((MessageDigest) md.clone()).digest();
        }
    }
}
