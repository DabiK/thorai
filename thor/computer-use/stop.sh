#!/bin/bash
# Arrête le serveur adb de la Debian lancé par start.sh (ou démarré à la volée
# par un client adb), celui de la socket privée $ADB_SERVER_SOCKET, et avant
# lui le serveur de mobilecli sur la console. Ne touche à aucun autre serveur adb.
#
# Sortie standard : THOR_ANDROID=arrete ou THOR_ANDROID=rien-a-arreter.
set -uo pipefail

STATE_DIR=${XDG_STATE_HOME:-$HOME/.local/state}/thor-android
# Même valeur que start.sh.
export ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-localfilesystem:/home/dev/.thor-adb/server}
rm -f "$STATE_DIR/serial"

# Le serveur se reconnaît à sa ligne de commande : adb -L <socket> fork-server.
server_pids() {
  pgrep -f -x "([^ ]*/)?adb -L $ADB_SERVER_SOCKET fork-server server.*"
}

if [ -z "$(server_pids)" ]; then
  echo "THOR_ANDROID=rien-a-arreter"
  exit 0
fi

ADB=${THOR_ADB:-adb}
# Le serveur de mobilecli sur la console (DeviceServer, utilisateur shell)
# survivrait 30 minutes au dernier appel, sur une socket abstraite que toute
# appli peut joindre (SELinux permissif sur la Thor, ADR 03) : on l'arrête.
for serial in $(timeout 5 "$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }'); do
  timeout 5 "$ADB" -s "$serial" shell 'pkill -f com.mobilenext.mobilecli.DeviceServer' >/dev/null 2>&1 || true
done

# Arrêt propre (il ferme aussi les adb forward de mobilecli), puis signal en repli.
timeout 5 "$ADB" kill-server >/dev/null 2>&1 || true
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  pids=$(server_pids) || break
  # shellcheck disable=SC2086
  [ "$attempt" -ge 5 ] && kill $pids 2>/dev/null
  sleep 0.2
done
echo "THOR_ANDROID=arrete"
