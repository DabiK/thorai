package app.regie.ui

import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.model.approvalModeHelp

private const val OPTIONS_UNKNOWN = "Modèle et réflexion non communiqués. Reprends la session pour les actualiser."

/**
 * L'aide de la table des réglages en modification, une ligne au plus : l'avis du réglage en cours
 * d'abord, puis les choix préparés, puis l'effet du mode choisi.
 */
fun settingsHint(state: SessionSettingsReview): String? = when {
    state.notice.isNotBlank() -> state.notice
    state.replacements.isNotEmpty() -> "${state.replacements.size} choix préparés. Applique-les pour reprendre."
    state.session?.active == false -> "Choisis les remplacements, puis applique-les."
    state.row?.id == "approvalMode" -> approvalModeHelp(state.value.orEmpty()).ifEmpty { null }
    state.session?.configOptions?.isEmpty() == true -> OPTIONS_UNKNOWN
    else -> null
}
