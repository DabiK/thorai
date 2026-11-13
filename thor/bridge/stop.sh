#!/data/data/com.termux/files/usr/bin/bash
# Stops the Régie bridge cleanly: the sessions first (cancelled, then their agents closed), then
# the bridge. Installed in Termux as ~/bin/regie-bridge-stop; the « Quitter » button of the app
# calls it with RUN_COMMAND. Only the processes of the bridge are touched (recorded pids): never
# a global `proot-distro kill`, other services run in the Debian.
set -u
state="$HOME/.local/state/thor"
rootfs="$PREFIX/var/lib/proot-distro/containers/debian/rootfs"
supervisor_pid="$state/regie-bridge-supervisor.pid"
bridge_pid="$rootfs/home/dev/.local/state/regie/bridge/bridge.pid"

pid_of() { # pid_of <file> <pattern in its command line>
  local pid
  [[ -f "$1" ]] && read -r pid < "$1" && [[ "$pid" =~ ^[0-9]+$ ]] &&
    [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q "$2" &&
    printf '%s\n' "$pid"
}

mkdir -p "$state"
touch "$state/regie-bridge.stop"
supervisor=$(pid_of "$supervisor_pid" regie-bridge-supervise) || supervisor=''
node=$(pid_of "$bridge_pid" 'regie/bridge/src/main.ts') || node=''

if [[ -z "$supervisor" && -z "$node" ]]; then
  echo "Le pont ne tourne pas."
  exit 0
fi

# SIGTERM to the bridge: it cancels the turns, closes the agents (up to ~7 s), then exits.
if [[ -n "$node" ]]; then
  kill -TERM "$node" 2> /dev/null || true
elif [[ -n "$supervisor" ]]; then
  kill -TERM "$supervisor" 2> /dev/null || true
fi

for ((i = 0; i < 25; i++)); do
  if ! pid_of "$supervisor_pid" regie-bridge-supervise > /dev/null &&
    ! pid_of "$bridge_pid" 'regie/bridge/src/main.ts' > /dev/null; then
    echo "Pont arrêté."
    exit 0
  fi
  sleep 1
done

# Still there: the recorded processes only.
node=$(pid_of "$bridge_pid" 'regie/bridge/src/main.ts') && kill -KILL "$node" 2> /dev/null
supervisor=$(pid_of "$supervisor_pid" regie-bridge-supervise) && kill -TERM "$supervisor" 2> /dev/null
sleep 2
if ! tmux has-session -t thor-services 2> /dev/null; then termux-wake-unlock; fi
echo "Pont arrêté de force. Journal : $state/regie-bridge.log"
