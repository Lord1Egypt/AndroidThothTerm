# ThothTerm diagnostics and structured logging

Status: implemented on `feature/thotterm-linux`, first post-golden-baseline phase.
The golden baseline `terminal-v1.0.0` is untouched.

This document describes the internal diagnostics system added before the future
Ubuntu/PRoot work. It is the reference for anyone instrumenting ThothTerm —
including the future `com.thothterm.linux` runtime code.

## Goals

- A single logging API used by all application code.
- A user-visible `Settings → Diagnostics → Logs` screen.
- Bounded, app-private, rotating persistence with asynchronous writes.
- Hard privacy rules: logs describe system behaviour, never user secrets.
- Logging never slows the terminal, and logging failures never crash it.

## Architecture

All classes live in `term/src/main/java/com/thothterm/logging/`.

| Class | Responsibility |
| --- | --- |
| `ThothLog` | Public facade and process-wide manager: API, level filter, buffer, queue, export, clear. |
| `LogLevel` | `ERROR`, `WARN`, `INFO`, `DEBUG`, `VERBOSE` with severity `rank`. |
| `LogCategory` | Structured category names plus the reserved Linux-era names. |
| `LogEntry` | Immutable record: timestamp, level, category, message, precomputed time text. |
| `LogBuffer` | Thread-safe bounded ring buffer (4000 records) for the live viewer. |
| `LogFileStore` | Rotating plain-text persistence in app-private storage. |
| `LogExporter` | Storage Access Framework export, off the main thread. |
| `Formats` | Locale-independent timestamp formatting (`ThreadLocal` formatters). |

`ThothLog` is initialized once, first thing in `com.thothterm.Application.onCreate()`,
before any other application code runs.

### Write path

1. A caller invokes `ThothLog.i(category, message)` (or `d/w/e/v`).
2. The active threshold is checked; records below it are dropped immediately.
3. The message is normalized (single line, control characters removed, capped at
   2048 characters) and mirrored to Android Logcat.
4. The record is appended to the in-memory ring buffer (cheap).
5. The record is offered to a bounded `LinkedBlockingQueue` (4096).
6. A single daemon writer thread (`ThothLog-writer`, `MIN_PRIORITY`) drains the
   queue in batches and appends them to disk.

No disk I/O happens on the calling thread. If the queue ever fills, records are
dropped and counted; logging never blocks the caller. Persistent-log levels
(`DEBUG`, `VERBOSE`) never touch the terminal renderer, which remains completely
independent of the logger.

## Public API

```java
ThothLog.i(LogCategory.SESSION, "Session created");
ThothLog.d(LogCategory.PTY, "PTY opened");
ThothLog.w(LogCategory.PTY, "PTY resize failed: " + e.getMessage());
ThothLog.e(LogCategory.SHELL, "Shell executable not found: " + name);
ThothLog.e(LogCategory.SESSION, "Shortcut request rejected", throwable);

if (ThothLog.isEnabled(LogLevel.DEBUG)) {
    ThothLog.d(LogCategory.PTY, "expensive detail " + expensiveValue());
}
```

Read/management helpers: `ThothLog.snapshot()`, `getChangeCount()`, `getDroppedCount()`,
`getLevelName()`, `isDeveloperLoggingEnabled()`, `flush()`, `clear()`,
`exportTo(OutputStream)`, `suggestedExportName()`, `onPreferenceChanged(key)`.

`ThothLog.e(category, message, throwable)` records the exception type and message
(not a stack trace or user payload).

## Log levels

| Level | Rank | Meaning |
| --- | --- | --- |
| `ERROR` | 0 | Operation failed. |
| `WARN` | 1 | Recoverable problem, or a non-zero process exit. |
| `INFO` | 2 | Normal lifecycle events. **Default.** |
| `DEBUG` | 3 | Development detail (PTY resize, session switch, activity lifecycle). |
| `VERBOSE` | 4 | Reserved for high-frequency deep detail. |

The default level is `INFO`. Developer logging is **off** by default; when it is
off, `DEBUG`/`VERBOSE` records are suppressed even if the level is set higher.
Both settings are read at startup and re-applied immediately when changed, with
no reinstall or restart required.

## Categories

Active today:

`APP`, `UI`, `SESSION`, `PTY`, `SHELL`, `INSTALLER`, `STORAGE`, `NETWORK`

Reserved for the future Linux edition (no events are emitted yet):

`RUNTIME`, `LINUX`, `ROOTFS`, `PROOT`, `WEB`, `SECURITY`

## Persistence and rotation

- Location: `<app files dir>/logs/` — application-private storage only. No
  external storage, no `FileProvider`, no world-readable path.
- Files: `thotterm.log`, `thotterm.1.log`, `thotterm.2.log`, `thotterm.3.log`.
- Limits: 4 MB per file, 4 files, about **16 MB total** on disk.
- Rotation: the newest file is `thotterm.log`. When it reaches 4 MB the files
  shift (`thotterm.log → thotterm.1.log`, and so on) and the oldest
  (`thotterm.3.log`) is deleted.
