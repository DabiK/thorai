#!/bin/bash
# Arrête le serveur adb de la Debian lancé par start.sh (ou démarré à la volée
# par un client adb), celui de la socket privée $ADB_SERVER_SOCKET, et avant
# lui le serveur de mobilecli sur la console. Ne touche à aucun autre serveur adb.
#
# Sortie standard : THOR_ANDROID=arrete ou THOR_ANDROID=rien-a-arreter.
set -uo pipefail
# Ce script ne lit jamais l'entrée standard : on la ferme, pour qu'aucune
# commande (adb shell…) n'avale celle de l'appelant, par exemple un script lu
# par « bash -s ».
exec </dev/null

STATE_DIR=${XDG_STATE_HOME:-$HOME/.local/state}/thor-android
# Même valeur que start.sh.
export ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-localfilesystem:/home/dev/.thor-adb/server}
rm -f "$STATE_DIR/serial"

# Le serveur se reconnaît à sa ligne de commande : adb -L <socket> fork-server.
SERVER_PATTERN="([^ ]*/)?adb -L $ADB_SERVER_SOCKET fork-server server.*"
server_pids() {
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -f -x "$SERVER_PATTERN"
    return
  fi
  # Repli sans procps (install.sh le pose) : lecture de /proc.
  local dir cmd found=1
  for dir in /proc/[0-9]*; do
    cmd=$(tr '\0' ' ' < "$dir/cmdline" 2>/dev/null) || continue
    if [[ "${cmd% }" =~ ^$SERVER_PATTERN$ ]]; then
      echo "${dir#/proc/}"
      found=0
    fi
  done
  return "$found"
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
  timeout 5 "$ADB" -s "$serial" shell -n 'pkill -f com.mobilenext.mobilecli.DeviceServer' >/dev/null 2>&1 || true
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
