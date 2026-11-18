package app.regie.game.engine.courier

import app.regie.domain.model.SessionId

/**
 * Ce que le joueur demande au coursier, sans rien d'Android : l'app traduit les entrées de la
 * manette (croix, stick, A, L1, R1, la liste du bas) en commandes (`da-jeu.md`, « Manette »).
 */
sealed interface CourierCommand {
    /** Une direction enfoncée ([pressed]) ou relâchée. Tant qu'elle est tenue, le coursier marche. */
    data class Move(val direction: Direction, val pressed: Boolean) : CourierCommand

    /** A : entrer dans la maison dont le coursier regarde la porte. */
    data object Interact : CourierCommand

    /**
     * Marcher seul jusqu'à la porte de la maison de [sessionId], par le plus court chemin, puis la
     * regarder. [enter] : y entrer à l'arrivée ([CourierEvent.EnterHouse]) ; sinon s'arrêter devant
     * ([CourierEvent.ArrivedAtDoor]). Toute entrée manuelle interrompt la marche.
     */
    data class GoTo(val sessionId: SessionId, val enter: Boolean = true) : CourierCommand

    /**
     * Marcher seul jusqu'au chantier ([app.regie.game.engine.village.Village.site]), devant son terrain,
     * puis le regarder ([CourierEvent.ArrivedAtSite]). X, l'ordre R2 « lance … » ou « Nouvelle session ».
     */
    data object GoToSite : CourierCommand

    /** R1 : la validation en attente d'abord, sinon la maison suivante. */
    data object Next : CourierCommand

    /** L1 : la maison précédente. */
    data object Previous : CourierCommand

    /** L2 maintenu : le coursier parle ([active]) ou se tait. Il peut marcher en parlant. */
    data class Talk(val active: Boolean) : CourierCommand
}

/** Ce que le coursier signale à l'interface. */
sealed interface CourierEvent {
    /** Le coursier entre dans la maison de [sessionId] : A devant la porte, ou fin d'une marche avec entrée. */
    data class EnterHouse(val sessionId: SessionId) : CourierEvent

    /** Le coursier s'est arrêté devant la porte de [sessionId], sans entrer (L1, R1). */
    data class ArrivedAtDoor(val sessionId: SessionId) : CourierEvent

    /** Le coursier est devant le chantier et le regarde. */
    data object ArrivedAtSite : CourierEvent
}

/** L'animation du coursier : debout, en marche ou en train de parler, toujours selon sa direction. */
enum class CourierAction { IDLE, WALK, TALK }
