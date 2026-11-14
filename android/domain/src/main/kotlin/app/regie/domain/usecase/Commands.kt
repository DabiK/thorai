package app.regie.domain.usecase

// Cas d'usage déclenchés par les commandes du PRD (section « Commandes »).
// Ils sont vides tant que le ticket qui les porte n'est pas livré : la manette
// et l'écran tactile sont déjà branchés dessus.

/** X sur la vue d'ensemble : nouvelle session. Ticket 11. */
class StartNewSession {
    operator fun invoke() = Unit
}

