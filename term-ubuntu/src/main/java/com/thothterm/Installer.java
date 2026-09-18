/*
 * Copyright (C) 2019-2025 Roumen Petrov.  All rights reserved.
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

import android.content.res.AssetManager;
import android.text.TextUtils;

import com.thothterm.compat.FilesCompat;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;


public class Installer {

    public static final String APPINFO_COMMAND = "libexec-t1plus.so";
    public static final String APPEXEC_COMMAND = "libcmd-t1plus.so";

    public static boolean install_directory(File dir, boolean share) {
        if (!(dir.exists() || dir.mkdir())) return false;

        // always preset directory permissions
        return dir.setReadable(true, !share) &&
                dir.setExecutable(true, false);
    }

    public static boolean install_text_file(String[] script, File file) {
        try {
            PrintWriter out = new PrintWriter(file);
            for (String line : script)
                out.println(line);
            out.flush();
            out.close();
            // always preset permissions
            return file.setReadable(true, true);
        } catch (IOException ignore) {
        }
        return false;
    }

    public static boolean installAppScriptFile() {
        ArrayList<String> shell_script = new ArrayList<>();

        String sysmkshrc = "/system/etc/mkshrc";
        if (!Application.getScriptFilePath().equals(sysmkshrc) &&
                Application.settings.sourceSystemShellStartupFile() &&
                new File(sysmkshrc).exists())
            shell_script.add(". " + sysmkshrc);

        // Friendly prompt: display HOME by its short name instead of the full
        // private application path. Everything is derived from the shell's own
        // $HOME/$PWD; no Android package path is assumed. $HOME may be spelled
        // with a different alias than the kernel reports for $PWD (for example
        // through a symlinked prefix), so resolve HOME's physical form once at
        // startup and accept either spelling. $PWD/HOME and the real `pwd` are
        // never modified; only the PS1 presentation differs.
        shell_script.add("__THOTHTERM_HOME=${HOME%/}");
        shell_script.add("if [ -d \"$__THOTHTERM_HOME\" ]; then");
        shell_script.add("  __THOTHTERM_HOME_PHYS=$(cd \"$__THOTHTERM_HOME\" && pwd -P)");
        shell_script.add("fi");
        shell_script.add(": \"${__THOTHTERM_HOME_PHYS:=$__THOTHTERM_HOME}\"");
        shell_script.add("__thothterm_prompt_path() {");
        shell_script.add("  __ttp_pwd=${PWD%/}");
        shell_script.add("  __ttp_base=${__THOTHTERM_HOME##*/}");
        shell_script.add("  for __ttp_home in \"$__THOTHTERM_HOME\" \"$__THOTHTERM_HOME_PHYS\"; do");
        shell_script.add("    [ -n \"$__ttp_home\" ] || continue");
        shell_script.add("    case \"$__ttp_pwd\" in");
        shell_script.add("      \"$__ttp_home\") printf '%s' \"$__ttp_base\"; return ;;");
        shell_script.add("      \"$__ttp_home\"/*) printf '%s/%s' \"$__ttp_base\" \"${__ttp_pwd#\"$__ttp_home\"/}\"; return ;;");
        shell_script.add("    esac");
        shell_script.add("  done");
        shell_script.add("  printf '%s' \"$PWD\"");
        shell_script.add("}");
        shell_script.add("PS1='$(__thothterm_prompt_path) $ '");
        shell_script.add("export PS1");

        // Source application startup script
        shell_script.add("test -f ~/.shrc && . ~/.shrc");

        //Next work fine with mksh but fail with ash.
        //shell_script.add(". /proc/self/fd/0 <<< \"$(libexec-t1plus.so aliases)\"");
        shell_script.add(". /proc/self/fd/0 <<EOF");
        shell_script.add("$(" + APPINFO_COMMAND + " " + android.os.Process.myUid() + " aliases)");
        shell_script.add("EOF");

        if (!TextUtils.isEmpty(APPEXEC_COMMAND)) {
            shell_script.add("t1pcmd() {");
            shell_script.add(APPEXEC_COMMAND + " " + android.os.Process.myUid() + " ${1+\"$@\"}");
            shell_script.add("}");
        }

        boolean installed = install_text_file(
                shell_script.toArray(new String[0]), Application.getScriptFile());
        if (installed)
            ThothLog.d(LogCategory.INSTALLER, "Startup script updated");
        else
            ThothLog.w(LogCategory.INSTALLER, "Startup script update failed");
        return installed;
    }

    public static boolean copy_executable(File source, File target_path) {
        int buflen = 32 * 1024; // 32k
        byte[] buf = new byte[buflen];

        File target = new File(target_path, source.getName());
        File backup = new File(target.getAbsolutePath() + "-bak");
        if (target.exists())
            if (!target.renameTo(backup))
                return false;

        try {
            OutputStream os = FilesCompat.newOutputStream(target);
            InputStream is = FilesCompat.newInputStream(source);
            int len;
            while ((len = is.read(buf, 0, buflen)) > 0) {
                os.write(buf, 0, len);
            }
            os.close();
            is.close();

            if (backup.exists())
                backup.delete();

            // always preset executable permissions
            return target.setReadable(true) &&
                    target.setExecutable(true, false);
        } catch (Exception ignore) {
        }
        return false;
    }

    public static boolean install_asset(AssetManager am, String asset, File target) {
        int buflen = 32 * 1024; // 32k
        byte[] buf = new byte[buflen];

        try {
            OutputStream os = FilesCompat.newOutputStream(target);
            InputStream is = am.open(asset, AssetManager.ACCESS_STREAMING);
            int len;
            while ((len = is.read(buf, 0, buflen)) > 0) {
                os.write(buf, 0, len);
            }
            is.close();
            os.close();

            return true;
        } catch (IOException ignore) {
        }
        return false;
    }
}