- Clearing logs deletes every persisted file, empties the live buffer, and
  discards queued records so nothing is written back.
- All file operations tolerate failure; a full disk or a missing directory can
  never crash the terminal.

## Privacy and redaction rules

The logger must **never** receive:

- terminal keyboard input, command text, or terminal output;
- passwords, API keys, authentication tokens, SSH credentials, private keys;
- environment variable values, clipboard contents, Web Terminal access codes;
- filesystem file contents.

Log system behaviour instead:

```
GOOD  SHELL:    process started pid=1234
BAD   SHELL:    user typed export API_KEY=abcdef

GOOD  NETWORK:  Web client authenticated
BAD   NETWORK:  access token=928374...
```

Every message is forced to a single line and stripped of control characters so
multi-line command text cannot leak through a format mistake. Full `Intent`
extras, environment maps, and command lines are not logged.

## Export

Export uses the Android Storage Access Framework
(`ActivityResultContracts.CreateDocument("text/plain")`). The user chooses the
destination; ThothTerm never exposes an internal file path or content URI. The
exported file is human-readable text with a small, non-sensitive header:

```
ThothTerm diagnostics log
----------------------------------------
App version: 1.0.0 (10000)
Android: <release> (API <n>)
Device architecture: <abi>
App flavor: <flavor>-<buildType>
Log level: INFO (developer logging: off)
Exported: yyyy-MM-dd HH:mm:ss.SSS
----------------------------------------
```

No device model, serial, account, or advertising identifier is included.
Export reads the persisted files (oldest first), so it survives restarts; if no
files exist it falls back to the live buffer. Export runs off the main thread.

## Settings and UI

`Settings → Diagnostics` provides:

- **View logs** — opens the in-app Logs screen.
- **Log level** — `ERROR`/`WARN`/`INFO`/`DEBUG`/`VERBOSE`; applies immediately.
- **Developer logging** — off by default; gates `DEBUG`/`VERBOSE`.
- **Clear logs** — confirmed, removes persisted and in-memory records.
- **Export logs** — SAF text export.

The **Logs** screen (`com.thothterm.LogsActivity`) shows a live list with
timestamp, level, category, and message; search; level and category filters;
pause/resume; auto-scroll; clear; and export. It refreshes on a 750 ms poll that
only rebuilds when the buffer changed, so it stays responsive with thousands of
records. Row content is monospace and level-coloured, and follows the
Dark/Light/System/AMOLED app themes.

## Instrumented events (terminal edition)

| Category | Events now recorded |
| --- | --- |
| `APP` | application start (version/flavor), terminal service start/stop. |
| `UI` | Term activity created/resumed/stopped, theme mode change, new-window before service ready. |
| `SESSION` | session created/closed with active count, session switched, service bind/unbind, service timeout, script/shortcut request rejected. |
| `PTY` | PTY opened, resize (rows/cols only), closed, resize/UTF-8 failure. |
| `SHELL` | shell process started (pid), executable name, shell exited (exit code), executable missing. |
| `INSTALLER` | bootstrap start/complete, startup script update/failure. |
| `STORAGE` | private directory initialization/ready. |
| `NETWORK` | none today; reserved for the Web Terminal phase. |

## HOW FUTURE LINUX CODE MUST LOG

The Ubuntu/PRoot/runtime work must use this same system. Do not add another
logger and do not call `android.util.Log` directly from application code.

Use the reserved categories so the Logs screen and filters keep working:

```java
// Embedded runtime lifecycle
ThothLog.i(LogCategory.RUNTIME, "Runtime start rootfs=" + rootfsId);
ThothLog.w(LogCategory.RUNTIME, "Runtime start failed: " + reason);

// Proot / rootfs extraction
ThothLog.i(LogCategory.PROOT,   "PRoot launcher started pid=" + pid);
ThothLog.i(LogCategory.ROOTFS,  "Rootfs extraction started");
ThothLog.i(LogCategory.ROOTFS,  "Rootfs extraction complete bytes=" + total);
ThothLog.e(LogCategory.ROOTFS,  "Rootfs extraction failed", throwable);

// Distro / Linux session layer
ThothLog.i(LogCategory.LINUX,   "Linux session created");
ThothLog.i(LogCategory.LINUX,   "Linux session closed code=" + exitCode);

// Web Terminal / LAN server
ThothLog.i(LogCategory.WEB,     "Web terminal server started port=" + port);
ThothLog.w(LogCategory.WEB,     "Web client authentication failed");

// Security-relevant decisions (never the secret itself)
ThothLog.i(LogCategory.SECURITY,"Access code rotated");
ThothLog.w(LogCategory.SECURITY,"Unauthorized session request rejected");
```

Rules for the Linux phase:

- Log identifiers, counts, ports, exit codes, and error types — never payloads.
- Never log rootfs file contents, shell commands, tokens, access codes, or keys.
- Keep high-frequency runtime detail at `DEBUG`/`VERBOSE`.
- Add no new storage location; the existing rotation and export keep covering logs.
