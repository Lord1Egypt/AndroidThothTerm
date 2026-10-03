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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Every arm64 ELF the app ships must have 16 KB-aligned LOAD segments.
 *
 * <p>Android 15 introduced devices with 16 KB pages, and a binary aligned to
 * 4 KB makes the system report the whole app as incompatible. This was a real
 * release blocker: {@code libproot_loader.so} shipped at 4 KB because PRoot's
 * makefile links the loader through its own {@code LOADER_LDFLAGS}, which the
 * alignment flags in {@code LDFLAGS} never reached, and the build only checked
 * {@code proot}. The build script now checks each artifact; this test is the
 * second line of defence and reads the ELF headers directly.
 */
public class PageAlignmentTest {
    /** 2**14. Android also accepts 64 KB, hence the >= comparison below. */
    private static final long REQUIRED_ALIGNMENT = 16384L;
    private static final int PT_LOAD = 1;

    private static File moduleDir() {
        File here = new File("").getAbsoluteFile();
        if (new File(here, "src/main/jniLibs").isDirectory()) return here;
        return new File(here, "term-ubuntu");
    }

    /** Reads the p_align of every PT_LOAD segment of a 64-bit little-endian ELF. */
    private static List<Long> loadAlignments(File elf) throws IOException {
        List<Long> alignments = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(elf, "r")) {
            byte[] ident = new byte[16];
            file.readFully(ident);
            assertEquals("not an ELF: " + elf, 0x7F, ident[0] & 0xFF);
            assertEquals("expected ELF64: " + elf, 2, ident[4]);
            assertEquals("expected little endian: " + elf, 1, ident[5]);

            byte[] header = new byte[48];
            file.seek(16);
            file.readFully(header);
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            // The buffer starts at e_type (file offset 16).
            buffer.position(8);              // skip e_type, e_machine, e_version
            buffer.getLong();                // e_entry
            long phoff = buffer.getLong();
            buffer.getLong();                // e_shoff
            buffer.getInt();                 // e_flags
            buffer.getShort();               // e_ehsize
            int phentsize = buffer.getShort() & 0xFFFF;
            int phnum = buffer.getShort() & 0xFFFF;

            for (int i = 0; i < phnum; i++) {
                byte[] entry = new byte[phentsize];
                file.seek(phoff + (long) i * phentsize);
                file.readFully(entry);
                ByteBuffer ph = ByteBuffer.wrap(entry).order(ByteOrder.LITTLE_ENDIAN);
                int type = ph.getInt();
                if (type != PT_LOAD) continue;
                // p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align
                ph.position(48);
                alignments.add(ph.getLong());
            }
        }
        return alignments;
    }

    private static List<File> shippedElfs() {
        List<File> files = new ArrayList<>();
        File module = moduleDir();
        File jni = new File(module, "src/main/jniLibs/arm64-v8a");
        File runtime = new File(module, "src/main/assets/runtime/arm64-v8a");
        for (File dir : new File[]{jni, runtime}) {
            File[] found = dir.listFiles();
            if (found == null) continue;
            for (File file : found) {
                String name = file.getName();
                if (name.endsWith(".so") || name.contains(".so.")) files.add(file);
            }
        }
        return files;
    }

    @Test
    public void everyShippedArm64ElfIsSixteenKilobyteAligned() throws Exception {
        List<File> elfs = shippedElfs();
        // The runtime is produced by tools/build-proot.sh; if it has not run,
        // there is nothing to check and nothing to ship either.
        org.junit.Assume.assumeFalse("no native artifacts built yet", elfs.isEmpty());

        for (File elf : elfs) {
            List<Long> alignments = loadAlignments(elf);
            assertTrue(elf.getName() + " has no PT_LOAD segments", !alignments.isEmpty());
            for (long alignment : alignments) {
                assertTrue(elf.getName() + " has a LOAD segment aligned to " + alignment
                                + ", need at least " + REQUIRED_ALIGNMENT
                                + " for 16 KB page devices",
                        alignment >= REQUIRED_ALIGNMENT);
            }
        }
    }

    @Test
    public void theLoaderIsCoveredByTheBuildScriptsOwnCheck() throws Exception {
        // The loader is the artifact that regressed, precisely because it is
        // linked through a different variable than everything else.
        File script = new File(moduleDir(), "tools/build-proot.sh");
        assertTrue("build-proot.sh is missing", script.isFile());
        String text = new String(Files.readAllBytes(script.toPath()),
                StandardCharsets.UTF_8);

        assertTrue("the loader link must set the alignment explicitly",
                text.contains("LOADER_LDFLAGS=\"$PAGE_ALIGN_LDFLAGS\""));
        assertTrue("both page-size knobs must be set",
                text.contains("max-page-size=16384")
                        && text.contains("common-page-size=16384"));
        assertTrue("the build must verify the loader, not just proot",
                text.contains("check_alignment \"$LOADER_BIN\""));
        assertTrue("the build must verify proot",
                text.contains("check_alignment \"$PROOT_BIN\""));
    }

    @Test
    public void theCmakeTargetsSetTheAlignmentTooAndNothingSuppressesTheWarning()
            throws Exception {
        File repo = moduleDir().getParentFile();
        for (String path : new String[]{
                "term-ubuntu/src/main/jni/CMakeLists.txt",
                "libtermexec/src/main/cpp/CMakeLists.txt"}) {
            File file = new File(repo, path);
            assertTrue("missing " + path, file.isFile());
            String text = new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(path + " must set max-page-size",
                    text.contains("max-page-size=16384"));
            assertTrue(path + " must set common-page-size",
                    text.contains("common-page-size=16384"));
        }

        // Aligning the binaries is the fix; declaring compatibility is not.
        File manifest = new File(moduleDir(), "src/main/AndroidManifest.xml");
        String text = new String(Files.readAllBytes(manifest.toPath()),
                StandardCharsets.UTF_8);
        assertTrue("pageSizeCompat must not be used to hide misaligned binaries",
                !text.contains("pageSizeCompat"));
    }
}
