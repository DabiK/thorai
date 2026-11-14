#!/usr/bin/env bash
# Chaîne de nuit : enchaîne les tickets avec Codex (aucun quota Claude), un par un.
# Pour chaque ticket : implémentation, relecture indépendante (3 tours au plus),
# fusion dans main, porte de qualité sur main, push, réinstallation sur la Thor.
# S'arrête au premier ticket bloqué, refusé, en conflit ou rouge sur main.
#
#   nohup scripts/night-chain.sh > /dev/null 2>&1 &
#   ORDER="07 08" NOTE_FILE=chemin scripts/night-chain.sh
#
# Journal : .scratch/regie-v1/runs/night-chain.log (dernière ligne : CHAÎNE …)
set -uo pipefail

ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
cd "$ROOT"
ISSUES=.scratch/regie-v1/issues
RUNS=.scratch/regie-v1/runs
LOG=$RUNS/night-chain.log
ORDER="${ORDER:-06 07 08 12 09 10 11}"
NOTE="$( [ -n "${NOTE_FILE:-}" ] && cat "$NOTE_FILE" || true)"
TRAILER="Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
mkdir -p "$RUNS"

say() { echo "$(date '+%d/%m %H:%M') $*" >>"$LOG"; }
stop() { say "CHAÎNE arrêtée : $*"; exit 1; }
quota_hit() { grep -qiE "usage limit|rate limit|hit your|quota" "$1"; }

say "CHAÎNE démarrée : $ORDER"
for NN in $ORDER; do
	ticket="$(ls $ISSUES/$NN-*.md 2>/dev/null | head -n 1)"
	[ -n "$ticket" ] || stop "ticket $NN introuvable"
	slug="$(basename "$ticket" .md)"
	if grep -q '^Statut : fait' "$ticket"; then say "$NN déjà fait"; continue; fi

	approved=0
	findings=""
	for round in 1 2 3; do
		note="$NOTE"
		[ -n "$findings" ] && note="$note

Relecture indépendante du tour précédent : corrige les constats bloquants ci-dessous, puis recommite.
$findings"
		tries=0
		while :; do
			say "$NN tour $round : implémentation"
			scripts/run-ticket-codex.sh "$NN" "$note" >>"$LOG" 2>&1
			report=$RUNS/$slug-codex-last-report.md
			if grep -q '^STATUT: failed' "$report" && quota_hit "$report" && [ $tries -lt 8 ]; then
				tries=$((tries + 1)); say "$NN quota Codex atteint, nouvel essai dans 30 min"; sleep 1800; continue
			fi
			break
		done
		statut="$(grep -m1 '^STATUT' "$report" | awk '{print $2}')"
		case "$statut" in
			done) ;;
			blocked) stop "$NN bloqué (voir $report)" ;;
			*) stop "$NN en échec (voir $report)" ;;
		esac
		say "$NN tour $round : relecture"
		MODE=review scripts/run-ticket-codex.sh "$NN" >>"$LOG" 2>&1
		review=$RUNS/$slug-codex-review.md
		if grep -q '^VERDICT: APPROUVÉ' "$review"; then approved=1; break; fi
		findings="$(cat "$review")"
		say "$NN tour $round : changements requis"
	done
	[ $approved = 1 ] || stop "$NN non approuvé après 3 tours (voir $RUNS/$slug-codex-review.md)"

	[ -z "$(git status --porcelain --untracked-files=no)" ] || stop "main n'est pas propre avant la fusion de $NN"
	git merge --no-ff --no-edit "ticket/$slug" >>"$LOG" 2>&1 || { git merge --abort; stop "$NN en conflit avec main"; }
	changed="$(git diff --name-only ORIG_HEAD HEAD)"
	gate=0
	if echo "$changed" | grep -qE '^(android|voice)/'; then
		(cd android && ./gradlew check -q) >>"$LOG" 2>&1 || gate=1
	fi
	if echo "$changed" | grep -q '^bridge/'; then
		(cd bridge && npm ci --no-audit --no-fund && npm run check) >>"$LOG" 2>&1 || gate=1
	fi
	[ $gate = 0 ] || { git reset -q --hard ORIG_HEAD; stop "$NN rouge sur main après fusion, fusion annulée"; }

	sed -i '' -E '1,5s/^Statut : .*/Statut : fait/' "$ticket"
	git add "$ticket" && git commit -q -m "chore(tickets): fusion du ticket $NN (chaîne de nuit)

$TRAILER"
	git push -q >>"$LOG" 2>&1 || say "$NN : push refusé, à refaire"
	git worktree remove --force ".worktrees/$slug" && git branch -D "ticket/$slug" >/dev/null
	if echo "$changed" | grep -q '^bridge/'; then thor/bridge/install.sh >>"$LOG" 2>&1 || say "$NN : réinstallation du pont en échec"; fi
	scripts/thor-install.sh >>"$LOG" 2>&1 || say "$NN : installation de l'app en échec"
	say "$NN FUSIONNÉ"
done
say "CHAÎNE finie"
