#!/data/data/com.termux/files/usr/bin/bash
# Runs the bridge in the Debian and starts it again if it crashes. Installed in Termux as
# ~/bin/regie-bridge-supervise; started by regie-bridge-start, never by hand.
#
# - A clean exit of the bridge (code 0: `bridge.shutdown`, or SIGTERM from regie-bridge-stop)
#   ends the supervisor.
# - A crash restarts it after 1, 2, 4… up to 30 s. Five crashes within two minutes: it gives up.
# - The wake lock is held while the bridge runs.
set -u
state="$HOME/.local/state/thor"
rootfs="$PREFIX/var/lib/proot-distro/containers/debian/rootfs"
supervisor_pid="$state/regie-bridge-supervisor.pid"
stop_flag="$state/regie-bridge.stop"
bridge_pid="$rootfs/home/dev/.local/state/regie/bridge/bridge.pid"
max_crashes=5
crash_window=120

log() { printf '%s supervisor %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

# The pid of the node process of the bridge, only if it is still that process.
bridge_node_pid() {
  local pid
  [[ -f "$bridge_pid" ]] && read -r pid < "$bridge_pid" && [[ "$pid" =~ ^[0-9]+$ ]] &&
    [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q 'regie/bridge/src/main.ts' &&
    printf '%s\n' "$pid"
}

on_signal() {
  touch "$stop_flag"
  local pid
  if pid=$(bridge_node_pid); then
    log "arrêt demandé, SIGTERM au pont ($pid)"
    kill -TERM "$pid" 2> /dev/null || true
  fi
}

cleanup() {
  rm -f "$supervisor_pid"
  # The wake lock is shared by Termux: it stays if the other Thor services still run.
  if ! tmux has-session -t thor-services 2> /dev/null; then termux-wake-unlock; fi
  log "superviseur arrêté"
}

printf '%s\n' "$$" > "$supervisor_pid"
trap on_signal TERM INT HUP
trap cleanup EXIT
termux-wake-lock

crashes=()
while :; do
  log "lancement du pont"
  proot-distro login debian --user dev \
    --bind "$HOME/projects:/home/dev/projects" --bind "$HOME/.codex:/home/dev/.codex" \
    --work-dir /home/dev -- bash -lc 'exec node /home/dev/.local/share/regie/bridge/src/main.ts' &
  child=$!
  wait "$child"
  code=$?
  # `wait` returns early when a signal is trapped: wait for the bridge to be really gone.
  while kill -0 "$child" 2> /dev/null; do
    wait "$child"
    code=$?
  done
  # proot can end while the bridge goes on, no longer traced (seen once on the Thor, proot
  # reporting « vpid 1: terminated with signal 9 » and exiting with 0). Untraced, the bridge
  # no longer sees the Debian files: it is stopped, and restarted as after a crash.
  if node=$(bridge_node_pid); then
    log "proot terminé, le pont ($node) tourne hors de proot : arrêt"
    kill -TERM "$node" 2> /dev/null || true
    for ((i = 0; i < 10; i++)); do kill -0 "$node" 2> /dev/null || break; sleep 1; done
    kill -KILL "$node" 2> /dev/null || true
    code=1
  fi
  if [[ -f "$stop_flag" ]]; then
    log "pont arrêté (code $code)"
    break
  fi
  # A clean stop removes the pid file of the bridge; proot's exit code alone is not enough.
  if [[ "$code" -eq 0 && ! -f "$bridge_pid" ]]; then
    log "pont arrêté proprement"
    break
  fi
  now=$(date +%s)
  recent=()
  for at in "${crashes[@]}"; do
    (( now - at < crash_window )) && recent+=("$at")
  done
  crashes=("${recent[@]}" "$now")
  if (( ${#crashes[@]} >= max_crashes )); then
    log "le pont a planté ${#crashes[@]} fois en moins de ${crash_window} s : abandon"
    exit 1
  fi
  delay=$(( 1 << (${#crashes[@]} - 1) ))
  (( delay > 30 )) && delay=30
  log "le pont a planté (code $code), relance dans ${delay} s"
  sleep "$delay" &
  wait $! || true
  [[ -f "$stop_flag" ]] && break
done
