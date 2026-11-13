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

# Chaque étape va jusqu'au bout même si une autre échoue : l'accès est coupé
# d'abord (serveurs, déclarations, fichiers), les consignes sont retirées en
# dernier. Les échecs sont rappelés à la fin, avec un code de sortie 1.
failed=()

echo "1/4 Debian (dev) : serveurs, Claude Code, fichiers de dev"
{
  printf 'wrapper_tag=%q\nsocket_dir=%q\n' "$ADB_WRAPPER_TAG" "$ADB_SOCKET_DIR"
  cat <<'EOF'
set -uo pipefail
rc=0
fail() { echo "    ERREUR : $*" >&2; rc=1; }
# </dev/null : aucune commande ne doit lire l'entrée standard, d'où vient ce
# script (lib.sh, debian_sh).
if [ -x /opt/thor-android/stop.sh ]; then
  /opt/thor-android/stop.sh </dev/null || fail "arrêt du serveur adb (stop.sh)"
fi
if [ ! -e ~/.claude.json ]; then
  : # Claude n'a aucune déclaration.
elif ! command -v jq >/dev/null 2>&1; then
  fail "jq absent : retirer thor_android de Claude à la main (claude mcp remove --scope user thor_android)"
elif ! jq empty ~/.claude.json </dev/null >/dev/null 2>&1; then
  fail "~/.claude.json illisible : retirer thor_android de Claude à la main (claude mcp remove --scope user thor_android)"
elif jq -e '.mcpServers.thor_android' ~/.claude.json </dev/null >/dev/null 2>&1; then
  claude mcp remove --scope user thor_android </dev/null || fail "retrait de thor_android de Claude"
fi
rm -f ~/.local/bin/thor-android-mcp ~/.local/bin/thor-screen || fail "lanceurs"
if [ -e ~/.local/bin/adb ] && grep -qF "$wrapper_tag" ~/.local/bin/adb; then
  rm -f ~/.local/bin/adb || fail "cale adb"
fi
rm -rf "$socket_dir" ~/.local/state/thor-android || fail "dossier de la socket"
rm -f ~/.android/adbkey ~/.android/adbkey.pub || fail "clé adb"
exit "$rc"
EOF
} | debian_sh dev || failed+=("1/4 serveurs, Claude Code ou fichiers de dev")

echo "2/4 /opt/thor-android"
debian_sh root <<'EOF' || failed+=("2/4 /opt/thor-android")
rm -rf /opt/thor-android
EOF

echo "3/4 Codex : déclaration thor_android"
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
} | termux_sh || failed+=("3/4 déclaration Codex")

echo "4/4 Consignes des agents"
{
  cat "$HERE/block.sh"
  cat <<'EOF'
set -uo pipefail
rc=0
for target in "Claude:$HOME/.claude/CLAUDE.md" "Codex:$HOME/.codex/AGENTS.md"; do
  if result=$(block_drop "${target#*:}"); then
    echo "    ${target%%:*} : $result"
  else
    echo "    ERREUR : consignes de ${target%%:*} non retirées (${target#*:})." >&2
    rc=1
  fi
done
exit "$rc"
EOF
} | debian_sh dev || failed+=("4/4 consignes (bloc à retirer à la main, entre ses deux marqueurs)")

if [ ${#failed[@]} -gt 0 ]; then
  echo "Coupure incomplète. Étapes en échec :" >&2
  printf '  - %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "Accès coupé côté Debian. Sur la console, désactivez aussi le Débogage sans fil."
