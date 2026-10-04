# Release signing and reproducible builds (F-Droid `Binaries`)

F-Droid can publish **our** signed APK instead of signing with its own key,
if its build of the tagged source reproduces our APK byte for byte apart from
the signature (`Binaries` + `AllowedAPKSigningKeys` in the recipe). That
choice is permanent per app: an app F-Droid has signed with its key cannot
switch later.

## Status

| App | Reproducible across checkouts and machines | Upstream release key |
|---|---|---|
| ThothTerm Rolling (`garden-arch`) | **Yes** (2026-10-04, see below) | not created yet — key ceremony pending |
| ThothTerm Trixie, Resolute | same native/Java build as Rolling; not compared on the buildserver yet | — |

## Evidence (Rolling, 2026-10-04)

Before the fix (`arch-v0.1.0`, 54a965f), three entries differed:

- `assets/runtime/arm64-v8a/libtalloc.so.2` — talloc's `__location__`
  (`__FILE__`) held the absolute checkout path;
- `lib/arm64-v8a/libterm-system.so` — only the GNU build-id, hashed by the
  linker over debug info that names the checkout and NDK paths;
- `lib/arm64-v8a/libproot.so` — PRoot embedded `git describe` of this
  repository (`-dirty` in fdroidserver's edited checkout).

Fixed by `-ffile-prefix-map=<repo>=.` in `build-proot.sh`, `--build-id=none`
for the CMake libraries, and `make GIT=false` for PRoot. At 98e63f7 (0.1.1):

- two host checkouts in different directories: identical APKs;
- the fdroiddata CI job (`fdroidserver:buildserver-trixie`, fdroidserver
  master, JDK 21, `fdroid build --on-server`) against a host build (JDK 21,
  NDK 23.2.8568313): **byte-identical** unsigned APK,
  SHA-256 `b53492a6cfda85d48661974140fc7baee309ae6004a5627d46fe4611fe61c8af`;
- a copy signed with a throwaway key as below passed fdroidserver's own
  `common.verify_apks()` against the buildserver's unsigned APK (MATCH),
  with v1 on and with v1 off. The throwaway key was deleted.

## Building a release APK for `Binaries`

From a clean clone of the tag, with submodules, JDK 21 and NDK 23.2.8568313:

    ./gradlew --no-build-cache :garden-arch:assembleFdroidRelease
    apksigner sign --alignment-preserved true --v1-signing-enabled false \
        --ks <release keystore> --ks-key-alias <alias> \
        --out ThothTerm-Rolling-v<version>-fdroid-release.apk \
        garden-arch/build/outputs/apk/fdroid/release/garden-arch-fdroid-release-unsigned.apk

`--alignment-preserved true` is required: without it apksigner re-aligns every
entry and the signature no longer fits F-Droid's build (measured: v3 digest
mismatch). Upload that APK to the GitHub release of the tag, then:

    Binaries: https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/arch-v%v/ThothTerm-Rolling-v%v-fdroid-release.apk
    AllowedAPKSigningKeys: <certificate SHA-256, lower-case hex, no colons>

`apksigner verify --print-certs` prints the certificate SHA-256.

## Key ceremony (owner)

The release key identifies the app for as long as it exists; losing it means
no updates through F-Droid, leaking it means anyone can publish updates. It is
never created by an automated session, never committed, never stored in the
repository's directory tree, and its password is never written to a file in
the repository or a log.

1. On a trusted machine: `keytool -genkeypair -keystore <outside the repo>/thothterm-release.p12
   -storetype PKCS12 -alias thothterm -keyalg RSA -keysize 4096 -validity 10000
   -dname "CN=ThothTerm"`; choose a strong password in a password manager.
2. Make two offline backups of the keystore (and the password), stored apart.
3. Record the certificate SHA-256 (`keytool -list -v` or
   `apksigner verify --print-certs` on a signed APK) in this file.
4. Decide before the first `Binaries` upload whether each app gets its own
   key; F-Droid accepts only the fingerprints listed for that app.
