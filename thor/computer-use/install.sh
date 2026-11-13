#!/bin/bash
# Installe l'accès des agents à Android (serveur MCP thor_android) sur la console.
# Lancé depuis le Mac, console branchée en USB. Idempotent.
#
#   thor/computer-use/install.sh            (THOR_SERIAL=THOR0000000001 par défaut)
#
# Pose sur la console :
#   Debian  : paquet adb ; /opt/thor-android (mobile-mcp épinglé, start.sh,
#             stop.sh, pair.sh) ; ~dev/.local/bin/thor-android-mcp et thor-screen
#   Claude  : serveur thor_android en portée utilisateur (~dev/.claude.json)
#   Codex   : bloc [mcp_servers.thor_android] dans ~/.codex/config.toml (Termux)
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

MOBILE_MCP_VERSION=1.0.17
LAUNCHER=/home/dev/.local/bin/thor-android-mcp

require_console

echo "1/4 Debian (root) : adb, mobile-mcp $MOBILE_MCP_VERSION, scripts de /opt/thor-android"
{
  cat <<EOF
set -euo pipefail
if ! command -v adb >/dev/null 2>&1; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq adb >/dev/null
fi
mkdir -p /opt/thor-android
cd /opt/thor-android
[ -f package.json ] || echo '{"name":"thor-android","private":true}' > package.json
current=\$(node -p "require('./node_modules/@mobilenext/mobile-mcp/package.json').version" 2>/dev/null || true)
if [ "\$current" != "$MOBILE_MCP_VERSION" ]; then
  npm install --save-exact --omit=dev --no-audit --no-fund --loglevel=error @mobilenext/mobile-mcp@$MOBILE_MCP_VERSION
fi
EOF
  emit_file "$HERE/start.sh" /opt/thor-android/start.sh 755
  emit_file "$HERE/stop.sh" /opt/thor-android/stop.sh 755
  emit_file "$HERE/pair.sh" /opt/thor-android/pair.sh 755
} | debian_sh root

echo "2/4 Debian (dev) : lanceurs thor-android-mcp et thor-screen"
{
  emit_file "$HERE/thor-android-mcp" "$LAUNCHER" 755
  emit_file "$HERE/thor-screen" /home/dev/.local/bin/thor-screen 755
  cat <<'EOF'
set -euo pipefail
mobilecli=$(node -p "require('/opt/thor-android/node_modules/@mobilenext/mobile-mcp/lib/mobilecli.js').Mobilecli.getMobilecliPath()")
echo "    $("$mobilecli" --version), adb $(adb version | sed -n 's/^Version //p')"
EOF
} | debian_sh dev

echo "3/4 Claude Code : serveur thor_android (portée utilisateur)"
debian_sh dev <<EOF
set -euo pipefail
if jq -e '.mcpServers.thor_android' ~/.claude.json >/dev/null 2>&1; then
  echo "    déjà déclaré"
else
  claude mcp add-json --scope user thor_android '{"type":"stdio","command":"$LAUNCHER","args":[],"env":{}}'
fi
EOF

echo "4/4 Codex : serveur thor_android (~/.codex/config.toml de Termux)"
termux_sh <<EOF
set -euo pipefail
config=\$HOME/.codex/config.toml
if grep -q '^\[mcp_servers\.thor_android\]' "\$config"; then
  echo "    déjà déclaré"
else
  printf '\n[mcp_servers.thor_android]\ncommand = "%s"\n' "$LAUNCHER" >> "\$config"
  echo "    ajouté"
fi
EOF

cat <<'EOF'

Installé. Pour que les agents joignent Android, il reste à associer une fois la
console au débogage sans fil : voir thor/computer-use/README.md.
Le pont appelle /opt/thor-android/start.sh à son démarrage.
EOF
