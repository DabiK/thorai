#!/bin/bash
# Coupe l'accès des agents à Android : retire le serveur thor_android des deux
# agents, arrête le serveur adb et efface ce que install.sh a posé, y compris la
# clé adb de la Debian. Lancé depuis le Mac. Idempotent.
#
# Le paquet Debian adb reste installé : sans clé associée, il ne peut rien.
# Côté console, couper aussi le Débogage sans fil (voir README.md).
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

require_console

echo "1/3 Claude Code et fichiers de dev"
debian_sh dev <<'EOF'
set -euo pipefail
if jq -e '.mcpServers.thor_android' ~/.claude.json >/dev/null 2>&1; then
  claude mcp remove --scope user thor_android
fi
[ -x /opt/thor-android/stop.sh ] && /opt/thor-android/stop.sh || true
rm -f ~/.local/bin/thor-android-mcp ~/.local/bin/thor-screen
rm -rf ~/.local/state/thor-android
rm -f ~/.android/adbkey ~/.android/adbkey.pub
EOF

echo "2/3 /opt/thor-android"
debian_sh root <<'EOF'
rm -rf /opt/thor-android
EOF

echo "3/3 Codex"
termux_sh <<'EOF'
set -euo pipefail
config=$HOME/.codex/config.toml
if grep -q '^\[mcp_servers\.thor_android[].]' "$config"; then
  tmp=$(mktemp)
  awk '
    /^\[/ { skip = ($0 ~ /^\[mcp_servers\.thor_android[].]/) }
    !skip { print }
  ' "$config" > "$tmp"
  # Retire la ligne vide laissée en fin de fichier par install.sh.
  sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' "$tmp" > "$config"
  rm -f "$tmp"
  echo "    retiré"
fi
EOF

echo "Accès coupé côté Debian. Sur la console, désactivez aussi le Débogage sans fil."
