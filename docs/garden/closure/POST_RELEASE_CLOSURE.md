# Garden post-release closure (2026-10-03)

| | |
|---|---|
| ThothTerm Ubuntu 0.3.1 (301) | released — tag `ubuntu-v0.3.1` → `54a965f`, GitHub release |
| ThothTerm Trixie 0.2.1 (201) | released — tag `trixie-v0.2.1` → `54a965f`, GitHub release |
| ThothTerm Rolling 0.1.0 (100) | released — tag `arch-v0.1.0` → `54a965f`, GitHub release; rootfs release `arch-rootfs-aarch64-03a4c669ed6f` with 120 source archives |
| F-Droid | fdroiddata !49556 (Ubuntu), !50342 (Trixie), !51045 (Rolling): latest Build only, all pipelines green, squash on, auto-merge off |
| Reviewer reply | posted on !49556 (note 3949190509) |
| Kernel 4.14 / crDroid | **UNVERIFIED**: the fork-child fix (PRoot 0007) is covered by host tests and a device run on a kernel that reports the pid; the reviewer's Redmi Note 10 Pro / crDroid 12.11 / 4.14.357 needs their retest |
| Upstream F-Droid | no merge request was merged by us |
| Historical tags | `terminal-v1.4.0`, `ubuntu-v0.3.0`, `trixie-v0.2.0` unchanged (tag objects 87b0c807, 78c657f8, b47a102d) |

Full evidence: `CLOSURE_REPORT.md`, `evidence/`, `device-evidence/`.

## This branch

`integration/garden-closure-into-master` = master (`c627393`, regular terminal 1.4.0) +
the Garden closure branch (`66f9d30`). `term/` is byte-identical to master's, so the
regular terminal is exactly the released 1.4.0 tree. Shared modules (`emulatorview`,
`libtermexec`, root build files) were merged: Garden's control-string handling and the
16 KB `common-page-size` link flag now also apply to the regular terminal's next build.
The regular terminal's lint baseline is unchanged (1 error, `GestureBackNavigation`).
