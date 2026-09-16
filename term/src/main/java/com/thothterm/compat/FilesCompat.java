/*
 * Copyright (C) 2023-2024 Roumen Petrov.  All rights reserved.
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

package com.thothterm.compat;

import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.RequiresApi;


public class FilesCompat {

    public static InputStream newInputStream(File file) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /*API level 26*/)
            return Compat26.newInput(file);
        else
            return Compat1.newInput(file);
    }

    public static OutputStream newOutputStream(File file) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /*API level 26*/)
            return Compat26.newOutput(file);
        else
            return Compat1.newOutput(file);
    }


    @RequiresApi(26)
    private static class Compat26 {
        static InputStream newInput(File file) throws IOException {
            return java.nio.file.Files.newInputStream(file.toPath());
        }

        static OutputStream newOutput(File file) throws IOException {
            return java.nio.file.Files.newOutputStream(file.toPath());
        }
    }

    private static class Compat1 {
        /**
         * @noinspection IOStreamConstructor
         */
        static InputStream newInput(File file) throws IOException {
            return new FileInputStream(file);
        }

        /**
         * @noinspection IOStreamConstructor
         */
        static OutputStream newOutput(File file) throws IOException {
            return new FileOutputStream(file);
        }
    }
}
