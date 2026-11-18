package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Keeps the reviewed target fixed; A confirms live settings or all prepared resume replacements. */
class ReviewSessionSettings(private val gateway: AgentGateway, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(SessionSettingsReview())
    val state = mutable.asStateFlow()
    private var observing: Job? = null
    /** [row] : the row touched in the bottom table, selected as soon as the session is known. */
    fun open(id: SessionId, row: String? = null) {
        if (state.value.busy) return
        observing?.cancel()
        mutable.value = SessionSettingsReview(visible = true)
        var wanted = row
        observing = scope.launch {
            gateway.sessions.collect { sessions ->
                val old = state.value
                val session = sessions.firstOrNull { it.id == id }
                val next = old.copy(session = session)
                val index = next.rows.indexOfFirst { it.id == (wanted ?: old.row?.id) }.takeIf { it >= 0 } ?: 0
                if (session != null) wanted = null
                val selected = next.copy(selected = index)
                val preserve = old.row?.id == selected.row?.id && old.value != old.row?.current && selected.row?.choices?.any { it.value == old.value } == true
                mutable.value = selected.copy(value = selected.replacements[selected.row?.id] ?: if (preserve) old.value else selected.row?.current)
            }
        }
    }
    fun select(index: Int) {
        val s = state.value
        if (s.busy || index !in s.rows.indices) return
        mutable.value = s.copy(selected = index, value = s.replacements[s.rows[index].id] ?: s.rows[index].current, notice = "")
    }
    fun move(delta: Int) = select((state.value.selected + delta).coerceIn(0, state.value.rows.lastIndex.coerceAtLeast(0)))
    fun change(delta: Int) {
        val s = state.value; val choices = s.row?.choices.orEmpty()
        if (s.busy || s.uncertain || choices.isEmpty()) return
        val index = choices.indexOfFirst { it.value == s.value }
        val value = choices[Math.floorMod(index + delta, choices.size)].value
        val replacements = if (s.session?.active == false && s.row?.id != "approvalMode") {
            if (value == s.row!!.current) s.replacements - s.row!!.id else s.replacements + (s.row!!.id to value)
        } else s.replacements
        // The keys already show A Appliquer and B Fermer: no reminder (docs/textes.md).
        mutable.value = s.copy(value = value, replacements = replacements, notice = "")
    }
    fun close() { if (!state.value.busy) { observing?.cancel(); mutable.value = SessionSettingsReview() } }
    fun apply() {
        val s = state.value; val session = s.session ?: return; val row = s.row ?: return
        if (!s.canApply) return
        mutable.value = s.copy(busy = true, notice = "Application…")
        scope.launch {
            val result = try {
                if (s.applyingReplacements) gateway.configureSession(session.id, SessionSetting(configValues = s.replacements))
                else if (row.id == "resume") gateway.resumeSession(session.id)
                else gateway.configureSession(session.id, if (row.id == "approvalMode") SessionSetting(approvalMode = s.value) else SessionSetting(configId = row.id, value = s.value))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { SettingsResult.UNKNOWN }
            mutable.value = state.value.copy(busy = false, uncertain = result == SettingsResult.UNKNOWN,
                replacements = if (result == SettingsResult.APPLIED) emptyMap() else state.value.replacements,
                notice = when (result) {
                    SettingsResult.APPLIED -> if (row.id == "resume") "Session reprise · prête à recevoir un message." else "Réglage confirmé par l’agent · prochains messages."
                    SettingsResult.REFUSED -> "Réglage refusé ou indisponible."
                    SettingsResult.UNKNOWN -> "Résultat incertain. Ferme puis vérifie le mode et les options. Aucun renvoi automatique."
                })
        }
    }
}
