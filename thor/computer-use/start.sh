#!/bin/bash
# Relie le client adb de la Debian à l'adbd de la console.
# Appelé par le pont à son démarrage, sous l'utilisateur dev. Rapide et
# idempotent : il ne laisse tourner que le serveur adb, sur une socket Unix
# privée ($ADB_SERVER_SOCKET).
#
# Sortie standard : une seule ligne, lisible par machine.
#   THOR_ANDROID=ok serial=<série adb> via=<existant|tcpip|sans-fil>
#   THOR_ANDROID=erreur raison=<adb-absent|getprop-absent|serveur-adb|non-connecte>
# Code de sortie : 0 si la console est joignable, 1 si adb ou getprop manque,
# ou si le serveur adb ne démarre pas, 2 si la console n'est pas joignable. Le message pour
# l'utilisateur, en français, va sur la sortie d'erreur.
set -uo pipefail
# Ce script ne lit jamais l'entrée standard : on la ferme, pour qu'aucune
# commande (adb shell…) n'avale celle de l'appelant, par exemple un script lu
# par « bash -s ».
exec </dev/null

ADB=${THOR_ADB:-adb}
STATE_DIR=${XDG_STATE_HOME:-$HOME/.local/state}/thor-android
TCPIP_ADDR=127.0.0.1:5555
# getprop d'Android, par chemin absolu : le PATH de la Debian ne le contient pas.
GETPROP=${THOR_GETPROP:-/system/bin/getprop}
# Le serveur adb écoute sur une socket Unix privée, pas sur 127.0.0.1:5037 :
# seuls les processus de Termux et de la Debian (même uid Android) la joignent.
# Sous proot, le chemin réel est celui du rootfs suivi de celui-ci : il doit
# tenir dans les 108 octets d'une adresse de socket Unix (103 aujourd'hui).
# Même valeur dans stop.sh, pair.sh, thor-screen, thor-android-mcp, adb-wrapper
# et dans les déclarations MCP d'install.sh.
export ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-localfilesystem:/home/dev/.thor-adb/server}

ok() {
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$1" > "$STATE_DIR/serial"
  echo "THOR_ANDROID=ok serial=$1 via=$2"
  exit 0
}

fail() {
  local code=$1 reason=$2
  shift 2
  rm -f "$STATE_DIR/serial"
  echo "THOR_ANDROID=erreur raison=$reason"
  printf 'thor-android : %s\n' "$@" >&2
  exit "$code"
}

command -v "$ADB" >/dev/null 2>&1 ||
  fail 1 adb-absent "adb n'est pas installé dans la Debian." \
    "Relancez thor/computer-use/install.sh depuis le Mac."

[ -x "$GETPROP" ] ||
  fail 1 getprop-absent "$GETPROP est introuvable : la Debian ne voit pas le /system d'Android."

# Démarre le serveur adb s'il n'y en a pas déjà un sur la socket, dans un
# dossier fermé aux autres (0700).
case "$ADB_SERVER_SOCKET" in
  localfilesystem:*)
    socket_dir=$(dirname "${ADB_SERVER_SOCKET#localfilesystem:}")
    if ! out=$(mkdir -p "$socket_dir" 2>&1 && chmod 700 "$socket_dir" 2>&1); then
      fail 1 serveur-adb "dossier de la socket adb impossible à créer : $out"
    fi
    ;;
esac
if ! out=$(timeout 20 "$ADB" start-server 2>&1); then
  fail 1 serveur-adb "le serveur adb ne démarre pas : $out"
fi

# Identifiant de cette console, pour ne pas confondre avec un autre appareil
# que verrait le serveur adb.
if ! GUID=$("$GETPROP" persist.adb.wifi.guid 2>&1); then
  fail 1 getprop-absent "$GETPROP ne répond pas : $GUID"
fi
GUID=$(tr -d '\r' <<<"$GUID")

is_this_console() {
  [ -z "$GUID" ] && return 0
  local remote
  remote=$(timeout 5 "$ADB" -s "$1" shell -n getprop persist.adb.wifi.guid 2>/dev/null | tr -d '\r')
  [ "$remote" = "$GUID" ]
}

find_device() {
  local serial
  for serial in $("$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }'); do
    if is_this_console "$serial"; then
      echo "$serial"
      return 0
    fi
  done
  return 1
}

try_connect() {
  local addr=$1 out
  out=$(timeout 8 "$ADB" connect "$addr" 2>&1)
  case "$out" in
    *"connected to"*) ;;
    *) return 1 ;;
  esac
  timeout 5 "$ADB" -s "$addr" wait-for-device >/dev/null 2>&1
  if [ "$("$ADB" -s "$addr" get-state 2>/dev/null)" = device ] && is_this_console "$addr"; then
    return 0
  fi
  "$ADB" disconnect "$addr" >/dev/null 2>&1
  return 1
}

# 1. Déjà connectée (USB vu par un serveur partagé, débogage sans fil déjà relié).
if serial=$(find_device); then
  ok "$serial" existant
fi

# 2. Repli : adbd en TCP sur 5555, après un « adb tcpip 5555 » depuis le Mac.
if try_connect "$TCPIP_ADDR"; then
  ok "$TCPIP_ADDR" tcpip
fi

# 3. Débogage sans fil : le port change à chaque activation.
ports=$("$GETPROP" service.adb.tls.port 2>/dev/null | tr -d '\r' || true)
ports="$ports $(timeout 5 "$ADB" mdns services 2>/dev/null |
  awk -v guid="$GUID" '/_adb-tls-connect/ && (guid == "" || index($0, guid)) { n = split($NF, a, ":"); print a[n] }')"
for port in $ports; do
  case "$port" in '' | *[!0-9]* | 0) continue ;; esac
  if try_connect "127.0.0.1:$port"; then
    ok "127.0.0.1:$port" sans-fil
  fi
done

# Le serveur adb peut avoir relié tout seul le débogage sans fil entre-temps.
if serial=$(find_device); then
  ok "$serial" sans-fil
fi

fail 2 non-connecte \
  "les agents ne peuvent pas joindre Android : le débogage sans fil est coupé ou pas encore associé." \
  "Sur la console : Paramètres › Options pour les développeurs › Débogage sans fil › Utiliser le débogage sans fil." \
  "S'il est déjà activé, refaites l'association (voir thor/computer-use/README.md, « Associer la console »)."
