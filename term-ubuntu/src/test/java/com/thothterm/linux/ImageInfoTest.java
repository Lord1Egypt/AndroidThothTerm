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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

public class ImageInfoTest {
    @Test
    public void parsesGeneratedProperties() throws Exception {
        String text = "imageId=ubuntu-26.04.1-base-arm64\n"
                + "ubuntuVersion=26.04.1\n"
                + "architecture=aarch64\n"
                + "sourceUrl=https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz\n"
                + "filename=ubuntu-base-26.04.1-base-arm64.tar.gz\n"
                + "upstreamSha256=5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd\n"
                + "compressedSize=35092106\n"
                + "uncompressedSize=122122240\n"
                + "schemaVersion=1\n";

        ImageInfo info = ImageInfo.load(
                new ByteArrayInputStream(text.getBytes(Charset.forName("US-ASCII"))));

        assertEquals("ubuntu-26.04.1-base-arm64", info.imageId());
        assertEquals("26.04.1", info.ubuntuVersion());
        assertEquals("aarch64", info.architecture());
        assertEquals("ubuntu-base-26.04.1-base-arm64.tar.gz", info.filename());
        assertEquals("5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd",
                info.upstreamSha256());
        assertEquals(35092106L, info.compressedSize());
        assertEquals(122122240L, info.uncompressedSize());
        assertEquals(1, info.schemaVersion());
    }
}
