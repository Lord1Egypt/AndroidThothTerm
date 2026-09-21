/*
 * Copyright (C) 2018 Roumen Petrov.  All rights reserved.
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

package com.thothterm;

import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;


public class Process {

    static {
        System.loadLibrary("term-system");
    }

    public static int createSubprocess(
            ParcelFileDescriptor masterPty,
            String cmd, String[] arguments, String[] environment
    ) throws IOException {
        return createSubprocess(masterPty, cmd, arguments, environment, null);
    }

    public static int createSubprocess(
            ParcelFileDescriptor masterPty,
            String cmd, String[] arguments, String[] environment,
            String workingDirectory
    ) throws IOException {
        // Let convert to UTF-8 in java code instead in native methods
        try {
            // prepare command path
            byte[] path = cmd.getBytes("UTF-8");

            // prepare command arguments
            byte[][] argv;
            argv = new byte[arguments.length][0];
            for (int k = 0; k < arguments.length; k++) {
                String val = arguments[k];
                argv[k] = val.getBytes("UTF-8");
            }

            // prepare command environment
            byte[][] envp;
            envp = new byte[environment.length][0];
            for (int k = 0; k < environment.length; k++) {
                String val = environment[k];
                envp[k] = val.getBytes("UTF-8");
            }

            byte[] cwd = workingDirectory == null
                    ? new byte[0] : workingDirectory.getBytes("UTF-8");

            // create terminal process ...
            int ptm = masterPty.getFd();
            return Native.createSubprocess(ptm, path, argv, envp, cwd);
        } catch (UnsupportedEncodingException ignore) {
            // TODO: ignore for now
        }
        return -1;
    }

    public static int waitExit(int pid) {
        return Native.waitExit(pid);
    }

    public static void finishChilds(int pid) {
        Native.finishChilds(pid);
    }

    /** SIGKILL the process group, for shutdowns that must not be refused. */
    public static void killChilds(int pid) {
        Native.killChilds(pid);
    }


    private static class Native {
        private static native int createSubprocess(
                int ptm,
                byte[] path, byte[][] argv, byte[][] envp, byte[] cwd
        ) throws IOException;
        private static native int waitExit(int pid);
        private static native void finishChilds(int pid);
        private static native void killChilds(int pid);
    }
}
