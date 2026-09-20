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

## 2. Blockers

F-Droid's Inclusion Policy requires that *"all binary dependencies including JAR
files must originate either from source compilation or Debian repository
downloads"*, and permits prebuilt FLOSS binaries only from a short list of
trusted sources (Debian main, trusted Maven repositories, the official Android
and Flutter SDKs, and a few language toolchains). It also rules out downloading
additional executable binaries without explicit user consent.

ThothTerm Ubuntu ships three classes of binary that do not meet that bar.

### B1 — PRoot runtime is a prebuilt binary from a GitHub release

`libproot.so`, `libproot_loader.so`, `libtalloc.so.2`, `libandroid-shmem.so` and
`libandroid-selinux.so` are fetched at build time from
`github.com/Lord1Egypt/ProotX-Assets-Support` release `v1.2.0` and embedded.

Mitigating facts: the bundle is SHA-256 pinned, and its `THIRD_PARTY_NOTICES.md`
records that the "modern" lane is **source-built** from pinned upstreams —
`termux/proot` tag `v5.1.107.92` (GPL-2.0), talloc 2.4.3, `termux/libandroid-shmem`
v0.7, termux-packages `libandroid-selinux` 14.0.0.11-1 — by a builder that pins
the image digest and produces deterministic archives.

That is good provenance, but it is still a prebuilt binary from a GitHub release
rather than something F-Droid compiled.

**Remediation:** have the F-Droid recipe build PRoot and its loader from the
pinned upstream source as part of the build, instead of downloading the bundle.
PRoot is C and buildable with the NDK, so this is tractable; it is the single
highest-value change for F-Droid eligibility.

### B2 — The Ubuntu base rootfs is embedded in the APK

`ubuntu-base-26.04.1-base-arm64.tgz` is 35 MB of prebuilt Ubuntu executables,
downloaded from `cdimage.ubuntu.com` at build time and packaged verbatim. It is
the bulk of the 43 MB APK.

Ubuntu's archive is not Debian's main archive, and in any case F-Droid does not
contemplate shipping an entire distribution inside an APK.

**Remediation options:**

1. **Download at runtime with explicit consent** and SHA-256 verification — the
   pattern comparable projects use to remain listable. This also cuts the APK to
   roughly 8 MB. F-Droid may still apply an anti-feature label for fetching
   binaries it did not build; that needs confirming with F-Droid rather than
   assuming.
2. **Ship outside f-droid.org main** — a self-hosted F-Droid repository, or
   IzzyOnDroid, both of which accept this shape of app.
3. Keep bundling and accept that f-droid.org main is not the distribution
   channel.

Option 1 is a product decision with real UX consequences (first run needs
network), which is why it is **not** being made unilaterally in this pass.

### B3 — Ubuntu `.deb` admin packages are embedded

`sudo`, `sudo-common` and `libapparmor1` are fetched from `ports.ubuntu.com` and
embedded for offline first-run provisioning. Same category as B2, much smaller
(≈1 MB total). If B2 moves to runtime download, these travel with it.

---

## 3. Secondary review points

These are not blockers but a reviewer will raise them.

- **Build-time network access.** `prepare-assets.sh` downloads from
  cdimage.ubuntu.com, ports.ubuntu.com and GitHub. An F-Droid recipe must either
  perform these in a `prebuild`/`sudo` step or vendor the inputs. Every download
  is SHA-256 pinned and fails closed, which is the right shape, but the network
  access itself still has to be declared.
- **Copyleft source offer.** PRoot is GPL-2.0 and talloc is GPL-3.0, and the
  rootfs contains GPL/LGPL packages. Upstream tags are recorded in
  `THIRD_PARTY_NOTICES.md`, but the release should also carry a written offer or
  a source mirror. Currently **open**.
- **arm64 only.** `abiFilters 'arm64-v8a'`. Legitimate, but should be stated in
  the description so users on other ABIs are not surprised.
- **APK size.** 43 MB, dominated by the rootfs. Not a policy violation.
- **`allowBackup="false"`.** Intentional, and relied upon by the
  notification-permission record.

---

## 4. Verdict

- **Ready for initial public release (e.g. a GitHub release): yes**, subject to
  the copyleft source offer above.
- **Ready for f-droid.org main repository: no**, until B1 is resolved and a
  decision is taken on B2/B3.

The prepared metadata under `fastlane/` and `docs/fdroid/` is complete and
correct, so once B1/B2 are settled the submission itself is mechanical.
