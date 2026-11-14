package app.regie.domain.usecase

// Cas d'usage déclenchés par les commandes du PRD (section « Commandes »).
// Ils sont vides tant que le ticket qui les porte n'est pas livré : la manette
// et l'écran tactile sont déjà branchés dessus.

/** R2 maintenu : donner un ordre vocal. Ticket 09. */
class StartVoiceCommand {
    operator fun invoke() = Unit
}

/** R2 relâché. Ticket 09. */
class StopVoiceCommand {
    operator fun invoke() = Unit
}

/** X sur la vue d'ensemble : nouvelle session. Ticket 11. */
class StartNewSession {
    operator fun invoke() = Unit
}

/** Start sur la vue d'ensemble : tout mettre en pause. Ticket 05 ou 09. */
class PauseAll {
    operator fun invoke() = Unit
}
