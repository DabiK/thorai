#!/bin/bash
# Installe l'accès des agents à Android (serveur MCP thor_android) sur la console.
# Lancé depuis le Mac, console branchée en USB. Idempotent.
#
#   thor/computer-use/install.sh            (appareil adb unique, sinon THOR_SERIAL)
#
# Pose sur la console :
#   Debian  : paquets adb et procps ; /opt/thor-android (mobile-mcp épinglé, start.sh,
#             stop.sh, pair.sh) ; ~dev/.local/bin/thor-android-mcp, thor-screen
#             et adb (cale qui impose la socket privée du serveur adb)
#   Consignes : bloc thor-android (consignes.md) dans ~dev/.claude/CLAUDE.md et
#             ~/.codex/AGENTS.md, entre deux marqueurs ; le reste est intact
#   Claude  : serveur thor_android en portée utilisateur (~dev/.claude.json) ;
#             outils thor_android, thor-screen et adb autorisés sans validation
#             (permissions.allow de ~dev/.claude/settings.json, permissions.sh)
#   Codex   : bloc [mcp_servers.thor_android] dans ~/.codex/config.toml (Termux),
#             outils approuvés d'office ; adb et thor-screen sans validation
#             (~/.codex/rules/thor-android.rules)
#
# mobile-mcp tourne en mode mobilecli. Pour le mode adb seul, créer
# ~dev/.config/thor-android/adb-seul (voir README.md) : ce script n'y touche pas.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

MOBILE_MCP_VERSION=1.0.17
LAUNCHER=/home/dev/.local/bin/thor-android-mcp
ADB_WRAPPER=/home/dev/.local/bin/adb

require_console

echo "1/5 Debian (root) : adb, mobile-mcp $MOBILE_MCP_VERSION, scripts de /opt/thor-android"
{
  cat <<EOF
set -euo pipefail
# adb, et procps pour pgrep (stop.sh, pair.sh).
missing=
command -v adb >/dev/null 2>&1 || missing="\$missing adb"
command -v pgrep >/dev/null 2>&1 || missing="\$missing procps"
if [ -n "\$missing" ]; then
  apt-get update -qq </dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \$missing </dev/null >/dev/null
fi
mkdir -p /opt/thor-android
cd /opt/thor-android
[ -f package.json ] || echo '{"name":"thor-android","private":true}' > package.json
current=\$(node -p "require('./node_modules/@mobilenext/mobile-mcp/package.json').version" </dev/null 2>/dev/null || true)
if [ "\$current" != "$MOBILE_MCP_VERSION" ]; then
  npm install --save-exact --omit=dev --no-audit --no-fund --loglevel=error @mobilenext/mobile-mcp@$MOBILE_MCP_VERSION </dev/null
fi
EOF
  emit_file "$HERE/start.sh" /opt/thor-android/start.sh 755
  emit_file "$HERE/stop.sh" /opt/thor-android/stop.sh 755
  emit_file "$HERE/pair.sh" /opt/thor-android/pair.sh 755
} | debian_sh root

echo "2/5 Debian (dev) : thor-android-mcp, thor-screen et la cale adb"
{
  emit_file "$HERE/thor-android-mcp" "$LAUNCHER" 755
  emit_file "$HERE/thor-screen" /home/dev/.local/bin/thor-screen 755
  printf 'wrapper=%q\nwrapper_tag=%q\n' "$ADB_WRAPPER" "$ADB_WRAPPER_TAG"
  cat <<'EOF'
if [ -e "$wrapper" ] && ! grep -qF "$wrapper_tag" "$wrapper"; then
  echo "    ATTENTION : $wrapper existe et ne vient pas de thor-android : laissé tel quel." >&2
else
EOF
  emit_file "$HERE/adb-wrapper" "$ADB_WRAPPER" 755
  echo 'fi'
  cat <<'EOF'
set -euo pipefail
mobilecli=$(node -p "require('/opt/thor-android/node_modules/@mobilenext/mobile-mcp/lib/mobilecli.js').Mobilecli.getMobilecliPath()" </dev/null)
echo "    $("$mobilecli" --version </dev/null), adb $(/usr/bin/adb version </dev/null | sed -n 's/^Version //p')"
EOF
} | debian_sh dev

