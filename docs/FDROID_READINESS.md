# F-Droid readiness assessment — ThothTerm Ubuntu

Status for the first public release: **not submittable to f-droid.org's main
repository as currently built.** The reasons are structural, not cosmetic, and
are listed below with remediation options. Everything else F-Droid checks —
licensing, trackers, pinned dependencies, reproducibility of the build itself —
is already in good shape.

This assessment was written against F-Droid's current published Inclusion Policy,
not from memory.

---

## 1. What already passes

| Requirement | Status | Evidence |
|---|---|---|
| FOSS license for the app | Pass | Apache-2.0, `LICENSE` + `NOTICE` |
| Upstream lineage attributed | Pass | `NOTICE` records TermOne Plus → Terminal Emulator for Android → AOSP |
| No trackers or analytics | Pass | No Firebase/Crashlytics/GMS/analytics; no `google-services.json` |
| No proprietary dependency | Pass | Only AndroidX + Material, all Apache-2.0 |
| No dynamic dependency versions | Pass | All pinned in `gradle/libs.versions.toml` |
| Gradle wrapper pinned by checksum | Pass | `distributionSha256Sum` in `version.gradle` |
| Build inputs pinned by SHA-256 | Pass | `term-ubuntu/tools/prepare-assets.sh`, fails closed |
| Build is deterministic on one host | Pass | Two clean builds → byte-identical APK |
| No secrets in the repository | Pass | No keystores, tokens or API keys committed |
| Source-based build | Pass for app code | Gradle builds the app from source |

---

## 2. Former blockers — all resolved

F-Droid's Inclusion Policy requires that binaries come from source compilation
or a small set of trusted sources, and that an app not download executable
binaries without explicit user consent. Three things failed that test. All three
are now closed.

### B1 — prebuilt PRoot from a GitHub release — RESOLVED

The runtime is now **compiled during the build** from sources in this
repository. `third_party/proot` and `third_party/libandroid-shmem` are git
submodules pinned to audited commits; talloc has no git upstream, so the two
files PRoot needs are vendored verbatim from the release tarball with its sha256
recorded. `term-ubuntu/tools/build-proot.sh` verifies both submodule commits,
downloads nothing, applies one documented patch, and asserts the link contract
and 16 KB alignment of what it produced.

The old `ProotX-Assets-Support` download is gone from the build entirely, and
`libandroid-selinux.so` — which the binary we build does not reference — is no
longer shipped.

Verified on the device against the already-proven rootfs: bash/dash/sh, sudo,
apt, dpkg over 728 packages, DNS, TLS, user PATH, and the semantic
`link2symlink` test (create, link, `nlink` = 2, execute, unlink the original,
execute again).

### B2 — Ubuntu rootfs embedded in the APK — RESOLVED for the F-Droid flavour

The build now has two flavours. `full` keeps the embedded userland for offline
installation and is unchanged in behaviour. `fdroid` ships **no distribution
payload at all** and is 6.8 MB against 42 MB.

Instead it asks. Before anything is fetched, the first-run screen states that
executable software will be downloaded after installation, that it comes from
Canonical rather than ThothTerm, that it is not part of the app package and was
not built or verified by F-Droid, and that ThothTerm checks it against a SHA-256
fixed in this app's source. "Not now" and "Download Ubuntu" are equal-width
buttons; declining leaves a working screen with the download still one tap away.

The URL, size and digest come from the same pinned `image.properties` the full
flavour is built against, so both flavours install byte-identical userlands. The
archive is written to a `.part` file and promoted only once the digest matches,
a cached archive that stops verifying is deleted, and redirects are followed by
hand so a downgrade to plain HTTP is refused.

### B3 — embedded sudo `.deb` payloads — RESOLVED for the F-Droid flavour

The fdroid flavour installs sudo from Ubuntu's own archive during the consented
bootstrap, so apt verifies it with the distribution's signing keys and resolves
the dependency closure itself — a stronger guarantee than a checksum we pin
ourselves. The full flavour keeps its offline packages.

## 3. Secondary review points

These are not blockers but a reviewer will raise them.

- **Build-time network access.** The `fdroid` flavour's build downloads
  **nothing**: `prepare-assets.sh` runs only for `full` variants, and the PRoot
  build works purely from in-repo sources. A reviewer can confirm this by
  building `assembleFdroidRelease` with no network.
- **Copyleft source offer.** Closed. The corresponding source for the GPL-2.0
  PRoot binary is the pinned submodule, the patch in `term-ubuntu/patches/` and
  the build script, all carried by the release tag, so any distributed binary
  maps to an exact, immutable source state. See §4 of
  `THIRD_PARTY_NOTICES.md`.
- **arm64 only.** `abiFilters 'arm64-v8a'`. Legitimate, but should be stated in
  the description so users on other ABIs are not surprised.
- **APK size.** 43 MB, dominated by the rootfs. Not a policy violation.
- **`allowBackup="false"`.** Intentional, and relied upon by the
  notification-permission record.

---

## 4. Verdict

- **Ready for a public release: yes.**
- **Ready for an fdroiddata merge request: yes**, for the `fdroid` flavour built
  from the release tag.

Remaining reviewer-facing points are in §3; none of them is a policy violation.
The consented download is the one item a reviewer will want to look at directly,
and `docs/fdroid/com.thothterm.ubuntu.yml` carries a note pointing at it.
