#!/usr/bin/env bash
# Un ticket avec Codex, en parallèle d'autres : implémentation puis relecture
# indépendante, 3 tours au plus. Ne fusionne pas : l'orchestrateur fusionne.
#
#   NOTE_FILE=chemin scripts/ticket-loop.sh <NN>
#
# Journal : .scratch/regie-v1/runs/loop-<NN>.log (dernière ligne : BOUCLE …)
set -uo pipefail

ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
cd "$ROOT" || exit 1
NN="${1:?usage: scripts/ticket-loop.sh <NN>}"
RUNS=.scratch/regie-v1/runs
LOG=$RUNS/loop-$NN.log
NOTE="$( [ -n "${NOTE_FILE:-}" ] && cat "$NOTE_FILE" || true)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
slug="$(basename "$(find .scratch/regie-v1/issues -maxdepth 1 -name "$NN-*.md" | head -n 1)" .md)"

say() { echo "$(date '+%d/%m %H:%M') $*" >>"$LOG"; }
quota_hit() { grep -qiE "usage limit|rate limit|hit your|quota" "$1"; }

say "BOUCLE démarrée : $NN"
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
	grep -q '^STATUT: done' "$report" || { say "BOUCLE arrêtée : $NN $(grep -m1 '^STATUT' "$report")"; exit 1; }
	say "$NN tour $round : relecture"
	MODE=review scripts/run-ticket-codex.sh "$NN" >>"$LOG" 2>&1
	review=$RUNS/$slug-codex-review.md
	if grep -q '^VERDICT: APPROUVÉ' "$review"; then say "BOUCLE finie : $NN APPROUVÉ, à fusionner"; exit 0; fi
	findings="$(cat "$review")"
	say "$NN tour $round : changements requis"
done
say "BOUCLE arrêtée : $NN non approuvé après 3 tours"
exit 1
