#!/bin/bash
# Associe le client adb de la Debian au débogage sans fil de la console.
# À lancer dans la Debian (utilisateur dev), pendant que la console affiche
# « Associer l'appareil avec un code d'association ».
#
#   /opt/thor-android/pair.sh <port d'association> <code à 6 chiffres>
#
# Le port est celui affiché après les deux-points dans « Adresse IP et port ».
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage : $0 <port d'association> <code à 6 chiffres>" >&2
  exit 2
fi
port=$1
code=$2

# Si aucun serveur adb ne tournait avant, on l'arrête à la fin : sinon la
# session Debian ne se fermerait pas.
already_running=0
pgrep -f 'adb .*fork-server' >/dev/null && already_running=1

adb pair "127.0.0.1:$port" "$code"
status=0
"$(dirname "$0")/start.sh" || status=$?

if [ "$already_running" = 0 ]; then
  "$(dirname "$0")/stop.sh" >/dev/null
fi
exit "$status"
