#!/bin/bash
# Coupe l'accès des agents à Android : retire le serveur thor_android des deux
# agents et leurs consignes thor-android, arrête le serveur adb et efface ce
# que install.sh a posé, y compris la clé adb de la Debian. Lancé depuis le
# Mac. Idempotent.
#
# Le paquet Debian adb reste installé : sans clé associée, il ne peut rien.
# Côté console, couper aussi le Débogage sans fil (voir README.md).
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

require_console

echo "1/3 Claude Code, consignes et fichiers de dev"
{
  cat "$HERE/block.sh"
  printf 'wrapper_tag=%q\nsocket_dir=%q\n' "$ADB_WRAPPER_TAG" "$ADB_SOCKET_DIR"
  cat <<'EOF'
set -euo pipefail
if jq -e '.mcpServers.thor_android' ~/.claude.json >/dev/null 2>&1; then
  claude mcp remove --scope user thor_android
fi
status=$(block_drop "$HOME/.claude/CLAUDE.md")
echo "    consignes Claude : $status"
status=$(block_drop "$HOME/.codex/AGENTS.md")
echo "    consignes Codex : $status"
if [ -x /opt/thor-android/stop.sh ]; then
  /opt/thor-android/stop.sh || true
fi
rm -f ~/.local/bin/thor-android-mcp ~/.local/bin/thor-screen
if [ -e ~/.local/bin/adb ] && grep -qF "$wrapper_tag" ~/.local/bin/adb; then
  rm -f ~/.local/bin/adb
fi
rm -rf "$socket_dir" ~/.local/state/thor-android
rm -f ~/.android/adbkey ~/.android/adbkey.pub
EOF
} | debian_sh dev

echo "2/3 /opt/thor-android"
debian_sh root <<'EOF'
rm -rf /opt/thor-android
EOF

echo "3/3 Codex"
{
  emit_toml_helpers
  cat <<'EOF'
set -euo pipefail
config=$HOME/.codex/config.toml
if [ -e "$config" ] && grep -q '^[[:space:]]*\[mcp_servers\.thor_android[].]' "$config"; then
  drop_thor_android
  echo "    retiré"
fi
EOF
} | termux_sh

echo "Accès coupé côté Debian. Sur la console, désactivez aussi le Débogage sans fil."
