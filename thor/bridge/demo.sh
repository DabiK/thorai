#!/bin/bash
# Runs the demo client of the bridge from the Mac, through adb forward (ticket 02).
# The bridge must be running (regie-bridge-start). The token is read from Termux and handed
# to the client in its environment: never written on the Mac, never printed.
#
#   thor/bridge/demo.sh            then resets ~/projects/regie-demo to its first commit
#   thor/bridge/demo.sh --list     only lists the sessions of the bridge (current and history)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
bridge="$(cd "$here/../../bridge" && pwd)"
# shellcheck source=lib.sh
source "$here/lib.sh"
need_device

adb -s "$SERIAL" forward "tcp:$BRIDGE_PORT" "tcp:$BRIDGE_PORT" > /dev/null
token="$(termux_sh "cat '$ROOTFS$DEB_TOKEN'")"
status=0
REGIE_BRIDGE_TOKEN="$token" REGIE_BRIDGE_URL="ws://127.0.0.1:$BRIDGE_PORT" \
  node "$bridge/scripts/demo-client.ts" "$@" || status=$?
unset token
[[ "${1:-}" == "--list" ]] && exit "$status"

# The demo project goes back to its first commit (6bf7330).
termux_sh 'cd ~/projects/regie-demo && git reset -q --hard 6bf7330 && git clean -qfd && git log --oneline -1'
exit "$status"
