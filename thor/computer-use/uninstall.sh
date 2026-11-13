#!/bin/bash
# Coupe l'accès des agents à Android : retire le serveur thor_android des deux
# agents, leurs autorisations sans validation (quatre entrées de Claude, règles
# de Codex) et leurs consignes thor-android, arrête le serveur adb et efface ce
# que install.sh a posé, ainsi que le dossier que mobilecli a pu poser dans
# les données de Régie. Lancé depuis le Mac. Idempotent.
#
# La clé adb de la Debian est gardée, pour ne pas refaire l'association du
# débogage sans fil à chaque réinstallation (décision de l'utilisateur, 13
# novembre). Pour l'effacer aussi : uninstall.sh --oublier-association
#
# Le paquet Debian adb reste installé : sans clé associée, il ne peut rien.
# Côté console, couper aussi le Débogage sans fil (voir README.md).
set -euo pipefail

forget_pairing=0
case "${1:-}" in
  --oublier-association) forget_pairing=1 ;;
  "") ;;
  *) echo "usage : $0 [--oublier-association]" >&2; exit 2 ;;
esac

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

require_console

# Chaque étape va jusqu'au bout même si une autre échoue : l'accès est coupé
# d'abord (serveurs, déclarations, fichiers), les consignes sont retirées en
# dernier. Les échecs sont rappelés à la fin, avec un code de sortie 1.
failed=()

echo "1/5 Debian (dev) : serveurs, Claude Code, fichiers de dev"
{
  cat "$HERE/permissions.sh"
  printf 'wrapper_tag=%q\nsocket_dir=%q\nforget_pairing=%q\n' "$ADB_WRAPPER_TAG" "$ADB_SOCKET_DIR" "$forget_pairing"
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
if ! command -v jq >/dev/null 2>&1; then
  [ ! -s ~/.claude/settings.json ] ||
    fail "jq absent : retirer de permissions.allow (~/.claude/settings.json) les entrées $THOR_CLAUDE_ALLOW"
elif result=$(claude_allow_drop ~/.claude/settings.json </dev/null); then
  echo "    autorisations de Claude : $result"
else
  fail "autorisations de Claude : retirer à la main de permissions.allow (~/.claude/settings.json) les entrées $THOR_CLAUDE_ALLOW"
fi
rm -f ~/.local/bin/thor-android-mcp ~/.local/bin/thor-screen || fail "lanceurs"
if [ -e ~/.local/bin/adb ] && grep -qF "$wrapper_tag" ~/.local/bin/adb; then
  rm -f ~/.local/bin/adb || fail "cale adb"
fi
rm -rf "$socket_dir" ~/.local/state/thor-android || fail "dossier de la socket"
# Journal et pid du démon de mobilecli (stop.sh l'a arrêté).
rm -rf ~/.mobilecli || fail "dossier ~/.mobilecli"
if [ "$forget_pairing" = 1 ]; then
  rm -f ~/.android/adbkey ~/.android/adbkey.pub || fail "clé adb"
fi
exit "$rc"
EOF
} | debian_sh dev || failed+=("1/5 serveurs, Claude Code ou fichiers de dev")

echo "2/5 /opt/thor-android"
debian_sh root <<'EOF' || failed+=("2/5 /opt/thor-android")
rm -rf /opt/thor-android
EOF

echo "3/5 Codex : déclaration thor_android et règles"
{
  emit_toml_helpers
  cat <<'EOF'
set -euo pipefail
config=$HOME/.codex/config.toml
if [ -e "$config" ] && grep -q '^[[:space:]]*\[mcp_servers\.thor_android[].]' "$config"; then
  drop_thor_android
  echo "    retiré"
fi
rules=$HOME/.codex/rules/thor-android.rules
if [ -e "$rules" ]; then
  rm -f "$rules"
  echo "    règles retirées"
fi
# Le dossier des règles part s'il est vide (Codex y range aussi les siennes).
rmdir "$HOME/.codex/rules" 2>/dev/null || true
EOF
} | termux_sh || failed+=("3/5 déclaration ou règles de Codex")

echo "4/5 Régie : dossier de mobilecli"
# En mode mobilecli, mobile-mcp copie son agent JVMTI dans les données de
# l'appli débogable au premier plan. On retire ce dossier-là, rien d'autre.
# Régie absente ou non débogable : run-as refuse, il n'y a rien à faire.
regie_mobilecli=/data/data/app.regie/mobilecli
regie_out=$(adb -s "$THOR_SERIAL" shell -n "run-as app.regie sh -c 'if [ ! -e $regie_mobilecli ]; then echo absent; elif rm -rf $regie_mobilecli; then echo removed; else echo failed; fi'" 2>&1) || true
regie_out=$(tr -d '\r' <<<"$regie_out")
case "$(tail -n 1 <<<"$regie_out")" in
  absent) echo "    rien à retirer" ;;
  removed) echo "    $regie_mobilecli retiré" ;;
  *run-as:*) echo "    rien à faire : Régie absente ou non débogable ($(tail -n 1 <<<"$regie_out"))" ;;
  *)
    printf '    ERREUR : %s\n' "$regie_out" >&2
    failed+=("4/5 $regie_mobilecli (à retirer à la main : adb shell run-as app.regie rm -rf $regie_mobilecli)")
    ;;
esac

echo "5/5 Consignes des agents"
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
} | debian_sh dev || failed+=("5/5 consignes (bloc à retirer à la main, entre ses deux marqueurs)")

if [ ${#failed[@]} -gt 0 ]; then
  echo "Coupure incomplète. Étapes en échec :" >&2
  printf '  - %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "Accès coupé côté Debian. Sur la console, désactivez aussi le Débogage sans fil."
