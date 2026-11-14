#!/usr/bin/env bash
# Lance Codex sur un ticket, dans son worktree. Repris d'agent-chat.
# Pas de quota Claude : Codex puise dans l'abonnement ChatGPT.
#
#   scripts/run-ticket-codex.sh <NN> ["note"]          implémentation (scripts/ticket-solo.md)
#   MODE=review scripts/run-ticket-codex.sh <NN>       relecture indépendante (scripts/review-codex.md)
#
# Rapport : .scratch/regie-v1/runs/<slug>-codex-last-report.md (ou -codex-review.md)
set -euo pipefail

ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
ISSUES="$ROOT/.scratch/regie-v1/issues"
RUNS="$ROOT/.scratch/regie-v1/runs"
MODE="${MODE:-dev}"

NN="${1:?usage: scripts/run-ticket-codex.sh <NN> [note]}"
NOTE="${2:-}"

ticket="$(find "$ISSUES" -maxdepth 1 -name "${NN}-*.md" | head -n 1)"
[ -n "$ticket" ] || { echo "ticket $NN introuvable" >&2; exit 1; }
slug="$(basename "$ticket" .md)"
branch="ticket/$slug"
wt="$ROOT/.worktrees/$slug"

if [ ! -d "$wt" ]; then
	if git -C "$ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
		git -C "$ROOT" worktree add "$wt" "$branch"
	else
		git -C "$ROOT" worktree add "$wt" -b "$branch" main
	fi
fi

# SDK Android : local.properties n'est pas versionné, un worktree neuf ne l'a pas.
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
for d in android voice; do
	if [ -d "$wt/$d" ] && [ ! -f "$wt/$d/local.properties" ]; then
		echo "sdk.dir=$ANDROID_HOME" >"$wt/$d/local.properties"
	fi
done

# Dépendances du pont : node_modules n'est pas versionné.
if [ -f "$wt/bridge/package-lock.json" ] && [ ! -d "$wt/bridge/node_modules/@rolldown" ]; then
	(cd "$wt/bridge" && npm ci --no-audit --no-fund >/dev/null 2>&1) || echo "npm ci a échoué dans $wt/bridge" >&2
fi

mkdir -p "$RUNS"
if [ "$MODE" = review ]; then
	instructions="$ROOT/scripts/review-codex.md"
	report="$RUNS/$slug-codex-review.md"
else
	instructions="$ROOT/scripts/ticket-solo.md"
	report="$RUNS/$slug-codex-last-report.md"
fi
log="$RUNS/$slug-codex-$MODE-$(date +%Y%m%d-%H%M%S).log"

prompt="$(cat "$instructions")

## Ticket

Fichier : .scratch/regie-v1/issues/$slug.md

$(cat "$wt/.scratch/regie-v1/issues/$slug.md")"

if [ -n "$NOTE" ]; then
	prompt="$prompt

## Note de l'orchestrateur

Ce ticket a peut-être déjà été travaillé sur cette branche : regarde git status et git log main..HEAD avant de reprendre.

$NOTE"
fi

rm -f "$report"
status=0
REGIE_TICKET_RUN=1 codex exec \
	--dangerously-bypass-approvals-and-sandbox \
	-C "$wt" \
	-c 'model_reasoning_effort="high"' \
	-o "$report" \
	"$prompt" >"$log" 2>&1 </dev/null || status=$?

if [ ! -s "$report" ]; then
	{
		echo "STATUT: failed"
		echo "RÉSUMÉ: Codex s'est arrêté sans rapport (code $status)."
		echo "Journal : $log"
		grep -iE "usage limit|rate limit|quota" "$log" | tail -n 2
	} >"$report"
fi
echo "--- codex $MODE: $slug (code $status) → $report"
