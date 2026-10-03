# Release process and version progression — ThothTerm Ubuntu

Applies to the `com.thothterm.ubuntu` application. The separate terminal edition
(`com.thothterm`) keeps its own `terminal-v*` tags and version line; the two must
not share version numbers.

---

## 1. Version scheme

`versionName` is `MAJOR.MINOR.PATCH`. `versionCode` is a plain integer derived
from it:

```
versionCode = MAJOR*10000 + MINOR*100 + PATCH
```

So `0.1.0` → `100`, `0.1.1` → `101`, `0.2.0` → `200`, `1.0.0` → `10000`.

The scheme is monotonic, leaves room for 99 patches per minor, and is readable
from the number alone. **A published `versionCode` is never reused**, even if a
release is withdrawn: withdraw it and increment.

Current state: `versionName 0.1.0`, `versionCode 100`, set in
`term-ubuntu/build.gradle`. Neither has been published, and no `*-ubuntu` tag
exists — `git tag` shows only `terminal-v1.0.0`, `terminal-v1.1.0`,
`terminal-v1.2.0` and `terminal-v1.2.1`, which belong to the other edition. So
`0.1.0` / `100` is free and appropriate for the first public release.

---

## 2. Planned progression

Pre-1.0 deliberately, because the Ubuntu edition is young and the first release
is a foundation rather than a feature-complete product.

| Line | Intent |
|---|---|
| `0.1.x` | Initial stabilisation. Bug fixes and small UX corrections only; no new subsystems. |
| `0.2.x` | First meaningful feature set after release. |
| `0.3.x` | Next feature set. |
| `1.0.0` | Declared only once the feature surface is settled, the F-Droid blockers in `docs/FDROID_READINESS.md` are resolved or consciously accepted, and the acceptance matrix passes for more than one distro. |

Features intentionally held back from `0.1.0`, so the app has visible ongoing
development: Reset Ubuntu / Reset Instance, public or LAN web terminal, browser
terminal access, and additional distro variants (Kali, Arch, AlmaLinux).

---

## 3. Tagging

Ubuntu-edition tags are `ubuntu-v<versionName>`, for example `ubuntu-v0.1.0`.
The prefix keeps them clearly apart from the terminal edition's `terminal-v*`
tags, and the F-Droid recipe's `UpdateCheckMode` matches
`Tags ^ubuntu-v[0-9.]+$`. Tags are annotated and are never moved once pushed:
an F-Droid build entry pins to the commit a tag resolves to, and the GPL
corresponding source for a distributed binary is identified by that same
commit.

---

## 4. Release checklist

1. Worktree clean; `git status --porcelain` empty.
2. `git diff --check` clean.
3. `./gradlew clean`, then a full build from the committed state.
4. Full unit test suite passes.
5. Instrumentation suite passes on a physical device.
6. The physical acceptance matrix in `docs/DISTRO_PORTING_CHECKLIST.md` passes.
7. `adb install -r` upgrade over the previous build; home directory, rootfs and
   preferences verified intact afterwards.
8. `THIRD_PARTY_NOTICES.md` matches the actual bundled artifacts, hashes
   included.
9. Changelog written to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
10. Both flavours build clean, and every arm64 ELF reports 16 KB alignment
    (`llvm-readelf -l`, `check_elf_alignment.sh`, `zipalign -c -P 16 -v 4`).
11. Record the release APK's size and SHA-256.
12. Tag, then publish.

Steps 11 and 12 require explicit authorisation; nothing is tagged or published
as part of ordinary engineering work.

---

## 5. Signing

No signing key is committed to this repository, and none should ever be. Debug
builds use the local debug keystore. Release signing material lives outside the
repository and is supplied at signing time.
