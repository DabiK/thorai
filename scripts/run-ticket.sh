#!/usr/bin/env bash
# Lance un sous-processus Claude sur un ticket, dans un worktree dédié.
# Repris de agent-chat (scripts/run-ticket.sh).
#
# Usage : scripts/run-ticket.sh <NN> ["note de l'orchestrateur"]
#
# Rapport final : .scratch/regie-v1/runs/<slug>-last-report.md
# Journal complet : .scratch/regie-v1/runs/<slug>-<date>.jsonl
set -euo pipefail

ROOT="$(git rev-parse --path-format=absolute --git-common-dir)"
ROOT="$(dirname "$ROOT")"
ISSUES="$ROOT/.scratch/regie-v1/issues"
RUNS="$ROOT/.scratch/regie-v1/runs"

NN="${1:?usage: scripts/run-ticket.sh <NN> [note]}"
NOTE="${2:-}"

ticket="$(find "$ISSUES" -maxdepth 1 -name "${NN}-*.md" | head -n 1)"
[ -n "$ticket" ] || {
	echo "ticket $NN introuvable dans $ISSUES" >&2
	exit 1
}
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

mkdir -p "$RUNS"
log="$RUNS/$slug-$(date +%Y%m%d-%H%M%S).jsonl"
report="$RUNS/$slug-last-report.md"

prompt="$(cat "$ROOT/scripts/ticket-lead.md")

## Ticket

Fichier : .scratch/regie-v1/issues/$slug.md

$(cat "$ticket")"

if [ -n "$NOTE" ]; then
	prompt="$prompt

## Note de l'orchestrateur

Si la branche a déjà des commits, regarde git log main..HEAD avant de reprendre.

$NOTE"
fi

cd "$wt"
status=0
REGIE_TICKET_RUN=1 claude -p "$prompt" \
	--model "${TICKET_MODEL:-claude-opus-5-5}" \
	--effort "${TICKET_EFFORT:-high}" \
	--dangerously-skip-permissions \
	--output-format stream-json --verbose \
	--max-budget-usd "${TICKET_BUDGET_USD:-60}" \
	>"$log" || status=$?

jq -r 'select(.type=="result") | .result // empty' "$log" >"$report"
if [ ! -s "$report" ]; then
	{
		echo "STATUT: failed"
		echo "RÉSUMÉ: le sous-processus s'est arrêté sans rapport (code $status)."
		echo "Journal : $log"
		jq -r 'select(.type=="result") | "subtype=\(.subtype) is_error=\(.is_error)"' "$log" 2>/dev/null | tail -n 1
	} >"$report"
fi

echo "--- run: $slug (code $status)"
jq -r 'select(.type=="result") | "durée=\((.duration_ms // 0) / 60000 | floor) min  coût=\(.total_cost_usd // "?") USD  tours=\(.num_turns // "?")"' "$log" | tail -n 1
echo "--- rapport: $report"
cat "$report"
