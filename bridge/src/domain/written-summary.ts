/** Ask the running agent once per turn; never launch a second agent or summary-only turn. */
export function withSummaryRequest(text: string): string {
  return `${SUMMARY_INSTRUCTION}\n\n${text}`;
}

const SUMMARY_INSTRUCTION = `Régie affiche un point écrit. À la fin de ce tour, ajoute un bilan français très court, fondé uniquement sur ce que tu as fait et observé. Chaque champ tient en une phrase courte (180 caractères maximum). Pour les tests absents, écris « non communiqués » ; ne déduis jamais une réussite. Si tu attends une décision, indique la question, sinon « aucune ». Respecte ce format sans bloc de code :
[Point Régie]
Fait : ce qui a été fait
Tests : commandes et résultats observés, ou non communiqués
Suite : ce qui reste, ou rien si établi
Question : question à l'utilisateur, ou aucune
[/Point Régie]`;
