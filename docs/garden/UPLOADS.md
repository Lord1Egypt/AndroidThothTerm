# Uploads into the terminal's current directory

ThothTerm can copy files, or a whole folder, into the **current working
directory of one terminal session** — from the phone (all editions) and, in
Garden editions with LAN Mode on, from a paired browser. There is no download,
file browser, delete, rename or archive handling: uploads only add.

Code: `com.thothterm.upload` (identical in `term/`, `term-ubuntu/` and
`garden-common/`); the browser side is `com.thothterm.lan.LanUploads`, the
`/api/upload/*` routes in `LanServer` and `assets/lan/app.js`.

## Which directory

A session's directory is the working directory of its PTY's **foreground
process group**, read from `/proc`:

1. The app knows the session leader's pid — it started it with `setsid()` on
   the session's own PTY. For a phone window that is the window's session; for
   a browser terminal it is that terminal's own PTY, found by the terminal id
   *and* the browser that owns it. A browser never sends a directory.
2. Field 8 of `/proc/<leader>/stat` is the PTY's foreground process group.
3. Its leader (or, if that exited, any member) — checked to belong to the same
   session, so a reused pid can never be taken — has its `/proc/<pid>/cwd` read.

At a prompt that is the shell; while `vim`, a subshell or `sudo -s` runs in
front, it is theirs. Nothing is typed into the shell, nothing is read from the
screen, no prompt or shell configuration is involved, and program output
cannot spoof it. Any shell works (mksh, bash, zsh, fish).

**Garden (PRoot).** PRoot used to emulate `chdir(2)` entirely, so the kernel
working directory of every guest process stayed where proot started.
`patches/0005-keep-the-kernel-working-directory-in-step-with-the-guest.patch`
lets the kernel change directory too, to the host path PRoot resolved, and
commits PRoot's own record only when the kernel succeeded; the first tracee
starts behind `--cwd`. `/proc/<pid>/cwd` of a guest process is therefore
`<rootfs><guest path>`, and the app shows the guest path. Directories outside
the rootfs — PRoot's `/proc`, `/dev`, `/sys` and resolver bindings — are
refused. PRoot itself is the session leader and is never the answer; a
terminal opened a moment ago whose bash has not taken the terminal yet is
looked at again for up to 5 s. `tests/proot-runtime/host-test.sh` checks the
patch natively (space, Unicode, symlink, failed `cd`, child `cd`, binding,
`cd -`, `fchdir`).

A symlinked directory resolves to its target: `cd link` then upload lands in
the directory `link` points to — the same place, named physically.

**Regular Terminal.** The shell is the session leader and runs as the app's
uid, so the same `/proc` read applies directly; kernel file systems (`/proc`,
`/sys`, `/dev`) are refused.

## When

The directory is fixed when an upload begins and shown before anything is
copied ("Upload to: /home/thoth/project"). A `cd` during the transfer does not
move it.

## Writing, and the guest's permissions

Files are written by the app as its own uid. This PRoot build has no
`USERLAND` ownership emulation: every file of the rootfs is owned by the app
uid on the host and shown as the current guest user, and the only permission
check is the kernel's, for that uid. The app's writes therefore get exactly
the checks `thoth` gets — a directory `thoth` cannot write to (`chmod 555`)
fails with "Cannot upload to this folder." — and the results look exactly
like `thoth`'s own files (`stat` shows `thoth`). Modes follow the session's
umask (`/proc/<pid>/status`), as a `cp` in that shell would. Nothing is ever
executed, sourced, made executable or extracted.

## Names

Every name is untrusted — a browser's, a document provider's. Each path
component is checked on its own (`UploadNames`): no empty, `.` or `..`
component, no `/` or `\`, no control characters, no unpaired surrogates, at
most 255 UTF-8 bytes, no drive specifier (`C:`), nothing that becomes `.`,
`..` or a separator when percent-decoded, however often, and not the staging
prefix. Browser paths are percent-encoded per component and decoded exactly
once, strictly. Names are never normalized: Arabic, NFD accents and CJK are
stored as given. At most 64 levels deep.

## Never partial, never overwriting, never following links

Each upload creates a hidden staging directory `.thothterm-upload-<random>`
inside the target (so on the same file system), recorded first in the app's
journal (`files/upload-staging`). A file is streamed into it through a 64 KiB
buffer, `fsync`ed, and only then moved to its final name. A folder is built
completely inside it and moved into place as a whole.

The move is `renameat2(RENAME_NOREPLACE)` (libtermexec, Android 11+; before
that, and on file systems without it, check-then-rename). If the name is
taken, the next free `name (1).ext`, `archive (1).tar.gz`, `project (1)` is
used: both are kept. Uploads never merge into an existing directory and never
open anything that already exists in the target, so a planted symlink
(`evil -> /elsewhere`) is never followed — the upload becomes `evil (1)`.

Cancel, failure, a dropped connection, sign-out, LAN Mode off, the terminal
ending, a browser that goes quiet for 2 minutes and Exit all remove the
staging directory. If the app is killed mid-upload, the journal removes it at
the next start. Files of a multi-file upload that had already finished stay,
complete.

Storage is checked before starting (total plus 16 MiB) and again per browser
file; running out midway (`ENOSPC`) fails cleanly with nothing left behind.

## Browser API

All four endpoints need the LAN Mode checks every request gets — exact `Host`,
exact `Origin`, the paired browser's bearer token (never in a URL) — and act
only on that browser's own terminals and uploads.

| Request | Body / headers | Answer |
|---|---|---|
| `POST /api/upload/begin` | `{term, kind: "files"\|"folder", name, bytes}` | `{upload, target}` — target read on the phone |
| `PUT /api/upload/file` | `X-ThothTerm-Upload`, `X-ThothTerm-Path` (encoded), `Content-Length`, the bytes | `{name}` — the final name |
| `POST /api/upload/finish` | `{upload}` | `{name}` — a folder's final name |
| `POST /api/upload/cancel` | `{upload}` | 204 |

A file's body is read only after every check has passed. Errors are
`{error: code}`: `unauthorized` 401, `forbidden` 403, `no_terminal` /
`no_upload` 404, `length_required` 411, `bad_name` 400, `no_space` 507,
`busy` / `not_writable` / `outside` / `no_directory` / `directory_gone` /
`conflict` 409, `cancelled` 410, `io` 500. One upload per terminal, eight at
once in all.

The page uses only browser APIs: `<input type=file multiple>`,
`<input type=file webkitdirectory>` and `XMLHttpRequest` (for upload
progress), sending each `File` straight from disk.

**Empty folders.** Browsers do not include empty directories in a folder pick,
so none arrive from a browser. The phone's Upload folder (the Storage Access
Framework lists directories) keeps them.

**Not encrypted.** LAN Mode is plain HTTP. Authentication decides who may
upload; it does not hide what is uploaded. The page and the phone say so.

## Logs

Upload logs carry the kind, byte and file counts, durations and error codes —
never file names, paths, contents, tokens or PINs.
