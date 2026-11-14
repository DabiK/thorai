package app.regie.domain.model

/** Identifiers and choices belong to the agent; no catalogue lives in Android. */
data class OptionValue(val value: String, val name: String)
data class SessionOption(val id: String, val name: String, val category: String?, val currentValue: String?, val options: List<OptionValue>) {
    val label: String get() = when (category) { "model" -> "Modèle"; "thought_level" -> "Réflexion"; else -> name }
    val valueLabel: String get() = options.firstOrNull { it.value == currentValue }?.name ?: currentValue ?: "Non communiqué"
}
val approvalModes = listOf("plan", "auto-edit", "ask", "allow-all")
fun approvalModeLabel(mode: String): String = when (mode) {
    "plan" -> "Plan d’abord"
    "ask" -> "Je valide chaque action"
    "auto-edit" -> "Modifs auto"
    "allow-all" -> "Tout autoriser"
    else -> "Mode non communiqué"
}
fun approvalModeHelp(mode: String): String = when (mode) {
    "allow-all" -> "Aucune validation pendant la session."
    "ask" -> "Les lectures usuelles restent automatiques."
    "auto-edit" -> "Éditions automatiques ; les commandes qui écrivent demandent validation."
    "plan" -> "L’agent prépare un plan avant les modifications."
    else -> ""
}
fun editableSessionOptions(options: List<SessionOption>) = options.filter { it.id !in listOf("mode", "collaboration_mode") && it.category !in listOf("mode", "collaboration_mode") }
enum class SettingsResult { APPLIED, REFUSED, UNKNOWN }
data class SessionSetting(val approvalMode: String? = null, val configId: String? = null, val value: String? = null, val configValues: Map<String, String> = emptyMap())
data class SettingsRow(val id: String, val label: String, val current: String?, val choices: List<OptionValue>)
data class SessionSettingsReview(
    val visible: Boolean = false, val session: Session? = null, val selected: Int = 0,
    val value: String? = null, val busy: Boolean = false, val uncertain: Boolean = false, val notice: String = "",
    val replacements: Map<String, String> = emptyMap(),
) {
    val rows: List<SettingsRow> get() = session?.let { session ->
        listOf(SettingsRow("approvalMode", "Validation", session.approvalMode, approvalModes.map { OptionValue(it, approvalModeLabel(it)) })) +
            editableSessionOptions(session.configOptions).map { SettingsRow(it.id, it.label, it.currentValue, it.options) } +
            if (!session.active) listOf(SettingsRow("resume", "Reprendre cette session", null, emptyList())) else emptyList()
    }.orEmpty()
    val canInterrupt: Boolean get() = session?.status?.isBusy == true
    val row: SettingsRow? get() = rows.getOrNull(selected)
    val applyingReplacements: Boolean get() = session?.active == false && replacements.isNotEmpty() && row?.id != "approvalMode"
    val canApply: Boolean get() = !busy && !uncertain && row != null && if (applyingReplacements) {
        // Saved choices may only become available after the prepared model is applied.
        // The bridge restores and validates them against each fresh ACP response.
        replacements.all { (id, choice) ->
            editableSessionOptions(session!!.configOptions).any { option ->
                option.id == id && option.options.any { it.value == choice }
            }
        }
    } else (row!!.id == "resume" || value != row!!.current && row!!.choices.any { it.value == value })
}
