package app.regie.domain.usecase

import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.SessionId
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Les messages que l'app a envoyés dictés, par session. Le pont ne dit pas d'où vient un message :
 * l'app s'en souvient, le temps du processus, pour afficher « Toi, dicté ».
 */
class DictatedMessages {
    private val _texts = MutableStateFlow<Map<SessionId, Set<String>>>(emptyMap())
    val texts: StateFlow<Map<SessionId, Set<String>>> = _texts.asStateFlow()

    fun remember(id: SessionId, text: String) = _texts.update { it + (id to (it[id].orEmpty() + text)) }

    fun forget(id: SessionId, text: String) = _texts.update { it + (id to (it[id].orEmpty() - text)) }
}

/**
 * A sur une carte, L1 / R1 sur une session : ouvrir une session en direct. Le fil est chargé à
 * l'ouverture s'il ne l'est pas. `null` tant que la session est inconnue, ou si elle disparaît.
 */
class OpenSession(private val gateway: AgentGateway, private val dictated: DictatedMessages) {
    operator fun invoke(id: SessionId): Flow<LiveSession?> =
        combine(
            gateway.sessions,
            gateway.thread(id),
            dictated.texts,
        ) { sessions, thread, texts ->
            sessions.firstOrNull { it.id == id }?.let { LiveSession.of(it, thread, texts[id].orEmpty()) }
        }.distinctUntilChanged()
}

/**
 * Envoyer un message à une session. Rien ne part sans un appui sur A (PRD) : c'est l'écran qui
 * l'appelle. Un texte vide ne part pas. [dictated] : le texte vient d'une dictée (ticket 08).
 */
class SendMessage(private val gateway: AgentGateway, private val dictated: DictatedMessages) {
    suspend operator fun invoke(id: SessionId, text: String, dictated: Boolean = false): Delivery? {
        val message = text.trim()
        if (message.isEmpty()) return null
        // Retenu avant l'envoi : le pont peut renvoyer le message dans le fil avant de répondre.
        if (dictated) this.dictated.remember(id, message)
        val delivery = gateway.sendMessage(id, message)
        if (dictated && delivery.canRetry) this.dictated.forget(id, message)
        return delivery
    }
}

/**
 * Start sur une session : interrompre son tour, sans confirmation. La session reste utilisable.
 * Seulement si elle travaille ou attend une validation ; `null` sinon (rien n'est demandé au pont).
 */
class InterruptSession(private val gateway: AgentGateway) {
    suspend operator fun invoke(live: LiveSession): Boolean? =
        if (live.canInterrupt) gateway.interrupt(live.session.id) else null
}
