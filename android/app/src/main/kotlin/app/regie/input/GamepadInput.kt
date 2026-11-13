package app.regie.input

/** Boutons de la manette que l'app utilise (PRD, « Commandes »). */
enum class Button { A, B, X, Y, L1, R1, START, UP, DOWN, LEFT, RIGHT }

/** Gâchettes lues en maintien. */
enum class Trigger { L2, R2 }

/** Entrée de manette, indépendante de l'écran qui a reçu l'événement. */
sealed interface GamepadInput {
    data class Pressed(val button: Button) : GamepadInput
    data class TriggerDown(val trigger: Trigger) : GamepadInput
    data class TriggerUp(val trigger: Trigger) : GamepadInput

    /** Vitesse de défilement demandée au stick droit, de -1 (haut) à 1 (bas). 0 : arrêt. */
    data class Scroll(val velocity: Float) : GamepadInput
}

/** Valeurs d'axes utiles, de 0 à 1 pour les gâchettes et de -1 à 1 pour le stick. */
data class TriggerAxes(val leftTrigger: Float, val rightTrigger: Float, val rightStickY: Float)
