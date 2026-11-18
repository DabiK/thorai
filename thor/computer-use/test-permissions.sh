#!/bin/bash
# Test des autorisations sans validation (ticket 14) :
#   - permissions.sh (claude_allow_put / claude_allow_drop), avec jq ;
#   - thor-android.rules, avec « codex execpolicy check » si codex est là.
#   thor/computer-use/test-permissions.sh
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=permissions.sh
. "$HERE/permissions.sh"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
fails=0
ok() { echo "ok   $1"; }
ko() { echo "ECHEC $1"; fails=$((fails + 1)); }
is() { # nom commande… : la commande doit réussir
  local name=$1; shift
  if "$@"; then ok "$name"; else ko "$name"; fi
}
check() { # nom attendu-fichier obtenu-fichier
  if cmp -s "$2" "$3"; then ok "$1"; else ko "$1"; diff "$2" "$3" | head -20; fi
}
# Contenu JSON attendu, au format de jq.
expect() { jq . > "$W/exp"; }

# 1. fichier absent : créé avec nos entrées ; retrait : effacé
f=$W/a/.claude/settings.json
claude_allow_put "$f" >/dev/null
expect <<'EOF'
{"permissions":{"allow":["mcp__thor_android__*","Bash(thor-screen *)","Bash(adb *)","Read(//tmp/thor-screen/**)"]}}
EOF
check "absent: créé" "$W/exp" "$f"
cp "$f" "$W/s"; out=$(claude_allow_put "$f")
check "absent: 2e passage identique" "$W/s" "$f"
is "absent: 2e passage annonce « déjà autorisé » ($out)" [ "$out" = "déjà autorisé" ]
claude_allow_drop "$f" >/dev/null
is "absent: retrait efface le fichier" [ ! -e "$f" ]

# 2. réglages existants (celui de la console) : ajout, puis retour à l'octet près
f=$W/settings.json
printf '{\n  "theme": "dark"\n}\n' > "$f"; cp "$f" "$W/orig"
claude_allow_put "$f" >/dev/null
expect <<'EOF'
{"theme":"dark","permissions":{"allow":["mcp__thor_android__*","Bash(thor-screen *)","Bash(adb *)","Read(//tmp/thor-screen/**)"]}}
EOF
check "existant: ajout" "$W/exp" "$f"
cp "$f" "$W/s"; claude_allow_put "$f" >/dev/null; check "existant: 2e passage identique" "$W/s" "$f"
claude_allow_drop "$f" >/dev/null; check "existant: retrait à l'octet près" "$W/orig" "$f"
claude_allow_drop "$f" >/dev/null; check "existant: 2e retrait sans effet" "$W/orig" "$f"

# 3. permissions de l'utilisateur : gardées, dans l'ordre ; une entrée déjà là
#    n'est pas doublée
f=$W/user.json
cat > "$f" <<'EOF'
{
  "permissions": {
    "allow": [
      "Bash(git status)",
      "Bash(adb *)"
    ],
    "deny": [
      "Bash(rm *)"
    ],
    "defaultMode": "default"
  },
  "model": "opus"
}
EOF
claude_allow_put "$f" >/dev/null
expect <<'EOF'
{"permissions":{"allow":["Bash(git status)","Bash(adb *)","mcp__thor_android__*","Bash(thor-screen *)","Read(//tmp/thor-screen/**)"],
 "deny":["Bash(rm *)"],"defaultMode":"default"},"model":"opus"}
EOF
check "utilisateur: complété sans doublon" "$W/exp" "$f"
claude_allow_drop "$f" >/dev/null
expect <<'EOF'
{"permissions":{"allow":["Bash(git status)"],"deny":["Bash(rm *)"],"defaultMode":"default"},"model":"opus"}
EOF
# Limite connue (README, ADR 03) : une entrée identique posée avant par
# l'utilisateur part aussi au retrait.
check "utilisateur: seules nos entrées partent" "$W/exp" "$f"

# 4. permissions sans allow : allow ajouté, puis retiré ; deny gardé
f=$W/deny.json
printf '{\n  "permissions": {\n    "deny": [\n      "Read(.env)"\n    ]\n  }\n}\n' > "$f"; cp "$f" "$W/orig"
claude_allow_put "$f" >/dev/null; claude_allow_drop "$f" >/dev/null
check "sans allow: aller-retour à l'octet près" "$W/orig" "$f"

# 5. fichier vide (0 octet) : traité comme absent
f=$W/empty.json; : > "$f"
claude_allow_put "$f" >/dev/null
expect <<'EOF'
{"permissions":{"allow":["mcp__thor_android__*","Bash(thor-screen *)","Bash(adb *)","Read(//tmp/thor-screen/**)"]}}
EOF
check "vide: entrées seules" "$W/exp" "$f"

# 6. JSON cassé ou de mauvaise forme : refus, fichier intact
for bad in '{"theme": ' '[]' '{"permissions": []}' '{"permissions": {"allow": "Bash(adb *)"}}'; do
  f=$W/bad.json; printf '%s\n' "$bad" > "$f"; cp "$f" "$W/orig"
  if claude_allow_put "$f" >/dev/null 2>&1; then ko "refus put: $bad"; else ok "refus put: $bad"; fi
  check "intact après put: $bad" "$W/orig" "$f"
  if claude_allow_drop "$f" >/dev/null 2>&1; then ko "refus drop: $bad"; else ok "refus drop: $bad"; fi
  check "intact après drop: $bad" "$W/orig" "$f"
done

# 7. droits gardés
f=$W/mode.json; printf '{}\n' > "$f"; chmod 600 "$f"
claude_allow_put "$f" >/dev/null
m=$(stat -c %A "$f" 2>/dev/null || stat -f %Sp "$f")
is "droits gardés ($m)" [ "$m" = "-rw-------" ]

# 8. lecture seule : échec signalé, fichier intact (sans objet sous root)
if [ "$(id -u)" -ne 0 ]; then
  f=$W/ro.json; printf '{\n  "theme": "dark"\n}\n' > "$f"; chmod 444 "$f"; cp "$f" "$W/orig"
  if claude_allow_put "$f" >/dev/null 2>&1; then ko "lecture seule: succès annoncé"; else ok "lecture seule: échec signalé"; fi
  check "lecture seule: intact" "$W/orig" "$f"
  chmod 644 "$f"
fi

# 9. règles de Codex : adb et thor-screen autorisés, rien d'autre
if command -v codex >/dev/null 2>&1; then
  rule() { # attendu commande…
    local want=$1 got; shift
    got=$(codex execpolicy check --rules "$HERE/thor-android.rules" "$@" 2>&1 | jq -r '.decision // "aucune"' 2>/dev/null)
    is "règle Codex: $* → $got (attendu $want)" [ "$got" = "$want" ]
  }
  rule allow adb shell input -d 4 tap 100 200
  rule allow adb shell "dumpsys activity activities | grep -E '^Display #|topResumedActivity'"
  rule allow thor-screen launch bas app.thorai
  rule aucune adbd
  rule aucune rm -rf /tmp/x
else
  echo "(codex absent : règles de Codex non vérifiées)"
fi

echo "échecs : $fails"
[ "$fails" -eq 0 ]
