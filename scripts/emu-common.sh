#!/usr/bin/env bash
# Shared setup; source from the emulator scripts.
set -euo pipefail
REGIE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGIE_BENCH="${REGIE_BENCH_DIR:-$HOME/Documents/regie-sandbox}"
EMU_SERIAL="${EMU_SERIAL:-emulator-5580}"
case "$EMU_SERIAL" in emulator-[0-9]*) ;; *) echo 'EMU_SERIAL doit désigner un émulateur.' >&2; exit 1 ;; esac
EMU_PORT="${EMU_SERIAL#emulator-}"
[[ "$EMU_PORT" =~ ^[0-9]+$ ]] && (( EMU_PORT >= 5554 && EMU_PORT <= 5682 && EMU_PORT % 2 == 0 )) || {
    echo 'Port émulateur invalide (pair, entre 5554 et 5682).' >&2; exit 1;
}
require_regie_emulator() {
    local name
    name="$(adb -s "$EMU_SERIAL" emu avd name | tr -d '\r' | head -n 1)"
    [[ "$name" == Regie_Thor ]] || { echo "$EMU_SERIAL n'est pas Regie_Thor ($name)." >&2; exit 1; }
}

reverse_bridge() {
    # Emulator adbd can transiently fail to bind a recently used guest TCP port.
    # Do not remove other listeners or restart the shared adb server.
    local result deadline=$((SECONDS + 90))
    while true; do
        if result="$(adb -s "$EMU_SERIAL" reverse tcp:47600 tcp:47600 2>&1)"; then
            return
        fi
        if [[ "$result" != *'Address already in use'* ]] || (( SECONDS >= deadline )); then
            printf '%s\n' "$result" >&2
            return 1
        fi
        echo 'Port reverse occupé temporairement ; nouvelle tentative dans 2 s.' >&2
        sleep 2
    done
}
