#!/bin/bash
# Sends the RUN_COMMAND intent the app will send, from the Mac:
#   thor/bridge/run-command.sh start | stop
# adb's shell user lacks com.termux.permission.RUN_COMMAND (« Requires permission »): the intent
# leaves with Termux's uid, through Termux's own `am`. The app sends the same intent with its
# own permission (docs/protocole-pont.md, « Démarrage et arrêt »).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$here/lib.sh"
need_device

case "${1:-}" in
  start | stop) ;;
  *)
    echo "usage: $0 start|stop" >&2
    exit 2
    ;;
esac
termux_sh "am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND --es com.termux.RUN_COMMAND_PATH $THOME/bin/regie-bridge-$1 \
--ez com.termux.RUN_COMMAND_BACKGROUND true"
