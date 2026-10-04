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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * The F-Droid build downloads twice: the base system from cdimage.ubuntu.com
 * after consent, then sudo with apt from the hosts the image's apt sources
 * name. The consent screen and the store description must name both stages
 * and every one of those hosts, and must not claim nothing else is downloaded.
 */
public class DownloadDisclosureTest {
    private static final Pattern URI_HOST = Pattern.compile("(?m)^URIs:\\s*https?://([^/\\s]+)/");

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    private static String consent() throws IOException {
        Matcher m = Pattern.compile("(?s)<string name=\"ubuntu_consent_body\">(.*?)</string>")
                .matcher(read("src/main/res/values/strings.xml"));
        assertTrue(m.find());
        return m.group(1);
    }

    private static String description() throws IOException {
        return read("../fastlane/metadata/android/en-US/full_description.txt");
    }

    @Test
    public void bothStagesAreDisclosed() throws IOException {
        for (String text : new String[]{consent(), description()}) {
            assertTrue(text.contains("archive.ubuntu.com"));
            assertTrue(text.contains("security.ubuntu.com"));
            assertTrue(text.contains("sudo"));
            assertTrue(text.contains("in the background"));
            assertFalse(text, text.contains("Nothing else is downloaded"));
        }
        assertTrue(consent().contains("from %3$s"));
        assertTrue(description().contains("cdimage.ubuntu.com"));
        assertFalse(description().contains("embedded and set up"));
    }

    @Test
    public void noTrademarkInTheTitle() throws IOException {
        assertFalse(read("src/main/res/values/strings.xml").contains("ThothTerm Ubuntu"));
        assertFalse(read("../fastlane/metadata/android/en-US/title.txt").contains("Ubuntu"));
        assertFalse(description().contains("ThothTerm Ubuntu"));
        assertTrue(description().contains("not affiliated with\nor endorsed by Canonical"));
    }

    /** Runs where the full flavour's pinned image is present (prepare-assets.sh). */
    @Test
    public void disclosedHostsAreTheImagesAptSources() throws IOException {
        File image = new File("src/full/assets/ubuntu/ubuntu-base-26.04.1-base-arm64.tgz");
        Assume.assumeTrue("pinned image not prepared", image.isFile());
        String sources = tarEntry(image, "etc/apt/sources.list.d/ubuntu.sources");
        Set<String> hosts = new TreeSet<>();
        Matcher m = URI_HOST.matcher(sources);
        while (m.find()) hosts.add(m.group(1));
        assertFalse(hosts.isEmpty());
        for (String host : hosts) {
            assertTrue(host, consent().contains(host));
            assertTrue(host, description().contains(host));
        }
    }

    private static String tarEntry(File tgz, String name) throws IOException {
        try (InputStream in = new GZIPInputStream(new FileInputStream(tgz))) {
            byte[] header = new byte[512];
            while (readFully(in, header)) {
                String entry = new String(header, 0, 100, StandardCharsets.UTF_8).replace("\0", "");
                if (entry.isEmpty()) break;
                String octal = new String(header, 124, 12, StandardCharsets.US_ASCII).replace("\0", "").trim();
                long size = octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
                long padded = (size + 511) / 512 * 512;
                if (entry.replaceFirst("^\\./", "").equals(name)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[(int) padded];
                    assertTrue(readFully(in, buf));
                    out.write(buf, 0, (int) size);
                    return out.toString("UTF-8");
                }
                long skipped = 0;
                while (skipped < padded) skipped += in.skip(padded - skipped);
            }
        }
        throw new AssertionError(name + " not in " + tgz);
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }
}
