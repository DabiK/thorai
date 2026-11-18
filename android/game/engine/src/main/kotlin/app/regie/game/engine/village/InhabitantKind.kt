package app.regie.game.engine.village

import app.regie.domain.model.Agent
import java.util.Locale

/**
 * L'habitant d'une maison : l'agent de la session (da-jeu.md, « Le hameau s'agrandit »).
 * Le scribe pour Claude, le bricoleur pour Codex, la cartographe pour opencode.
 */
enum class InhabitantKind {
    SCRIBE,
    BRICOLEUR,
    CARTOGRAPHE,
    ;

    companion object {
        /** Un agent inconnu (un quatrième connecteur) prend l'habitant par défaut plutôt que d'échouer. */
        val FALLBACK = SCRIBE

        fun of(agent: Agent): InhabitantKind = ofAgentName(agent.name)

        /** D'après le nom ou l'identifiant de l'agent, sans tenir compte de la casse. */
        fun ofAgentName(name: String): InhabitantKind =
            when (name.trim().lowercase(Locale.ROOT)) {
                "claude" -> SCRIBE
                "codex" -> BRICOLEUR
                "opencode" -> CARTOGRAPHE
                else -> FALLBACK
            }
    }
}
