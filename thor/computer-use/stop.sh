#!/bin/bash
# Arrête le serveur adb de la Debian, lancé par start.sh (ou démarré à la
# volée par un client adb). Ne touche pas à un serveur adb distant.
#
# Sortie standard : THOR_ANDROID=arrete ou THOR_ANDROID=rien-a-arreter.
set -uo pipefail

STATE_DIR=${XDG_STATE_HOME:-$HOME/.local/state}/thor-android
rm -f "$STATE_DIR/serial"

pids=$(pgrep -f 'adb .*fork-server' || true)
if [ -z "$pids" ]; then
  echo "THOR_ANDROID=rien-a-arreter"
  exit 0
fi

# shellcheck disable=SC2086
kill $pids 2>/dev/null || true
for _ in 1 2 3 4 5 6 7 8 9 10; do
  pgrep -f 'adb .*fork-server' >/dev/null || break
  sleep 0.2
done
echo "THOR_ANDROID=arrete"
