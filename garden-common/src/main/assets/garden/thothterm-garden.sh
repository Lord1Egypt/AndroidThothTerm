# Managed by ThothTerm Garden. User shell customizations belong in ~/.bashrc.
export HOME=/home/thoth
export USER="${USER:-thoth}"
export LOGNAME="${LOGNAME:-thoth}"
export LANG=C.UTF-8
export HISTFILE=/home/thoth/.bash_history
# ThothTerm starts the shell through "su -m", i.e. a non-login shell, so neither
# /etc/profile nor ~/.profile runs and PAM's pam_env resets PATH from
# /etc/environment. Re-establish the well-known user-local bin directories that
# exist, in the order a login shell would, never replacing what is in PATH.
thothterm_prepend_path() {
  [ -n "${1-}" ] || return 0
  [ -d "$1" ] || return 0
  case ":${PATH}:" in
    *":$1:"*) return 0 ;;
  esac
  PATH="$1:${PATH}"
}

# Listed last-to-first: the final PATH order is the reverse of this list.
for thothterm_dir in \
    "$HOME/.npm-global/bin" \
    "$HOME/.deno/bin" \
    "$HOME/.bun/bin" \
    "$HOME/go/bin" \
    "$HOME/.foundry/bin" \
    "$HOME/.cargo/bin" \
    "$HOME/bin" \
    "$HOME/.local/bin"; do
  thothterm_prepend_path "$thothterm_dir"
done
unset thothterm_dir
unset -f thothterm_prepend_path
export PATH

# PRoot cannot change the kernel's node name, so \h would show Android's host
# name; the prompt uses the guest's /etc/hostname instead.
thothterm_host="$(cat /etc/hostname 2>/dev/null)"
PS1='\[\e[01;32m\]\u@'"${thothterm_host:-thothterm}"'\[\e[00m\]:\[\e[01;34m\]\w\[\e[00m\]\$ '
unset thothterm_host
