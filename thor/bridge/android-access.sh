#!/data/data/com.termux/files/usr/bin/bash
# Links the agents back to Android (ticket 03) after a restart of the console: wireless debugging
# comes back on a new port, that /opt/thor-android/start.sh finds again. Installed in Termux as
# ~/bin/regie-android-access by thor/bridge/install.sh; regie-bridge-start runs it, detached, at
# every call (the app calls regie-bridge-start again after turning wireless debugging back on).
# Without the access of ticket 03, it only says so in the log.
set -u
state="$HOME/.local/state/thor"
log="$state/regie-bridge.log"
rootfs="$PREFIX/var/lib/proot-distro/containers/debian/rootfs"
script=/opt/thor-android/start.sh
attempts=6
pause=5

mkdir -p "$state"
say() { printf '%s android-access %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >> "$log"; }

if [[ ! -x "$rootfs$script" ]]; then
  say "accès Android non installé (ticket 03)"
  exit 0
fi

# One at a time: a second call while the first one waits for the port has nothing more to do.
exec 8> "$state/regie-android-access.lock"
if ! flock -n 8; then
  say "un autre appel est en cours (verrou tenu), rien à faire"
  exit 0
fi

# The wireless port takes a moment to show up after wireless debugging is turned back on:
# start.sh exits 2 while the console is not reachable, 1 when something is missing (no retry).
for ((i = 1; i <= attempts; i++)); do
  # 8>&- : start.sh lance le serveur adb (un démon), qui ne doit pas hériter du verrou.
  out=$(proot-distro login debian --user dev -- "$script" 2>&1 < /dev/null 8>&-)
  code=$?
  say "essai $i/$attempts : ${out//$'\n'/ | }"
  case "$code" in
    0) exit 0 ;;
    2) ((i < attempts)) && sleep "$pause" ;;
    *) exit "$code" ;;
  esac
done
exit 2
