package app.regie.ui

import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Les rappels que les écrans du jeu reçoivent de l'app (tickets 45 à 49) : ce qu'un toucher demande,
 * traduit dans les mêmes entrées que la manette. Ils vivaient avec les écrans classiques, retirés au
 * ticket 51.
 */

/** Ce que la vue d'ensemble peut demander, au doigt comme à la manette. */
interface ControlCallbacks {
    fun onButton(button: Button)
    fun onTrigger(trigger: Trigger, held: Boolean)

    /** « Quitter », au toucher : le PRD ne lui donne pas de bouton de manette. */
    fun onQuit()
    fun onConfirmQuit()
    fun onCancelQuit()
}

/** Ce que l'écran d'une session peut demander, au doigt comme à la manette. */
interface SessionCallbacks {
    fun onButton(button: Button)
    fun onOptions() = onButton(Button.RIGHT)

    /** Toucher une ligne de la table des réglages : la table passe en modification sur cette ligne. */
    fun onSettingRow(row: String) = onOptions()
    fun onTrigger(trigger: Trigger, held: Boolean)
    fun onDraftChange(text: String)

    /** Envoi tactile explicite, même quand un autre panneau utilise A. */
    fun onSend()

    /** L'avis est resté affiché assez longtemps. */
    fun onNoticeSeen(notice: SessionNotice)
}

/** Défilement demandé à la manette : pas de la croix, vitesse du stick droit. */
class ThreadScroll(val steps: Flow<Int>, val velocity: StateFlow<Float>)
