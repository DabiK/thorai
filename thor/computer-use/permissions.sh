# shellcheck shell=bash
# Autorisations de Claude Code pour piloter Android sans validation : quatre
# entrées de permissions.allow dans ~/.claude/settings.json. À sourcer :
# install.sh et uninstall.sh l'envoient tel quel à la console. Demande jq.
#
#   claude_allow_put FICHIER    ajoute les entrées qui manquent, à la fin de
#                               permissions.allow ; crée le fichier s'il manque.
#                               Ne réécrit rien si elles y sont toutes.
#   claude_allow_drop FICHIER   retire ces entrées, et seulement elles. Retire
#                               aussi permissions.allow, puis permissions, s'ils
#                               deviennent vides ; efface le fichier s'il ne
#                               reste que {}.
#
# Les deux affichent ce qu'elles ont fait et renvoient 0, ou renvoient 1 avec
# un message sur la sortie d'erreur, sans toucher au fichier (JSON illisible,
# permissions qui n'est pas un objet, allow qui n'est pas une liste…).
# Le fichier est réécrit sur place (même inode, mêmes droits), au format de jq
# (indentation de deux espaces, celui de Claude Code).

# Outils thor_android, thor-screen, adb (la cale de ~/.local/bin), et lecture
# des captures de thor-screen, rangées dans /tmp/thor-screen (consignes.md).
THOR_CLAUDE_ALLOW='["mcp__thor_android__*","Bash(thor-screen *)","Bash(adb *)","Read(//tmp/thor-screen/**)"]'

_allow_fail() {
  printf 'autorisations thor-android : %s\n' "$*" >&2
  return 1
}

# Vrai si $1 est un objet JSON dont permissions (s'il existe) est un objet et
# permissions.allow (s'il existe) une liste.
_allow_check() {
  jq -e 'type == "object"
    and ((.permissions // {}) | type == "object")
    and ((.permissions.allow // []) | type == "array")' "$1" >/dev/null 2>&1 ||
    _allow_fail "$1 n'est pas un réglage de Claude lisible (objet JSON, permissions.allow en liste) : à corriger à la main."
}

# Écrit $2 dans $1 sans changer l'inode ni les droits de $1.
_allow_write() {
  cat "$2" > "$1" || _allow_fail "écriture impossible dans $1."
}

claude_allow_put() {
  local file=$1 tmp rc=0
  if [ -s "$file" ]; then
    [ -r "$file" ] || _allow_fail "$file illisible." || return 1
    _allow_check "$file" || return 1
    if jq -e --argjson want "$THOR_CLAUDE_ALLOW" \
      '($want - (.permissions.allow // [])) == []' "$file" >/dev/null; then
      echo "déjà autorisé"
      return 0
    fi
  fi
  mkdir -p "$(dirname "$file")" || _allow_fail "dossier de $file impossible à créer." || return 1
  tmp=$(mktemp "$(dirname "$file")/.thor-allow.XXXXXX") ||
    _allow_fail "fichier temporaire impossible à créer à côté de $file." || return 1
  if [ -s "$file" ]; then
    jq --argjson want "$THOR_CLAUDE_ALLOW" \
      '.permissions.allow = ((.permissions.allow // []) + ($want - (.permissions.allow // [])))' \
      "$file" > "$tmp"
  else
    jq -n --argjson want "$THOR_CLAUDE_ALLOW" '{permissions: {allow: $want}}' > "$tmp"
  fi || { _allow_fail "nouveau contenu de $file impossible à préparer."; rc=1; }
  if [ "$rc" -eq 0 ]; then
    if _allow_write "$file" "$tmp"; then echo "autorisé"; else rc=1; fi
  fi
  rm -f "$tmp"
  return "$rc"
}

claude_allow_drop() {
  local file=$1 tmp rc=0
  if [ ! -s "$file" ]; then
    echo "rien à retirer"
    return 0
  fi
  [ -r "$file" ] || _allow_fail "$file illisible." || return 1
  _allow_check "$file" || return 1
  if jq -e --argjson want "$THOR_CLAUDE_ALLOW" \
    '((.permissions.allow // []) - $want) == (.permissions.allow // [])' "$file" >/dev/null; then
    echo "rien à retirer"
    return 0
  fi
  tmp=$(mktemp "$(dirname "$file")/.thor-allow.XXXXXX") ||
    _allow_fail "fichier temporaire impossible à créer à côté de $file." || return 1
  if ! jq --argjson want "$THOR_CLAUDE_ALLOW" '
    .permissions.allow -= $want
    | if .permissions.allow == [] then del(.permissions.allow) else . end
    | if .permissions == {} then del(.permissions) else . end
  ' "$file" > "$tmp"; then
    _allow_fail "nouveau contenu de $file impossible à préparer."
    rc=1
  elif jq -e '. == {}' "$tmp" >/dev/null; then
    if rm -f "$file"; then
      echo "retiré, fichier vide effacé"
    else
      _allow_fail "$file impossible à effacer."
      rc=1
    fi
  elif _allow_write "$file" "$tmp"; then
    echo "retiré"
  else
    rc=1
  fi
  rm -f "$tmp"
  return "$rc"
}
