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

# The prompt's colours are the edition's, from /etc/thothterm/palette (written
# by the app, read here as data). Only colour changes between editions; the
# text is always thoth@thothterm:DIR$ . NO_COLOR, or no palette, gives it plain.
thothterm_user= thothterm_host= thothterm_path=
if [ -z "${NO_COLOR-}" ] && [ -r "${THOTHTERM_GUEST_ROOT-}/etc/thothterm/palette" ]; then
  while IFS='=' read -r thothterm_role thothterm_sgr; do
    case $thothterm_sgr in ''|*[!0-9\;]*) continue ;; esac
    case $thothterm_role in
      promptUser) thothterm_user="\[\e[${thothterm_sgr}m\]" ;;
      promptHost) thothterm_host="\[\e[${thothterm_sgr}m\]" ;;
      promptPath) thothterm_path="\[\e[${thothterm_sgr}m\]" ;;
    esac
  done < "${THOTHTERM_GUEST_ROOT-}/etc/thothterm/palette"
fi
thothterm_reset=
[ -n "$thothterm_user$thothterm_host$thothterm_path" ] && thothterm_reset='\[\e[0m\]'
PS1="${thothterm_user}thoth${thothterm_reset}@${thothterm_host}thothterm${thothterm_reset}:${thothterm_path}"'\w'"${thothterm_reset}"'\$ '
export PS1
unset thothterm_role thothterm_sgr thothterm_user thothterm_host thothterm_path thothterm_reset

if [ -n "${PS1-}" ] && [ -t 1 ] \
    && [ -f /etc/thothterm/welcome-enabled ] \
    && [ -z "${THOTHTERM_WELCOME_SHOWN-}" ]; then
  export THOTHTERM_WELCOME_SHOWN=1
  thothfetch
fi
