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

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Extracts a pinned {@code .tar.gz} rootfs into a staging directory and
 * verifies it: the one code path the app, the host extractor gate and the
 * device extractor gate all run.
 *
 * <p>The SHA-256 covers every byte of the compressed file: after the tar end
 * marker the rest of the gzip stream and anything after it are read to EOF,
 * so the digest never depends on how far a buffer happened to read ahead.</p>
 *
 * <p>Verification happens after extraction for a streamed archive. That is
 * safe because extraction is confined to the staging directory by
 * construction (see {@link TarballExtractor}), and staging is discarded on
 * any failure before anything is promoted.</p>
 */
public final class RootfsArchive {
    public interface Progress {
        /** @param compressedBytes bytes of the archive read so far */
        void onProgress(long compressedBytes, long entries);
    }

    /** What one extraction did. */
    public static final class Result {
        public long entries;
        public long rejected;
        public long skippedSpecial;
        public long hardlinkFallbacks;
        public long compressedBytes;
        public long extractedBytes;
        public long millis;
        public String sha256 = "";
        public List<String> rejectionReasons = Collections.emptyList();

        public String summary() {
            return "entries=" + entries + " rejected=" + rejected
                    + " skipped=" + skippedSpecial + " hardlinkFallbacks=" + hardlinkFallbacks
                    + " compressedBytes=" + compressedBytes + " extractedBytes=" + extractedBytes
                    + " ms=" + millis;
        }
    }

    private RootfsArchive() {
    }

    /** Extracts {@code compressed} into {@code staging}; the caller verifies. */
    public static Result extract(InputStream compressed, File staging, FileOps ops,
                                 final Progress progress) throws IOException {
        long start = System.nanoTime();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        final CountingInputStream counting = new CountingInputStream(compressed);
        DigestInputStream digesting = new DigestInputStream(counting, digest);
        GZIPInputStream gzip = new GZIPInputStream(digesting, 64 * 1024);
        TarballExtractor extractor = new TarballExtractor(ops, staging,
                progress == null ? null
                        : count -> progress.onProgress(counting.count, count));
        try {
            extractor.extract(gzip);
            // The tar end marker is not the end of the file: drain the rest of
            // the gzip member(s), then any raw trailing bytes, into the digest.
            drain(gzip);
            drain(digesting);
        } finally {
            try {
                gzip.close();
            } catch (IOException ignored) {
                // The result does not depend on closing.
            }
        }
        Result result = new Result();
        result.entries = extractor.extractedEntries();
        result.rejected = extractor.rejectedEntries();
        result.rejectionReasons = new ArrayList<>(extractor.rejectionReasons());
        result.skippedSpecial = extractor.skippedSpecialEntries();
        result.hardlinkFallbacks = extractor.hardlinkFallbacks();
        result.compressedBytes = counting.count;
        result.extractedBytes = extractor.copiedBytes();
        result.sha256 = toHex(digest.digest());
        result.millis = (System.nanoTime() - start) / 1_000_000L;
        if (progress != null) progress.onProgress(counting.count, result.entries);
        return result;
    }

    /**
     * Fails unless the archive is exactly the pinned one and nothing in it was
     * refused. A pinned image is trusted content; a refused entry would mean a
     * silently incomplete userland, so it fails the installation instead.
     */
    public static void verify(Result result, String expectedSha256) throws IOException {
        if (expectedSha256 == null || !expectedSha256.equalsIgnoreCase(result.sha256)) {
            throw new IOException("Rootfs checksum mismatch");
        }
        if (result.rejected > 0) {
            throw new IOException("Rootfs archive has " + result.rejected
                    + " entries that cannot be extracted safely: " + result.rejectionReasons);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        while (in.read(buffer) >= 0) {
            // Discard; the digest stream sees every byte.
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        volatile long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) count += read;
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            if (skipped > 0) count += skipped;
            return skipped;
        }
    }
}
