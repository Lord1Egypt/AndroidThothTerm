# Managed by ThothTerm. User shell customizations belong in ~/.bashrc.
export HOME=/home/thoth
export USER="${USER:-thoth}"
export LOGNAME="${LOGNAME:-thoth}"
export SHELL=/bin/bash
export LANG=C.UTF-8
export HISTFILE=/home/thoth/.bash_history
export PS1='\[\e[38;5;39m\]thoth\[\e[0m\]@\[\e[38;5;214m\]thothterm\[\e[0m\]:\[\e[38;5;252m\]\w\[\e[0m\]\$ '

if [ -n "${PS1-}" ] && [ -t 1 ] \
    && [ -f /etc/thothterm/welcome-enabled ] \
    && [ -z "${THOTHTERM_WELCOME_SHOWN-}" ]; then
  export THOTHTERM_WELCOME_SHOWN=1
  thothfetch
fi
