# shellcheck shell=bash
# Bloc délimité par deux marqueurs dans un fichier de consignes des agents
# (~/.claude/CLAUDE.md, ~/.codex/AGENTS.md). À sourcer : install.sh et
# uninstall.sh l'envoient tel quel à la console. Le reste du fichier n'est
# jamais modifié.
#
#   block_put FICHIER CONTENU   pose le bloc (contenu du fichier CONTENU) :
#                               remplace le bloc existant, sinon l'ajoute à la
#                               fin après une ligne vide, sinon crée le fichier.
#   block_drop FICHIER          retire le bloc et la ligne vide qui le précède ;
#                               efface le fichier s'il ne reste rien (0 octet).

THOR_BLOCK_BEGIN='<!-- thor-android:début -->'
THOR_BLOCK_END='<!-- thor-android:fin -->'

# Vrai si FICHIER a au plus un bloc, bien fermé.
_block_check() {
  awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" '
    $0 == b { if (open || seen) exit 1; open = 1; seen = 1; next }
    $0 == e { if (!open) exit 1; open = 0 }
    END { if (open) exit 1 }
  ' "$1" || {
    printf 'marqueurs thor-android incohérents dans %s : à corriger à la main.\n' "$1" >&2
    return 1
  }
}

# Écrit $2 dans $1 sans changer l'inode ni les droits de $1, et seulement si
# le contenu change. Affiche « inchangé » ou $3.
_block_write() {
  if [ -e "$1" ] && cmp -s "$2" "$1"; then
    echo "inchangé"
  else
    cat "$2" > "$1"
    echo "$3"
  fi
}

block_put() {
  local file=$1 content=$2 tmp status
  if [ -e "$file" ]; then
    _block_check "$file" || return 1
  fi
  mkdir -p "$(dirname "$file")"
  tmp=$(mktemp "$(dirname "$file")/.thor-block.XXXXXX") || return 1
  {
    printf '%s\n' "$THOR_BLOCK_BEGIN"
    cat "$content"
    [ -z "$(tail -c 1 "$content")" ] || echo
    printf '%s\n' "$THOR_BLOCK_END"
  } > "$tmp.blk"
  if [ ! -s "$file" ]; then
    cp "$tmp.blk" "$tmp"
    status="créé"
  elif grep -qxF "$THOR_BLOCK_BEGIN" "$file"; then
    awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" -v blk="$tmp.blk" '
      skip { if ($0 == e) skip = 0; next }
      $0 == b { while ((getline line < blk) > 0) print line; skip = 1; next }
      { print }
    ' "$file" > "$tmp"
    status="remplacé"
  else
    {
      cat "$file"
      [ -z "$(tail -c 1 "$file")" ] || echo
      echo
      cat "$tmp.blk"
    } > "$tmp"
    status="ajouté"
  fi
  _block_write "$file" "$tmp" "$status"
  rm -f "$tmp" "$tmp.blk"
}

block_drop() {
  local file=$1 tmp
  if [ ! -e "$file" ] || ! grep -qxF "$THOR_BLOCK_BEGIN" "$file"; then
    echo "pas de bloc"
    return 0
  fi
  _block_check "$file" || return 1
  tmp=$(mktemp "$(dirname "$file")/.thor-block.XXXXXX") || return 1
  # Une ligne vide est retenue jusqu'à la ligne suivante : elle part avec le
  # bloc si c'est son marqueur de début.
  awk -v b="$THOR_BLOCK_BEGIN" -v e="$THOR_BLOCK_END" '
    skip { if ($0 == e) skip = 0; next }
    $0 == b { held = 0; skip = 1; next }
    { if (held) print ""; held = 0 }
    $0 == "" { held = 1; next }
    { print }
    END { if (held) print "" }
  ' "$file" > "$tmp"
  if [ -s "$tmp" ]; then
    _block_write "$file" "$tmp" "retiré"
  else
    rm -f "$file"
    echo "retiré, fichier vide effacé"
  fi
  rm -f "$tmp"
}
