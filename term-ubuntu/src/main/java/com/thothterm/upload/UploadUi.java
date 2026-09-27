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

import android.app.Activity;
import android.content.res.Resources;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

import com.thothterm.R;
import com.thothterm.widget.ScreenMessage;

/**
 * The phone's upload dialogs, redrawn from {@link LocalUpload}'s state: while
 * the selection is read, the target to confirm ("Upload to: ..."), progress
 * with Cancel, then the outcome. The upload itself lives on without the
 * activity; a recreated activity picks the dialog up again.
 */
@RequiresApi(21)
public final class UploadUi implements LocalUpload.Listener {
    private final Activity activity;
    private AlertDialog dialog;
    private LocalUpload.Phase shown;
    private TextView line1;
    private TextView line2;
    private ProgressBar bar;

    public UploadUi(Activity activity) {
        this.activity = activity;
    }

    /** From onResume. */
    public void attach() {
        LocalUpload.addListener(this);
        LocalUpload upload = LocalUpload.current();
        if (upload != null) onUploadChanged(upload);
    }

    /** From onPause: the dialog goes; the upload does not. */
    public void detach() {
        LocalUpload.removeListener(this);
        dismiss();
    }

    @Override
    public void onUploadChanged(LocalUpload upload) {
        if (activity.isFinishing() || upload != LocalUpload.current()) return;
        LocalUpload.Phase phase = upload.phase();
        switch (phase) {
            case PREPARING:
                if (shown != phase) showProgress(upload, true);
                line1.setText(R.string.upload_preparing);
                line2.setText("");
                break;
            case CONFIRM:
                if (shown != phase) showConfirm(upload);
                break;
            case RUNNING:
                if (shown != phase) showProgress(upload, false);
                updateProgress(upload);
                break;
            case DONE:
                dismiss();
                ScreenMessage.show(activity, doneMessage(upload));
                upload.acknowledge();
                break;
            case CANCELLED:
                dismiss();
                ScreenMessage.show(activity, activity.getString(R.string.upload_cancelled));
                upload.acknowledge();
                break;
            case FAILED:
                if (shown != phase) showFailure(upload);
                break;
        }
    }

    private void showConfirm(LocalUpload upload) {
        dismiss();
        Resources r = activity.getResources();
        String size = upload.totalBytes() < 0 ? "" : " (" + bytes(upload.totalBytes()) + ")";
        String what = upload.isFolder()
                ? r.getString(R.string.upload_confirm_folder, name(upload.folderName()),
                r.getQuantityString(R.plurals.upload_file_count, upload.itemCount(), upload.itemCount()) + size)
                : r.getQuantityString(R.plurals.upload_file_count, upload.itemCount(), upload.itemCount()) + size;
        String message = r.getString(R.string.upload_confirm_message, what, path(upload.target().displayPath));
        dialog = new AlertDialog.Builder(activity)
                .setTitle(upload.isFolder() ? R.string.upload_folder : R.string.upload_files)
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> upload.cancel())
                .setPositiveButton(R.string.upload_start, (d, w) -> upload.start())
                .show();
        shown = LocalUpload.Phase.CONFIRM;
    }

    private void showProgress(LocalUpload upload, boolean indeterminate) {
        dismiss();
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                activity.getResources().getDisplayMetrics());
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        line1 = new TextView(activity);
        line2 = new TextView(activity);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(indeterminate);
        bar.setMax(1000);
        box.addView(line1);
        box.addView(bar);
        box.addView(line2);
        String title = upload.target() == null ? activity.getString(R.string.upload_preparing)
                : activity.getString(R.string.upload_progress_title, path(upload.target().displayPath));
        dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> upload.cancel())
                .show();
        shown = indeterminate ? LocalUpload.Phase.PREPARING : LocalUpload.Phase.RUNNING;
    }

    private void updateProgress(LocalUpload upload) {
        String current = upload.currentName();
        line1.setText(current == null ? "" : name(current));
        long done = upload.bytesDone();
        long total = upload.totalBytes();
        String files = activity.getString(R.string.upload_progress_files,
                Math.min(upload.filesDone() + 1, Math.max(upload.itemCount(), 1)), upload.itemCount());
        if (total > 0) {
            int percent = (int) Math.min(100, done * 100 / total);
            bar.setIndeterminate(false);
            bar.setProgress((int) Math.min(1000, done * 1000 / total));
            line2.setText(activity.getString(R.string.upload_progress_bytes_of, bytes(done), bytes(total), percent)
                    + " · " + files);
        } else {
            bar.setIndeterminate(true);
            line2.setText(bytes(done) + " · " + files);
        }
    }

    private void showFailure(LocalUpload upload) {
        dismiss();
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.upload_failed_title)
                .setMessage(errorMessage(upload))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (d, w) -> upload.acknowledge())
                .show();
        shown = LocalUpload.Phase.FAILED;
    }

    private String doneMessage(LocalUpload upload) {
        String where = path(upload.target().displayPath);
        if (upload.isFolder()) {
            return activity.getString(R.string.upload_done_folder, name(upload.resultName()), where);
        }
        return activity.getResources().getQuantityString(R.plurals.upload_done_files,
                upload.filesDone(), upload.filesDone(), where);
    }

    private String errorMessage(LocalUpload upload) {
        UploadError.Code code = upload.error();
        if (code == null) code = UploadError.Code.IO;
        switch (code) {
            case NO_DIRECTORY:
                return activity.getString(R.string.upload_error_no_directory);
            case OUTSIDE:
                return activity.getString(R.string.upload_error_outside);
            case DIRECTORY_GONE:
                return activity.getString(R.string.upload_error_directory_gone);
            case NOT_WRITABLE:
                return activity.getString(R.string.upload_error_not_writable);
            case NO_SPACE:
                return activity.getString(R.string.upload_error_no_space);
            case BAD_NAME:
                return upload.badName() == null ? activity.getString(R.string.upload_error_bad_name)
                        : activity.getString(R.string.upload_error_bad_name_named, name(upload.badName()));
            case CONFLICT:
                return activity.getString(R.string.upload_error_conflict);
            default:
                return activity.getString(R.string.upload_error_io);
        }
    }

    private void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        shown = null;
    }

    private String bytes(long n) {
        return Formatter.formatShortFileSize(activity, n);
    }

    /** A path reads left to right whatever the names in it. */
    private static String path(String path) {
        return BidiFormatter.getInstance().unicodeWrap(visible(path), TextDirectionHeuristicsCompat.LTR);
    }

    /** A name keeps its own direction and cannot reorder the text around it. */
    private static String name(String name) {
        return BidiFormatter.getInstance().unicodeWrap(visible(name));
    }

    /** Control characters of a refused name are shown, never interpreted. */
    private static String visible(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            b.append(c < 0x20 || (c >= 0x7f && c <= 0x9f) ? '�' : c);
        }
        return b.toString();
    }
}