echo "3/5 Consignes des agents : ~/.claude/CLAUDE.md et ~/.codex/AGENTS.md"
{
  cat "$HERE/block.sh"
  printf 'consignes_b64=%q\n' "$(base64 < "$HERE/consignes.md" | tr -d '\n')"
  cat <<'EOF'
set -uo pipefail
content=$(mktemp) || exit 1
trap 'rm -f "$content"' EXIT
printf '%s' "$consignes_b64" | base64 -d > "$content" || exit 1
rc=0
for target in "Claude:$HOME/.claude/CLAUDE.md" "Codex:$HOME/.codex/AGENTS.md" "opencode:$HOME/.config/opencode/AGENTS.md"; do
  if result=$(block_put "${target#*:}" "$content"); then
    echo "    ${target%%:*} : $result"
  else
    echo "    ERREUR : consignes de ${target%%:*} non posées (${target#*:})." >&2
    rc=1
  fi
done
if [ -e "$HOME/.codex/AGENTS.override.md" ]; then
  echo "    ATTENTION : ~/.codex/AGENTS.override.md existe ; Codex le lit à la place d'AGENTS.md." >&2
fi
exit "$rc"
EOF
} | debian_sh dev || {
  echo "Arrêt avant les déclarations MCP (étapes 4 et 5) : les consignes ne sont pas posées." >&2
  echo "Une déclaration thor_android posée par une installation précédente reste en place :" >&2
  echo "corrigez les consignes puis relancez install.sh, ou coupez l'accès avec uninstall.sh." >&2
  exit 1
}

echo "4/5 Claude Code : serveur thor_android (portée utilisateur), sans validation"
CLAUDE_ENTRY="{\"type\":\"stdio\",\"command\":\"$LAUNCHER\",\"args\":[],\"env\":{\"ADB_SERVER_SOCKET\":\"$ADB_SOCKET\"}}"
{
  cat "$HERE/permissions.sh"
  printf 'want=%q\n' "$CLAUDE_ENTRY"
  cat <<'EOF'
set -euo pipefail
if jq -e --argjson want "$want" '.mcpServers.thor_android == $want' ~/.claude.json >/dev/null 2>&1; then
  echo "    déjà déclaré"
else
  if jq -e '.mcpServers.thor_android' ~/.claude.json >/dev/null 2>&1; then
    /home/dev/.local/bin/claude mcp remove --scope user thor_android </dev/null >/dev/null
    echo "    ancienne déclaration retirée"
  fi
  /home/dev/.local/bin/claude mcp add-json --scope user thor_android "$want" </dev/null
fi
result=$(claude_allow_put ~/.claude/settings.json)
echo "    autorisations (~/.claude/settings.json) : $result"
EOF
} | debian_sh dev

echo "5/5 Codex : serveur thor_android (~/.codex/config.toml de Termux), sans validation"
{
  printf 'want=%q\n' "$(printf '[mcp_servers.thor_android]\ncommand = "%s"\nenv = { ADB_SERVER_SOCKET = "%s" }\ndefault_tools_approval_mode = "approve"' "$LAUNCHER" "$ADB_SOCKET")"
  printf 'rules_b64=%q\n' "$(base64 < "$HERE/thor-android.rules" | tr -d '\n')"
  emit_toml_helpers
  cat <<'EOF'
set -euo pipefail
config=$HOME/.codex/config.toml
mkdir -p "$(dirname "$config")"
if [ ! -e "$config" ]; then
  (umask 077 && touch "$config")
fi
# Lignes actuelles de [mcp_servers.thor_android] et de ses sous-tables.
current=$(awk '/^[ \t]*\[/ { s = ($0 ~ /^[ \t]*\[mcp_servers\.thor_android[].]/) } s' "$config")
if [ "$current" = "$want" ]; then
  echo "    déjà déclaré"
else
  if [ -n "$current" ]; then
    drop_thor_android
    echo "    ancienne déclaration retirée"
  fi
  printf '\n%s\n' "$want" >> "$config"
  echo "    ajouté"
fi
# Règles d'exécution : adb et thor-screen sans validation.
rules=$HOME/.codex/rules/thor-android.rules
mkdir -p "$(dirname "$rules")"
tmp=$(mktemp "$rules.XXXXXX")
printf '%s' "$rules_b64" | base64 -d > "$tmp"
if cmp -s "$tmp" "$rules"; then
  rm -f "$tmp"
  echo "    règles déjà posées (~/.codex/rules/thor-android.rules)"
else
  chmod 644 "$tmp"
  mv -f "$tmp" "$rules"
  echo "    règles posées (~/.codex/rules/thor-android.rules)"
fi
EOF
} | termux_sh

echo "opencode : serveur thor_android dans sa configuration globale native"
debian_sh dev <<'EOF'
set -euo pipefail
/home/dev/.opencode/bin/opencode mcp add --global --env ADB_SERVER_SOCKET=localfilesystem:/home/dev/.thor-adb/server thor_android -- /home/dev/.local/bin/thor-android-mcp </dev/null
EOF

cat <<'EOF'

Installé. Pour que les agents joignent Android, il reste à associer une fois la
console au débogage sans fil : voir thor/computer-use/README.md.
Le pont appelle /opt/thor-android/start.sh à son démarrage.
EOF
