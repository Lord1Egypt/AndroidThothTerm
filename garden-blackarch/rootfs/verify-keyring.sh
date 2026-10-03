#!/bin/sh
#
# verify-keyring.sh INPUT_DIR [PINS]
#
# Independently verifies the BlackArch keyring package before build-rootfs.sh
# lets it near a root filesystem, and fails closed on anything unexpected.
# INPUT_DIR holds the package and its detached signature (file names from
# keyring.pins). PINS defaults to keyring.pins beside this script; the public
# keys it verifies with are blackarch.gpg beside the pins file.
#
#   1. the package and signature match the pinned size and SHA-256;
#   2. the signature is a valid OpenPGP signature by exactly KEYRING_SIGNER
#      (checked with gpgv against a keyring holding that one public key,
#      taken from the pinned blackarch.gpg);
#   3. the package carries exactly the pinned keyring files: blackarch.gpg is
#      the pinned blackarch.gpg, blackarch-trusted and blackarch-revoked,
#      the hook and the install script match their pinned digests;
#   4. the keys in blackarch.gpg are exactly the pinned set, the trusted list
#      is exactly the pinned one, every trusted and revoked key is in the
#      keyring, and a trusted key may not also be revoked.
# Keys that are present but neither trusted nor revoked are reported as
# NOTICE lines (the pins say which ones to expect; any other fails).
#
# Needs sh, sha256sum, gpg, gpgv, tar with zstd support (or bsdtar), awk. Exit 0 only when every
# check passes.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
INPUT=${1:?usage: verify-keyring.sh INPUT_DIR [PINS]}
PINS=${2:-$HERE/keyring.pins}
PINDIR=$(cd "$(dirname "$PINS")" && pwd)

fail() {
    echo "verify-keyring: FAIL: $*" >&2
    exit 1
}
sha() { sha256sum "$1" | cut -d' ' -f1; }

[ -r "$PINS" ] || fail "no pins file $PINS"
# shellcheck disable=SC1090
. "$PINS"
for v in KEYRING_FILE KEYRING_SIZE KEYRING_SHA256 KEYRING_SIG_SHA256 KEYRING_SIGNER KEYRING_GPG_SHA256 \
        KEYRING_TRUSTED_SHA256 KEYRING_REVOKED_SHA256 KEYRING_HOOK_SHA256 KEYRING_INSTALL_SHA256 \
        KEYRING_TRUSTED KEYRING_REVOKED KEYRING_ALL_KEYS; do
    eval "[ -n \"\${$v:-}\" ]" || fail "$v is not pinned"
