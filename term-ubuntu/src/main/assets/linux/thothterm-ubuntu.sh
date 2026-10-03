# Managed by ThothTerm. User shell customizations belong in ~/.bashrc.
export HOME=/home/thoth
export USER="${USER:-thoth}"
export LOGNAME="${LOGNAME:-thoth}"
export SHELL=/bin/bash
export LANG=C.UTF-8
export HISTFILE=/home/thoth/.bash_history
# Keep the resolved terminal width visible to thothfetch (bash does not export it).
export COLUMNS
# ThothTerm starts bash through "su -m", i.e. a non-login shell, so neither
# /etc/profile nor ~/.profile runs and PAM's pam_env resets PATH from
# /etc/environment. Without this, anything a user installs under $HOME
# (pipx, cargo, foundry, go, npm ...) is on disk but never found by the shell.
# Re-establish only the well-known user-local bin directories that exist, in
# the order a login shell would, never replacing what is already in PATH.
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

export PS1='\[\e[38;5;39m\]thoth\[\e[0m\]@\[\e[38;5;214m\]thothterm\[\e[0m\]:\[\e[38;5;252m\]\w\[\e[0m\]\$ '

if [ -n "${PS1-}" ] && [ -t 1 ] \
    && [ -f /etc/thothterm/welcome-enabled ] \
    && [ -z "${THOTHTERM_WELCOME_SHOWN-}" ]; then
  export THOTHTERM_WELCOME_SHOWN=1
  thothfetch
fi
