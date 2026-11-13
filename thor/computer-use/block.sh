# shellcheck shell=bash
# Bloc délimité par deux marqueurs dans un fichier de consignes des agents
# (~/.claude/CLAUDE.md, ~/.codex/AGENTS.md). À sourcer : install.sh et
# uninstall.sh l'envoient tel quel à la console. Le reste du fichier n'est
# pas modifié, sauf un saut de ligne final ajouté s'il manquait.
#
#   block_put FICHIER CONTENU   pose le bloc (contenu du fichier CONTENU) :
#                               remplace le bloc existant, sinon l'ajoute à la
#                               fin après une ligne vide, sinon crée le fichier.
#   block_drop FICHIER          retire le bloc et la ligne vide qui le précède ;
#                               efface le fichier s'il ne reste rien (0 octet).
#
# Les deux affichent ce qu'elles ont fait et renvoient 0, ou renvoient 1 avec
# un message sur la sortie d'erreur ; le fichier est alors laissé tel quel.

THOR_BLOCK_BEGIN='<!-- thor-android:début -->'
THOR_BLOCK_END='<!-- thor-android:fin -->'

_block_fail() {
  printf 'consignes thor-android : %s\n' "$*" >&2
  return 1
}

# Vrai si FICHIER a au plus un bloc, bien fermé.
_block_check() {
  awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" '
    $0 == b { if (open || seen) exit 1; open = 1; seen = 1; next }
    $0 == e { if (!open) exit 1; open = 0 }
    END { if (open) exit 1 }
  ' "$1" || _block_fail "marqueurs incohérents dans $1 : à corriger à la main."
}

# Écrit $2 dans $1 sans changer l'inode ni les droits de $1, et seulement si
# le contenu change. Affiche « inchangé » ou $3.
_block_write() {
  if [ -e "$1" ] && cmp -s "$2" "$1"; then
    echo "inchangé"
  else
    cat "$2" > "$1" || _block_fail "écriture impossible dans $1." || return 1
    echo "$3"
  fi
}

# Fabrique dans $tmp le nouveau contenu de $file. Utilise $tmp.blk.
_block_build() {
  local file=$1 content=$2 tmp=$3
  {
    printf '%s\n' "$THOR_BLOCK_BEGIN" &&
      cat "$content" &&
      { [ -z "$(tail -c 1 "$content")" ] || echo; } &&
      printf '%s\n' "$THOR_BLOCK_END"
  } > "$tmp.blk" || return 1
  if [ ! -s "$file" ]; then
    cp "$tmp.blk" "$tmp" && echo "créé"
  elif grep -qxF "$THOR_BLOCK_BEGIN" "$file"; then
    awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" -v blk="$tmp.blk" '
      skip { if ($0 == e) skip = 0; next }
      $0 == b { while ((getline line < blk) > 0) print line; skip = 1; next }
      { print }
    ' "$file" > "$tmp" && echo "remplacé"
  else
    {
      cat "$file" &&
        { [ -z "$(tail -c 1 "$file")" ] || echo; } &&
        echo &&
        cat "$tmp.blk"
    } > "$tmp" && echo "ajouté"
  fi
}

block_put() {
  local file=$1 content=$2 tmp status rc=0
  if [ -e "$file" ]; then
    _block_check "$file" || return 1
  fi
  mkdir -p "$(dirname "$file")" || _block_fail "dossier de $file impossible à créer." || return 1
  tmp=$(mktemp "$(dirname "$file")/.thor-block.XXXXXX") ||
    _block_fail "fichier temporaire impossible à créer à côté de $file." || return 1
  if status=$(_block_build "$file" "$content" "$tmp"); then
    _block_write "$file" "$tmp" "$status" || rc=1
  else
    _block_fail "nouveau contenu de $file impossible à préparer."
    rc=1
  fi
  rm -f "$tmp" "$tmp.blk"
  return "$rc"
}

block_drop() {
  local file=$1 tmp rc=0
  if [ ! -e "$file" ] || ! grep -qxF "$THOR_BLOCK_BEGIN" "$file"; then
    echo "pas de bloc"
    return 0
  fi
  _block_check "$file" || return 1
  tmp=$(mktemp "$(dirname "$file")/.thor-block.XXXXXX") ||
    _block_fail "fichier temporaire impossible à créer à côté de $file." || return 1
  # Une ligne vide est retenue jusqu'à la ligne suivante : elle part avec le
  # bloc si c'est son marqueur de début.
  if ! awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" '
    skip { if ($0 == e) skip = 0; next }
    $0 == b { held = 0; skip = 1; next }
    { if (held) print ""; held = 0 }
    $0 == "" { held = 1; next }
    { print }
    END { if (held) print "" }
  ' "$file" > "$tmp"; then
    _block_fail "nouveau contenu de $file impossible à préparer."
    rc=1
  elif [ -s "$tmp" ]; then
    _block_write "$file" "$tmp" "retiré" || rc=1
  elif rm -f "$file"; then
    echo "retiré, fichier vide effacé"
  else
    _block_fail "$file impossible à effacer."
    rc=1
  fi
  rm -f "$tmp"
  return "$rc"
}
