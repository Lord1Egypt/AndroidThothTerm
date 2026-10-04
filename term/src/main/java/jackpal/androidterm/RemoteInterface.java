/*
 * Copyright (C) 2012 Steven Luo
 * Copyright (C) 2019-2023 Roumen Petrov.  All rights reserved.
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

package jackpal.androidterm;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.thothterm.Application;
import com.thothterm.RemoteActionActivity;
import com.thothterm.TermActivity;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import jackpal.androidterm.emulatorview.TermSession;


public class RemoteInterface extends RemoteActionActivity {

    /**
     * Quote a string so it can be used as a parameter in bash and similar shells.
     */
    public static String quoteForBash(String s) {
        StringBuilder builder = new StringBuilder();
        String specialChars = "\"\\$`!";
        builder.append('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (specialChars.indexOf(c) >= 0) {
                builder.append('\\');
            }
            builder.append(c);
        }
        builder.append('"');
        return builder.toString();
    }

    @Override
    protected void processAction(@NonNull Intent intent, @NonNull String action) {
        if (Intent.ACTION_SEND.equals(action)) {
            /* "permission.RUN_SCRIPT" not required as this is merely opening a new window. */
            processSendAction(intent);
            return;
        }
        // Intent sender may not have permissions, ignore any extras
        openNewWindow(null);
    }

    /*
     * The shared path comes from any installed app. It becomes the new
     * shell's working directory and is never typed into the terminal: shell
     * quoting does not neutralise terminal control characters
     * (docs/security/THORNS.md).
     */
    private void processSendAction(@NonNull Intent intent) {
        Uri uri = ExtraStreamCompat.get(intent);
        File dir = null;
        if (uri != null && "file".equals(uri.getScheme())) {
            dir = shareDirectory(uri.getPath());
        }
        openNewWindowIn(dir);
    }

    /**
     * The existing directory a shared file path names -- the path itself or
     * its parent -- or null when it names none.
     */
    static File shareDirectory(String path) {
        if (path == null) return null;
        File file = new File(path);
        if (!file.isAbsolute()) return null;
        File dir = file.isDirectory() ? file : file.getParentFile();
        return dir != null && dir.isDirectory() ? dir : null;
    }

    protected void switchWindowActivity(int index) {
        Intent intent = TermActivity.getSwitchWindowIntent(this)
                .putExtra(Application.ARGUMENT_TARGET_WINDOW, index);
        startActivity(intent);
    }

    protected void setResultWindow(String handle) {
        Intent result = new Intent();
        result.putExtra(Application.ARGUMENT_WINDOW_HANDLE, handle);
        setResult(RESULT_OK, result);
    }

    protected String openNewWindow(String iInitialCommand) {
        return openNewWindow(iInitialCommand, null);
    }

    private void openNewWindowIn(File workingDirectory) {
        openNewWindow(null, workingDirectory != null ? workingDirectory.getPath() : null);
    }

    private String openNewWindow(String iInitialCommand, String workingDirectory) {
        TermService service = getTermService();

        try {
            TermSession session = TermActivity.createTermSession(this, iInitialCommand,
                    workingDirectory);

            service.addSession(session);

            String handle = UUID.randomUUID().toString();
            ((GenericTermSession) session).setHandle(handle);

            Intent intent = TermActivity.getNewWindowIntent(this);
            startActivity(intent);

            return handle;
        } catch (IOException e) {
            return null;
        }
    }

    protected String appendToWindow(String handle, String iInitialCommand) {
        TermService service = getTermService();

        // Find the target window
        GenericTermSession target = null;
        int index;
        for (index = 0; index < service.getSessionCount(); ++index) {
            GenericTermSession session = (GenericTermSession) service.getSession(index);
            String h = session.getHandle();
            if (h != null && h.equals(handle)) {
                target = session;
                break;
            }
        }

        if (target == null) {
            // Target window not found, open a new one
            return openNewWindow(iInitialCommand);
        }

        if (iInitialCommand != null) {
            target.write(iInitialCommand);
            target.write('\r');
        }

        switchWindowActivity(index);

        return handle;
    }


    private static class ExtraStreamCompat {

        private static Uri get(Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) return null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU /*API level 33*/)
                return Compat33.get(extras);
            else
                return Compat1.get(extras);
        }

        @RequiresApi(33)
        private static class Compat33 {
            private static Uri get(Bundle extras) {
                return extras.getParcelable(Intent.EXTRA_STREAM, Uri.class);
            }
        }

        // Explicitly suppress deprecation warnings
        // "get(String) in BaseBundle has been deprecated"
        // Remark: get() was replaced with getParcelable()
        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        private static class Compat1 {
            private static Uri get(Bundle extras) {
                Object extraStream = extras.getParcelable(Intent.EXTRA_STREAM);
                return (extraStream instanceof Uri) ? (Uri) extraStream : null;
            }
        }
    }
}
