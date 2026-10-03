#!/bin/sh
#
# check-pacman-conf.sh PACMAN_CONF [PINS]
#
# Validates the pacman.conf an image would ship -- the final file, not the
# template -- and fails closed. Pure sh and awk, so it runs on the build host,
# in the build container and in the unit tests. Comments are stripped the way
# pacman strips them (everything after '#').
#
# Required:
#   - repositories exactly core, extra, alarm, aur, blackarch, in that order
#     (Arch Linux ARM's four, then BlackArch's);
#   - Architecture = aarch64, once;
#   - the global SigLevel requires signatures (Required or PackageRequired)
#     and contains no Never, Optional, TrustAll or their Package/Database
#     forms other than DatabaseOptional (Arch's default for databases);
#   - no SigLevel in any repository section; no RemoteFileSigLevel; a
#     LocalFileSigLevel only as Arch's default (Optional) or stricter;
#   - core, extra, alarm and aur include only /etc/pacman.d/mirrorlist;
#   - blackarch has exactly one line, Server = BLACKARCH_SERVER from the pins;
#   - no XferCommand (a custom downloader), no DisableSandbox, no active
#     DisableSandboxSyscalls, DownloadUser = alpm, and only
#     DisableSandboxFilesystem relaxed (Android kernels have no Landlock).
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
CONF=${1:?usage: check-pacman-conf.sh PACMAN_CONF [PINS]}
PINS=${2:-$HERE/keyring.pins}
[ -r "$CONF" ] || { echo "check-pacman-conf: FAIL: no $CONF" >&2; exit 1; }
# shellcheck disable=SC1090
. "$PINS"
[ -n "${BLACKARCH_SERVER:-}" ] && [ -n "${BLACKARCH_REPO_NAME:-}" ] \
    || { echo "check-pacman-conf: FAIL: the repository is not pinned" >&2; exit 1; }

awk -v server="$BLACKARCH_SERVER" -v repo="$BLACKARCH_REPO_NAME" '
function bad(msg) { print "check-pacman-conf: FAIL: " msg > "/dev/stderr"; failed = 1 }
function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
{
    line = $0
    sub(/#.*/, "", line)
    line = trim(line)
    if (line == "") next
    if (line ~ /^\[.*\]$/) {
        section = substr(line, 2, length(line) - 2)
        if (section != "options") { nrepos++; repos[nrepos] = section }
        lines[section] = 0
        next
    }
    key = line; val = ""
    eq = index(line, "=")
    if (eq) { key = trim(substr(line, 1, eq - 1)); val = trim(substr(line, eq + 1)) }
    lines[section]++
    if (section == "") { bad("a setting outside any section: " line); next }
    if (section == "options") {
        if (key == "Architecture") { arch++; if (val != "aarch64") bad("Architecture is " val ", not aarch64") }
        else if (key == "SigLevel") {
            siglevel++
            n = split(val, t, /[ \t]+/)
            for (i = 1; i <= n; i++) {
                if (t[i] ~ /^(Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll|DatabaseNever|DatabaseTrustAll)$/)
                    bad("the global SigLevel contains " t[i])
                if (t[i] ~ /^(Required|PackageRequired)$/) required = 1
            }
        }
        else if (key == "LocalFileSigLevel") {
            n = split(val, t, /[ \t]+/)
            for (i = 1; i <= n; i++)
                if (t[i] ~ /^(Never|TrustAll|PackageNever|PackageTrustAll|DatabaseNever|DatabaseTrustAll)$/)
                    bad("LocalFileSigLevel contains " t[i])
        }
        else if (key == "RemoteFileSigLevel") bad("RemoteFileSigLevel is set: " val)
        else if (key == "XferCommand") bad("XferCommand replaces pacmans downloader")
        else if (key == "DisableSandbox") bad("DisableSandbox turns off every sandbox layer")
        else if (key == "DisableSandboxSyscalls") bad("DisableSandboxSyscalls is active")
        else if (key == "DisableSandboxFilesystem") sandboxfs++
        else if (key == "DownloadUser") { dluser++; if (val != "alpm") bad("DownloadUser is " val) }
        next
    }
    if (key ~ /SigLevel$/) bad("the [" section "] section sets " key)
    if (section == repo) {
        if (key != "Server" || val != server) bad("[" section "] has " line ", expected only Server = " server)
        next
    }
    if (key != "Include" || val != "/etc/pacman.d/mirrorlist") bad("[" section "] has " line)
}
END {
    expected = "core extra alarm aur " repo
    got = ""
    for (i = 1; i <= nrepos; i++) got = got (i > 1 ? " " : "") repos[i]
    if (got != expected) bad("repositories are [" got "], expected [" expected "]")
    if (arch != 1) bad("Architecture is set " arch + 0 " times")
    if (siglevel != 1) bad("the global SigLevel is set " siglevel + 0 " times")
    if (!required) bad("the global SigLevel does not require signatures")
    if (sandboxfs != 1) bad("DisableSandboxFilesystem must be set exactly once")
    if (dluser != 1) bad("DownloadUser = alpm must be set exactly once")
    if (lines[repo] != 1) bad("[" repo "] must have exactly one setting")
    exit failed
}' "$CONF"
echo "check-pacman-conf: OK $(awk '{sub(/#.*/,"")} /^\[.*\]$/ && $0 != "[options]" {printf "%s ", $0}' "$CONF")"
