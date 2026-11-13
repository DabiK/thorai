#!/bin/bash
# Test de thor/computer-use/pair.sh avec un faux adb et un faux pgrep : pas
# d'adb pair si le serveur qui répond sur la socket n'est pas local, ni si
# adb start-server échoue.
#   thor/computer-use/test-pair.sh [chemin de pair.sh]
set -uo pipefail
PAIR=$(cd "$(dirname "${1:-$(dirname "$0")/pair.sh}")" && pwd)/$(basename "${1:-pair.sh}")
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
fails=0
check() { # nom commande… : la commande doit réussir
  local name=$1; shift
  if "$@"; then echo "ok   $name"; else echo "ECHEC $name"; fails=$((fails + 1)); fi
}

mkdir -p "$W/bin" "$W/thor"
cp "$PAIR" "$W/thor/pair.sh"
# Faux start.sh / stop.sh : ils notent leur appel.
for s in start stop; do
  # shellcheck disable=SC2016 # $LOG est développé par le faux script.
  printf '#!/bin/bash\necho %s >> "$LOG"\n' "$s" > "$W/thor/$s.sh"
done
# Faux adb : note ses arguments ; start-server échoue si LOCAL_ON_START=echec,
# lance le serveur local si LOCAL_ON_START=1 (sinon un relais répond déjà sur
# la socket).
cat > "$W/bin/adb" <<'ADB'
#!/bin/bash
echo "adb $*" >> "$LOG"
if [ "$1" = start-server ] && [ "$LOCAL_ON_START" = echec ]; then
  echo "cannot bind socket: Permission denied" >&2
  exit 1
fi
if [ "$1" = start-server ] && [ "$LOCAL_ON_START" = 1 ]; then touch "$STATE"; fi
exit 0
ADB
# Faux pgrep : trouve le serveur local si le fichier d'état existe.
# shellcheck disable=SC2016
printf '#!/bin/bash\n[ -e "$STATE" ]\n' > "$W/bin/pgrep"
chmod +x "$W/bin/"* "$W/thor/"*.sh

# shellcheck disable=SC2329 # appelée par check.
not() { ! "$@"; }
run() { # LOCAL_ON_START (0, 1 ou echec) déjà-lancé
  : > "$W/log"; rm -f "$W/state"
  [ "$2" = 1 ] && touch "$W/state"
  LOG=$W/log STATE=$W/state LOCAL_ON_START=$1 PATH="$W/bin:$PATH" \
    ADB_SERVER_SOCKET="localfilesystem:$W/sock/server" \
    "$W/thor/pair.sh" 37000 123456 2> "$W/err"
}

# 1. un relais répond sur la socket : refus, pas d'adb pair
run 0 0; st=$?
check "relais: code 3 ($st)" test "$st" -eq 3
check "relais: adb pair jamais appelé" not grep -q '^adb pair' "$W/log"
check "relais: message sur stderr" grep -q 'Refus' "$W/err"

# 2. adb start-server échoue : code 1, pas d'adb pair, message adapté
run echec 0; st=$?
check "start-server en échec: code 1 ($st)" test "$st" -eq 1
check "start-server en échec: adb pair jamais appelé" not grep -q '^adb pair' "$W/log"
check "start-server en échec: message sur stderr" grep -q 'Impossible de démarrer le serveur adb de la Debian' "$W/err"
check "start-server en échec: erreur d'adb relayée" grep -q 'cannot bind socket' "$W/err"
check "start-server en échec: pas de refus « relayé »" not grep -q 'Refus' "$W/err"

# 3. serveur local démarré par start-server : adb pair, puis stop.sh
run 1 0; st=$?
check "local: code 0 ($st)" test "$st" -eq 0
check "local: adb pair appelé" grep -qx 'adb pair 127.0.0.1:37000 123456' "$W/log"
check "local: serveur arrêté à la fin" grep -qx stop "$W/log"

# 4. serveur local déjà lancé avant : adb pair, sans stop.sh
run 1 1
check "déjà lancé: adb pair appelé" grep -qx 'adb pair 127.0.0.1:37000 123456' "$W/log"
check "déjà lancé: serveur laissé en place" not grep -qx stop "$W/log"

[ "$fails" = 0 ] && echo "tout passe" || echo "$fails échec(s)"
exit "$fails"
