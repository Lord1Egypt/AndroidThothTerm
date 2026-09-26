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

package com.thothterm.garden;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fetches the pinned distro rootfs for builds that do not embed it.
 *
 * <p>The archive is written to a {@code .part} file and is only promoted to its
 * final name once its SHA-256 matches the pinned digest, so an interrupted or
 * corrupted transfer can never be mistaken for a complete one. Nothing is
 * extracted from a file that has not been verified first.
 *
 * <p>The URL, size and digest all come from the same {@code distro.properties}
 * the embedded build is pinned to, so both flavours install byte-identical
 * userlands.
 */
final class RootfsDownloader {
    /** Refuse a redirect chain rather than follow it indefinitely. */
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int BUFFER = 64 * 1024;

    interface Progress {
        /** @param total expected byte count, or -1 when the server withheld it */
        void onBytes(long downloaded, long total);
    }

    private final File target;
    private final GardenDistro image;

    RootfsDownloader(File target, GardenDistro image) {
        this.target = target;
        this.image = image;
    }

    /** True once a previously downloaded archive is present and still matches. */
    boolean isVerified() {
        if (!target.isFile()) return false;
        try {
            String actual = sha256Of(target);
            if (actual.equalsIgnoreCase(image.rootfsSha256())) return true;
            ThothLog.w(LogCategory.SECURITY,
                    "Cached distro archive failed verification; discarding it");
        } catch (IOException e) {
            ThothLog.w(LogCategory.STORAGE, "Cannot read cached distro archive: " + e);
        }
        // A file that does not verify is never kept: leaving it would turn one
        // bad download into a permanently broken install.
        deleteQuietly(target);
        return false;
    }

    /**
     * Downloads and verifies the pinned archive. On any failure the partial file
     * is removed and the exception propagates, so the caller can simply retry.
     */
    void download(Progress progress) throws IOException {
        File partial = new File(target.getAbsolutePath() + ".part");
        deleteQuietly(partial);

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create download directory");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }

        HttpURLConnection connection = open(image.rootfsUrl());
        try {
            long expected = image.compressedSize();
            long reported = connection.getContentLengthLong();
            if (reported > 0 && expected > 0 && reported != expected) {
                throw new IOException("distro archive is " + reported
                        + " bytes, expected " + expected);
            }

            long total = 0;
            InputStream in = connection.getInputStream();
            OutputStream out = new FileOutputStream(partial);
            try {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    total += read;
                    if (expected > 0 && total > expected) {
                        throw new IOException("distro archive is larger than expected");
                    }
                    if (progress != null) progress.onBytes(total, expected);
                }
                out.flush();
            } finally {
                closeQuietly(in);
                closeQuietly(out);
            }

            if (expected > 0 && total != expected) {
                throw new IOException("distro archive ended early at " + total
                        + " of " + expected + " bytes");
            }

            String actual = RootfsManager.toHex(digest.digest());
            if (!actual.equalsIgnoreCase(image.rootfsSha256())) {
                ThothLog.w(LogCategory.SECURITY, "Downloaded distro archive checksum mismatch");
                throw new IOException("Downloaded distro archive failed verification");
            }

            deleteQuietly(target);
            if (!partial.renameTo(target)) {
                throw new IOException("Cannot store the verified distro archive");
            }
            ThothLog.i(LogCategory.ROOTFS, "distro archive downloaded and verified bytes=" + total);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Opens the URL over HTTPS, following redirects manually so that a downgrade
     * to plain HTTP is refused rather than silently accepted.
     */
    private HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!current.regionMatches(true, 0, "https://", 0, 8)) {
                throw new IOException("Refusing to download over an insecure URL");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept-Encoding", "identity");

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307 || status == 308) {
                String next = connection.getHeaderField("Location");
                connection.disconnect();
                if (next == null) throw new IOException("Redirect without a target");
                current = next;
                continue;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("distro archive request failed with HTTP " + status);
            }
            return connection;
        }
        throw new IOException("Too many redirects fetching the distro archive");
    }

    private static String sha256Of(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        InputStream in = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        } finally {
            closeQuietly(in);
        }
        return RootfsManager.toHex(digest.digest());
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            ThothLog.w(LogCategory.STORAGE, "Cannot remove " + file.getName());
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing useful to do while unwinding.
        }
    }
}
