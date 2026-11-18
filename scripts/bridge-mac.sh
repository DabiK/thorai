#!/usr/bin/env bash
# Launch only on demand; separate state, projects and token, bound to loopback.
# --device uses the selected adb device; default requires Regie_Thor. stop shuts down this bench.
source "$(dirname "$0")/emu-common.sh"
umask 077
mkdir -p "$REGIE_BENCH/projects" "$REGIE_BENCH/state"
export REGIE_BENCH_DIR="$REGIE_BENCH"
# The authenticated control client also needs ws when entering a fresh worktree.
if [[ ! -f "$REGIE_ROOT/bridge/node_modules/ws/package.json" ]]; then
    (cd "$REGIE_ROOT/bridge" && npm ci)
fi
if [[ "${1:-}" == stop ]]; then
    node "$REGIE_ROOT/scripts/emu-client.mjs" stop
    exit
fi
bridge_device="$EMU_SERIAL"
if [[ "${1:-}" == --device ]]; then
    source "$REGIE_ROOT/scripts/device.sh"
    bridge_device="$(select_adb_device)"
else
    require_regie_emulator
fi
if [[ ! -f "$REGIE_BENCH/bridge-token" ]]; then
    openssl rand -hex 32 > "$REGIE_BENCH/bridge-token"
fi
chmod 600 "$REGIE_BENCH/bridge-token"
if curl --silent --fail --max-time 2 http://127.0.0.1:47600/health >/dev/null; then
    if [[ ! -f "$REGIE_BENCH/bridge-root" ]] || [[ "$(cat "$REGIE_BENCH/bridge-root")" != "$REGIE_ROOT" ]]; then
        echo 'Pont actif issu d’un autre worktree ou non identifié : bridge-mac.sh stop, puis bridge-mac.sh pour utiliser ce worktree.' >&2
        exit 1
    fi
    node "$REGIE_ROOT/scripts/emu-client.mjs" ping || {
        echo 'Le port 47600 appartient à un autre pont ou le jeton ne correspond pas. Aucun processus arrêté.' >&2; exit 1;
    }
else
    (cd "$REGIE_ROOT/bridge" && npm ci)
    demo="$REGIE_BENCH/projects/regie-demo"
    if [[ ! -e "$demo" ]]; then
        mkdir "$demo"
        git -C "$demo" init -b main --quiet
        printf '# Régie — projet de démonstration\n\nRepère de lecture : THOR-DEMO-16.\n' > "$demo/README.md"
        git -C "$demo" add README.md
        git -C "$demo" -c user.name='Regie Bench' -c user.email='bench@regie.invalid' commit --quiet -m 'chore: initialise la démonstration'
    fi
    export REGIE_BRIDGE_PORT=47600 REGIE_BRIDGE_TOKEN_FILE="$REGIE_BENCH/bridge-token"
    export REGIE_BRIDGE_STATE_DIR="$REGIE_BENCH/state" REGIE_PROJECTS_DIR="$REGIE_BENCH/projects"
    export OPENCODE_PATH="${OPENCODE_PATH:-$(command -v opencode || true)}"
    export CODEX_PATH="${CODEX_PATH:-$(command -v codex)}"
    [[ -x "$CODEX_PATH" ]] || { echo 'Codex est absent du PATH.' >&2; exit 1; }
    printf '%s\n' "$REGIE_ROOT" > "$REGIE_BENCH/bridge-root"
    python3 - "$REGIE_ROOT/bridge" "$REGIE_BENCH/bridge.log" <<'SPAWN'
import subprocess, sys
with open(sys.argv[2], 'a') as log:
    subprocess.Popen(['node', 'src/main.ts'], cwd=sys.argv[1], stdin=subprocess.DEVNULL,
                     stdout=log, stderr=log, start_new_session=True)
SPAWN
    ready=false
    for attempt in {1..15}; do
        if node "$REGIE_ROOT/scripts/emu-client.mjs" ping 2>/dev/null; then ready=true; break; fi
        sleep 1
    done
    $ready || { echo "Pont indisponible : $REGIE_BENCH/bridge.log" >&2; exit 1; }
fi
if [[ "$bridge_device" == "$EMU_SERIAL" ]]; then reverse_bridge
else adb -s "$bridge_device" reverse tcp:47600 tcp:47600; fi
echo "Pont prêt sur le Mac ; projets : $REGIE_BENCH/projects."
