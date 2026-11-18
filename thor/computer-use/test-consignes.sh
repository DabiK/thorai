#!/bin/bash
# Contrat des consignes livrées, sans console ni agent. L'effet visible exige
# aussi l'essai réel décrit dans README.md (une consigne n'est pas un routage).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/block.sh"
CONTENT=${1:-$HERE/consignes.md}
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

for profile in .claude/CLAUDE.md .codex/AGENTS.md .config/opencode/AGENTS.md; do
  target=$W/$profile
  mkdir -p "$(dirname "$target")"
  printf '# Consignes personnelles\n\n%s\nAncien accès Android\n%s\n\nFin personnelle\n' \
    "$THOR_BLOCK_BEGIN" "$THOR_BLOCK_END" > "$target"
  block_put "$target" "$CONTENT" >/dev/null
  # Ces clauses protègent le contrat de visibilité, pas le comportement du LLM.
  for clause in \
    'console, de l’écran, d’une appli ou du navigateur' \
    'agis sur Android avec `thor_android` ou `thor-screen`' \
    '`thor_browser` est réservé à l’automatisation web invisible' \
    'dis explicitement que cette action est invisible' \
    'mobile_open_url' \
    '/opt/thor-android/start.sh' \
    'android.intent.action.VIEW' \
    'navigateur Android par défaut'; do
    if ! grep -qF "$clause" "$target"; then
      echo "ECHEC $profile : clause absente : $clause" >&2
      exit 1
    fi
  done
  cp "$target" "$W/once"
  block_put "$target" "$CONTENT" >/dev/null
  cmp "$W/once" "$target"
  block_drop "$target" >/dev/null
  printf '# Consignes personnelles\n\nFin personnelle\n' > "$W/expected"
  cmp "$W/expected" "$target"
  echo "ok $profile : contrat visible/invisible, remplacement, idempotence, reste conservé"
done
