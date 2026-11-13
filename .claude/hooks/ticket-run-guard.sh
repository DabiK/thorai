#!/usr/bin/env bash
# Garde-fou PreToolUse sur Bash. Un code de sortie 2 refuse la commande et
# renvoie le message à l'agent.
#
# - Toujours : refuse --no-verify.
# - Pendant un run de ticket (REGIE_TICKET_RUN=1, posé par
#   scripts/run-ticket.sh) : refuse ce qui sort de la branche du ticket. Pousser,
#   fusionner et nettoyer les worktrees reviennent à l'orchestrateur.

input="$(cat)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)"
[ -z "$cmd" ] && exit 0

deny() {
	echo "$1" >&2
	exit 2
}

case "$cmd" in
*--no-verify*)
	deny "Refusé : --no-verify est interdit. Corrige la cause de l'échec du hook."
	;;
esac

if [ "${REGIE_TICKET_RUN:-}" = "1" ]; then
	sep='(^|[;&|[:space:]])'
	git_ops='git[[:space:]]+(push|merge|rebase|worktree|(checkout|switch)[[:space:]]+main)'
	if printf '%s' "$cmd" | grep -Eq "${sep}(${git_ops}|gh[[:space:]])"; then
		deny "Refusé pendant un run de ticket : reste sur la branche du ticket. Pas de push, merge, rebase, worktree, retour sur main ni commande gh — l'orchestrateur s'en charge."
	fi
fi

exit 0
