#!/bin/bash
# Associe le client adb de la Debian au débogage sans fil de la console.
# À lancer dans la Debian (utilisateur dev), pendant que la console affiche
# « Associer l'appareil avec un code d'association ».
#
#   /opt/thor-android/pair.sh <port d'association> <code à 6 chiffres>
#
# Le port est celui affiché après les deux-points dans « Adresse IP et port ».
set -euo pipefail
# Ce script ne lit jamais l'entrée standard : on la ferme, pour qu'aucune
# commande (adb shell…) n'avale celle de l'appelant, par exemple un script lu
# par « bash -s ».
exec </dev/null

if [ $# -ne 2 ]; then
  echo "usage : $0 <port d'association> <code à 6 chiffres>" >&2
  exit 2
fi
port=$1
code=$2
# Même socket privée que start.sh.
export ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-localfilesystem:/home/dev/.thor-adb/server}

# Le serveur local se reconnaît à sa ligne de commande : adb -L <socket>
# fork-server (même motif et même repli sans pgrep que stop.sh).
SERVER_PATTERN="([^ ]*/)?adb -L $ADB_SERVER_SOCKET fork-server server.*"
local_server_running() {
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -f -x "$SERVER_PATTERN" >/dev/null
    return
  fi
  local dir cmd
  for dir in /proc/[0-9]*; do
    cmd=$(tr '\0' ' ' < "$dir/cmdline" 2>/dev/null) || continue
    [[ "${cmd% }" =~ ^$SERVER_PATTERN$ ]] && return 0
  done
  return 1
}

# Si aucun serveur adb ne tournait avant, on l'arrête à la fin : sinon la
# session Debian ne se fermerait pas. À calculer avant adb start-server.
already_running=0
local_server_running && already_running=1

case "$ADB_SERVER_SOCKET" in
  localfilesystem:*)
    socket_dir=$(dirname "${ADB_SERVER_SOCKET#localfilesystem:}")
    mkdir -p "$socket_dir" && chmod 700 "$socket_dir"
    ;;
esac

# adb pair enregistre la clé du serveur qui répond sur la socket. Si ce n'est
# pas un serveur de la Debian (socket relayée vers le serveur adb d'une autre
# machine, par exemple le Mac), c'est la clé de cette machine qui serait
# associée, et l'accès ne marcherait pas sans elle : on refuse.
if ! start_err=$(adb start-server 2>&1 >/dev/null); then
  {
    echo "Impossible de démarrer le serveur adb de la Debian sur $ADB_SERVER_SOCKET."
    [ -z "$start_err" ] || echo "$start_err"
  } >&2
  exit 1
fi
if ! local_server_running; then
  {
    echo "Refus : le serveur adb qui répond sur $ADB_SERVER_SOCKET n'est pas un serveur local de la Debian."
    echo "Un autre serveur adb (relayé, par exemple celui du Mac) y répond : adb pair associerait sa clé, pas celle de la Debian."
    echo "Arrêtez ce relais, puis relancez l'association : elle doit se faire avec le serveur adb de la Debian."
  } >&2
  exit 3
fi

# Pas de sortie directe par set -e si adb pair échoue : le serveur démarré
# plus haut doit quand même être arrêté.
status=0
adb pair "127.0.0.1:$port" "$code" || status=$?
if [ "$status" = 0 ]; then
  "$(dirname "$0")/start.sh" || status=$?
fi

if [ "$already_running" = 0 ]; then
  "$(dirname "$0")/stop.sh" >/dev/null
fi
exit "$status"
