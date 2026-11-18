package app.regie.domain.usecase

import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.domain.port.SetupMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Le parcours d'accueil (ticket 28). Il s'ouvre seul au premier lancement, sur la bienvenue, et se
 * rouvre depuis l'accueil (« Configurer ») sur la première étape qui n'est pas faite. Les étapes se
 * vérifient sur [update] ; A, B, X et Y jouent les actions de [SetupReview.keys]. La navigation
 * reste ici, le reste ([perform]) est un geste d'Android : permission, réglages, téléchargement.
 */
class ReviewSetup(
    private val memory: SetupMemory,
    private val perform: (SetupAction) -> Unit,
) {
    private val mutable = MutableStateFlow(SetupReview())
    val state: StateFlow<SetupReview> = mutable.asStateFlow()

    /** Ni mené au bout ni refermé : il s'ouvrira au lancement. */
    val pending: Boolean get() = !memory.finished

    /** Au lancement : la bienvenue, si le parcours n'a jamais été mené au bout ni refermé. */
    fun offerOnLaunch(): Boolean {
        if (memory.finished || state.value.visible) return false
        mutable.update { it.copy(visible = true, step = SetupStep.WELCOME) }
        return true
    }

    /** « Configurer » : la première étape qui n'est pas faite, la fin si tout l'est. [at] : une étape précise. */
    fun open(at: SetupStep? = null) {
        if (state.value.visible && at == null) return
        mutable.update { review ->
            val first = at ?: SetupStep.entries.firstOrNull { it != SetupStep.WELCOME && !review.checks.isDone(it) } ?: SetupStep.FINISH
            review.copy(visible = true, step = first, copied = false)
        }
    }

    fun update(checks: SetupChecks) = mutable.update { it.copy(checks = checks) }

    /** Le bouton de l'écran : [SetupAction] décidée par l'étape, ou rien si le bouton est muet. */
    fun press(action: SetupAction?) {
        val review = state.value
        if (!review.visible || action == null) return
        // Un toucher sur un libellé périmé ne joue pas l'action d'une autre étape. Refermer reste
        // toujours possible, au doigt (« Reprendre plus tard »).
        if (action != SetupAction.CLOSE && action !in listOfNotNull(review.keys.a, review.keys.b, review.keys.x, review.keys.y)) return
        when (action) {
            SetupAction.NEXT, SetupAction.SKIP -> move(1)
            SetupAction.BACK -> move(-1)
            SetupAction.CLOSE -> close()
            SetupAction.CREATE_SESSION -> { close(); perform(action) }
            SetupAction.COPY_INSTALL_COMMANDS, SetupAction.COPY_PAIRING_COMMANDS -> { perform(action); mutable.update { it.copy(copied = true) } }
            else -> perform(action)
        }
    }

    /** Referme le parcours : il ne se rouvrira plus seul, « Configurer » le reprend. */
    fun close() {
        memory.finish()
        mutable.update { it.copy(visible = false) }
    }

    private fun move(delta: Int) = mutable.update { review ->
        val index = (review.step.ordinal + delta).coerceIn(0, SetupStep.entries.lastIndex)
        review.copy(step = SetupStep.entries[index], copied = false)
    }
}
