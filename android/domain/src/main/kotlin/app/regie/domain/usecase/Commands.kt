package app.regie.domain.usecase

import app.regie.domain.model.ApprovalId
import app.regie.domain.model.SessionId

// Cas d'usage déclenchés par les commandes du PRD (section « Commandes »).
// Ils sont vides tant que le ticket qui les porte n'est pas livré : la manette
// et l'écran tactile sont déjà branchés dessus.

/** L2 maintenu : dicter un message à la session affichée. Ticket 08. */
class StartDictation {
    operator fun invoke(target: SessionId?) = Unit
}

/** L2 relâché. Ticket 08. */
class StopDictation {
    operator fun invoke() = Unit
}

/** R2 maintenu : donner un ordre vocal. Ticket 09. */
class StartVoiceCommand {
    operator fun invoke() = Unit
}

/** R2 relâché. Ticket 09. */
class StopVoiceCommand {
    operator fun invoke() = Unit
}

/** A sur une carte : ouvrir la session. Ticket 06. */
class OpenSession {
    operator fun invoke(id: SessionId) = Unit
}

/** R1 sur la vue d'ensemble : ouvrir la première validation en attente. Ticket 07. */
class OpenApproval {
    operator fun invoke(id: ApprovalId) = Unit
}

/** X sur la vue d'ensemble : nouvelle session. Ticket 11. */
class StartNewSession {
    operator fun invoke() = Unit
}

/** Y : faire le point à voix haute. Ticket 12. */
class SpeakSummary {
    operator fun invoke(target: SessionId?) = Unit
}

/** Start sur la vue d'ensemble : tout mettre en pause. Ticket 05 ou 09. */
class PauseAll {
    operator fun invoke() = Unit
}