done
case "$KEYRING_FILE" in */*|'') fail "KEYRING_FILE must be a plain file name" ;; esac

PKG=$INPUT/$KEYRING_FILE
SIG=$PKG.sig
[ -f "$PKG" ] || fail "no package $PKG"
[ -f "$SIG" ] || fail "no detached signature $SIG"

# 1. the bytes
[ "$(stat -c %s "$PKG")" = "$KEYRING_SIZE" ] || fail "package size $(stat -c %s "$PKG"), pinned $KEYRING_SIZE"
[ "$(sha "$PKG")" = "$KEYRING_SHA256" ] || fail "package SHA-256 $(sha "$PKG") differs from the pin"
[ "$(sha "$SIG")" = "$KEYRING_SIG_SHA256" ] || fail "signature SHA-256 $(sha "$SIG") differs from the pin"
[ "$(sha "$PINDIR/blackarch.gpg")" = "$KEYRING_GPG_SHA256" ] || fail "the pinned public keys file blackarch.gpg was changed"

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
chmod 700 "$T"

# 2. the signature: gpgv, one public key, exact signer
GNUPGHOME=$T/gnupg
export GNUPGHOME
mkdir -m 700 "$GNUPGHOME"
gpg -q --batch --import "$PINDIR/blackarch.gpg" 2>/dev/null
gpg --batch --export "$KEYRING_SIGNER" > "$T/signer.gpg" 2>/dev/null
[ -s "$T/signer.gpg" ] || fail "the signer $KEYRING_SIGNER is not in blackarch.gpg"
gpgv --keyring "$T/signer.gpg" --status-fd 1 "$SIG" "$PKG" > "$T/status" 2>"$T/gpgv.err" \
    || { cat "$T/gpgv.err" >&2; fail "the signature does not verify with $KEYRING_SIGNER"; }
valid=$(grep '^\[GNUPG:\] VALIDSIG ' "$T/status" || true)
[ "$(printf '%s\n' "$valid" | grep -c .)" = 1 ] || fail "expected exactly one valid signature"
signing=$(printf '%s' "$valid" | cut -d' ' -f3)
primary=$(printf '%s' "$valid" | awk '{print $NF}')
[ "$primary" = "$KEYRING_SIGNER" ] || fail "signed by $primary, not by $KEYRING_SIGNER"
case " $KEYRING_ALL_KEYS " in *" $primary "*) ;; *) fail "signer $primary is not a pinned keyring key" ;; esac

# 3. the package contents
mkdir "$T/pkg"
if tar --zstd -xf "$PKG" -C "$T/pkg" 2>/dev/null; then :; elif command -v bsdtar >/dev/null 2>&1; then
    bsdtar -xf "$PKG" -C "$T/pkg" || fail "cannot unpack $KEYRING_FILE"
else
    fail "cannot unpack $KEYRING_FILE (needs tar with zstd, or bsdtar)"
fi
K=$T/pkg/usr/share/pacman/keyrings
for f in blackarch.gpg blackarch-trusted blackarch-revoked; do
    [ -f "$K/$f" ] && [ ! -L "$K/$f" ] || fail "the package has no regular file $f"
done
extra=$(cd "$K" && ls -A | grep -vxE 'blackarch\.gpg|blackarch-trusted|blackarch-revoked' || true)
[ -z "$extra" ] || fail "unexpected keyring files: $extra"
[ "$(sha "$K/blackarch.gpg")" = "$KEYRING_GPG_SHA256" ] || fail "the package's blackarch.gpg is not the pinned one"
[ "$(sha "$K/blackarch-trusted")" = "$KEYRING_TRUSTED_SHA256" ] || fail "blackarch-trusted differs from the pin"
[ "$(sha "$K/blackarch-revoked")" = "$KEYRING_REVOKED_SHA256" ] || fail "blackarch-revoked differs from the pin"
H=$T/pkg/etc/pacman.d/hooks/blackarch-key.hook
[ -f "$H" ] && [ "$(sha "$H")" = "$KEYRING_HOOK_SHA256" ] || fail "the pacman hook differs from the pin"
[ -f "$T/pkg/.INSTALL" ] && [ "$(sha "$T/pkg/.INSTALL")" = "$KEYRING_INSTALL_SHA256" ] || fail "the install script differs from the pin"
other=$(cd "$T/pkg" && find . -mindepth 1 \( -type f -o -type l \) | LC_ALL=C sort \
    | grep -vxE '\./\.(BUILDINFO|MTREE|PKGINFO|INSTALL)|\./etc/pacman\.d/hooks/blackarch-key\.hook|\./usr/share/pacman/keyrings/blackarch(\.gpg|-trusted|-revoked)' || true)
[ -z "$other" ] || fail "unexpected files in the package: $other"

# 4. the trust decisions
LC_ALL=C
export LC_ALL
all=$(gpg --batch --with-colons --show-keys "$K/blackarch.gpg" 2>/dev/null | awk -F: '/^pub:/{p=1;next} p&&/^fpr:/{print $10;p=0}' | sort | tr '\n' ' ' | sed 's/ $//')
[ "$all" = "$KEYRING_ALL_KEYS" ] || fail "keys in blackarch.gpg: [$all], pinned [$KEYRING_ALL_KEYS]"
trusted=$(awk -F: 'NF{print $1 ":" $2}' "$K/blackarch-trusted" | sort | tr '\n' ' ' | sed 's/ $//')
want=$(for k in $KEYRING_TRUSTED; do printf '%s:4 ' "$k"; done | sed 's/ $//')
[ "$trusted" = "$want" ] || fail "blackarch-trusted lists [$trusted], pinned [$want]"
revoked=$(grep -v '^$' "$K/blackarch-revoked" | sort | tr '\n' ' ' | sed 's/ $//')
[ "$revoked" = "$KEYRING_REVOKED" ] || fail "blackarch-revoked lists [$revoked], pinned [$KEYRING_REVOKED]"
for k in $KEYRING_TRUSTED $KEYRING_REVOKED; do
    case " $KEYRING_ALL_KEYS " in *" $k "*) ;; *) fail "$k is trusted or revoked but not in the keyring" ;; esac
done
for k in $KEYRING_TRUSTED; do
    case " $KEYRING_REVOKED " in *" $k "*) fail "$k is both trusted and revoked" ;; esac
done
case " $KEYRING_TRUSTED " in *" $KEYRING_SIGNER "*) ;; *) fail "the signer is not a trusted key" ;; esac
for k in $KEYRING_ALL_KEYS; do
    case " $KEYRING_TRUSTED $KEYRING_REVOKED " in *" $k "*) ;;
        *) echo "verify-keyring: NOTICE: $k is in the keyring but neither trusted nor revoked (pinned, not trusted by pacman-key --populate)" ;;
    esac
done

echo "verify-keyring: OK $KEYRING_NAME-$KEYRING_VERSION sha256=$KEYRING_SHA256 signed by $primary (signing key $signing); trusted=$(echo $KEYRING_TRUSTED | wc -w) revoked=$(echo $KEYRING_REVOKED | wc -w) keys=$(echo $KEYRING_ALL_KEYS | wc -w)"
