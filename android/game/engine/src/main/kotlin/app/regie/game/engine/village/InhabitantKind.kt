package app.regie.game.engine.village

import app.regie.domain.model.Agent

/**
 * L'habitant d'une maison : l'agent de la session (da-jeu.md, « Le hameau s'agrandit »).
 * Le scribe pour Claude, le bricoleur pour Codex, la cartographe pour opencode. Deux sessions du même
 * agent donnent deux maisons, chacune avec son habitant.
 */
enum class InhabitantKind {
    SCRIBE,
    BRICOLEUR,
    CARTOGRAPHE,
    ;

    companion object {
        /** Tout autre agent annoncé par le pont prend l'habitant par défaut. */
        val FALLBACK = SCRIBE

        /** D'après l'identifiant de l'agent annoncé par le pont, jamais d'après son libellé. */
        fun of(agent: Agent): InhabitantKind =
            when (agent.id) {
                "claude" -> SCRIBE
                "codex" -> BRICOLEUR
                "opencode" -> CARTOGRAPHE
                else -> FALLBACK
            }
    }
}
