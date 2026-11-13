#!/bin/bash
# Test de thor/computer-use/block.sh : block_put / block_drop.
#   thor/computer-use/test-block.sh [chemin de block.sh]
set -uo pipefail
# shellcheck source=block.sh
. "${1:-$(dirname "$0")/block.sh}"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
fails=0
check() { # nom attendu-fichier obtenu-fichier
  if cmp -s "$2" "$3"; then echo "ok   $1"; else echo "ECHEC $1"; diff "$2" "$3" | head -20; fails=$((fails + 1)); fi
}
printf 'Ligne A\nLigne B accentuée\n' > "$W/c1"
printf 'Autre consigne\n' > "$W/c2"
B="<!-- thor-android:début -->"
E="<!-- thor-android:fin -->"
{ echo "$B"; cat "$W/c1"; echo "$E"; } > "$W/blk1"
{ echo "$B"; cat "$W/c2"; echo "$E"; } > "$W/blk2"

# 1. fichier absent : créé avec le bloc seul
f=$W/d1/sub/AGENTS.md
block_put "$f" "$W/c1" >/dev/null
check "absent: créé" "$W/blk1" "$f"
cp "$f" "$W/s"; block_put "$f" "$W/c1" >/dev/null; check "absent: 2e passage identique" "$W/s" "$f"
block_drop "$f" >/dev/null
if [ ! -e "$f" ]; then echo "ok   absent: retrait efface le fichier vide"; else echo "ECHEC absent: fichier resté"; fails=$((fails + 1)); fi

# 2. fichier avec contenu : bloc ajouté après une ligne vide, retour à l'octet près
f=$W/user.md
printf '# Mes consignes\n\n- réponds en français\n' > "$f"; cp "$f" "$W/orig"
block_put "$f" "$W/c1" >/dev/null
{ cat "$W/orig"; echo; cat "$W/blk1"; } > "$W/exp"; check "contenu: ajout" "$W/exp" "$f"
cp "$f" "$W/s"; block_put "$f" "$W/c1" >/dev/null; check "contenu: 2e passage identique" "$W/s" "$f"
block_drop "$f" >/dev/null; check "contenu: retrait à l'octet près" "$W/orig" "$f"

# 3. bloc déjà présent au milieu : rien ne bouge
f=$W/mid.md
{ printf 'avant\n\n'; cat "$W/blk1"; printf '\naprès\n'; } > "$f"; cp "$f" "$W/s"
block_put "$f" "$W/c1" >/dev/null; check "présent: inchangé" "$W/s" "$f"

# 4. bloc modifié : remplacé sur place, le reste intact
block_put "$f" "$W/c2" >/dev/null
{ printf 'avant\n\n'; cat "$W/blk2"; printf '\naprès\n'; } > "$W/exp"; check "modifié: remplacé sur place" "$W/exp" "$f"

# 5. retrait d'un bloc au milieu : la ligne vide qui le précède part avec lui
block_drop "$f" >/dev/null
printf 'avant\n\nafter\n' | sed 's/after/après/' > "$W/exp"; check "retrait au milieu" "$W/exp" "$f"

# 6. fichier se terminant par une ligne vide : retour à l'octet près
f=$W/blank.md; printf 'x\n\n' > "$f"; cp "$f" "$W/orig"
block_put "$f" "$W/c1" >/dev/null; block_drop "$f" >/dev/null; check "ligne vide finale: aller-retour" "$W/orig" "$f"

# 7. retrait sans bloc : rien ne bouge
f=$W/none.md; printf 'rien\n' > "$f"; cp "$f" "$W/orig"
block_drop "$f" >/dev/null; check "retrait sans bloc: inchangé" "$W/orig" "$f"
block_drop "$W/absent.md" >/dev/null
if [ ! -e "$W/absent.md" ]; then echo "ok   retrait fichier absent: rien créé"; else echo "ECHEC retrait absent"; fails=$((fails + 1)); fi

# 8. droits gardés
f=$W/mode.md; printf 'y\n' > "$f"; chmod 600 "$f"
block_put "$f" "$W/c1" >/dev/null
m=$(stat -f %Sp "$f" 2>/dev/null || stat -c %A "$f")
if [ "$m" = "-rw-------" ]; then echo "ok   droits gardés"; else echo "ECHEC droits: $m"; fails=$((fails + 1)); fi

# 9. marqueur de début sans fin : refus, fichier intact
f=$W/broken.md; printf 'a\n%s\nb\n' "$B" > "$f"; cp "$f" "$W/orig"
if block_put "$f" "$W/c1" >/dev/null 2>&1; then echo "ECHEC bloc cassé accepté"; fails=$((fails + 1)); else echo "ok   bloc cassé refusé"; fi
check "bloc cassé: intact" "$W/orig" "$f"
if block_drop "$f" >/dev/null 2>&1; then echo "ECHEC retrait bloc cassé accepté"; fails=$((fails + 1)); else echo "ok   retrait bloc cassé refusé"; fi
check "bloc cassé: intact après retrait" "$W/orig" "$f"

# 10. fichier vide préexistant : bloc seul, puis effacé au retrait (décision notée)
f=$W/empty.md; : > "$f"
block_put "$f" "$W/c1" >/dev/null; check "vide: bloc seul" "$W/blk1" "$f"

echo "échecs : $fails"
[ "$fails" -eq 0 ]
