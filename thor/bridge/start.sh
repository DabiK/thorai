#!/data/data/com.termux/files/usr/bin/bash
# Starts the Régie bridge, on demand only (nothing at boot). Installed in Termux as
# ~/bin/regie-bridge-start by thor/bridge/install.sh. The app calls it with RUN_COMMAND
# (docs/protocole-pont.md). Idempotent: a running bridge is left alone.
set -u
state="$HOME/.local/state/thor"
mkdir -p "$state"
supervisor_pid="$state/regie-bridge-supervisor.pid"
log="$state/regie-bridge.log"
port="${REGIE_BRIDGE_PORT:-47600}"

# Two starts at once: only one goes on.
exec 9>"$state/regie-bridge.lock"
flock -n 9 || { echo "Démarrage déjà en cours."; exit 0; }

running() {
  local pid
  [[ -f "$supervisor_pid" ]] && read -r pid < "$supervisor_pid" && [[ "$pid" =~ ^[0-9]+$ ]] &&
    [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q regie-bridge-supervise
}

if running; then
  echo "Le pont tourne déjà."
  exit 0
fi

rm -f "$state/regie-bridge.stop"
# Keeps the log under 5 Mo: the previous one is kept as .1.
if [[ -f "$log" ]] && (( $(stat -c %s "$log") > 5 * 1024 * 1024 )); then
  mv -f "$log" "$log.1"
fi
setsid nohup "$HOME/bin/regie-bridge-supervise" >> "$log" 2>&1 < /dev/null 9>&- &

for ((i = 0; i < 30; i++)); do
  if curl -fsS "http://127.0.0.1:$port/health" > /dev/null 2>&1; then
    echo "Pont démarré sur 127.0.0.1:$port."
    exit 0
  fi
  sleep 1
done
echo "Démarrage non confirmé. Journal : $log"
exit 1
