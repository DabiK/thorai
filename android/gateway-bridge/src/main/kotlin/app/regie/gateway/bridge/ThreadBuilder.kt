package app.regie.gateway.bridge

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.ContextUsage
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.LineChanges
import app.regie.domain.model.PlanStep
import app.regie.domain.model.PlanStepStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ToolKind
import app.regie.gateway.bridge.BridgeState.Companion.actionStatusOf
import app.regie.gateway.bridge.BridgeState.Companion.commandOf
import app.regie.gateway.bridge.BridgeState.Companion.oneLine
import app.regie.gateway.bridge.BridgeState.Companion.relativeTo
import kotlinx.serialization.json.JsonObject

/**
 * Le fil d'une session, reconstruit à partir de ses événements (docs/protocole-pont.md,
 * « Événements » et « Corrections faites par le pont ») :
 * - `user_message` : un message envoyé à l'agent ;
 * - `agent_message_chunk` : mis bout à bout, un bloc par suite de morceaux ; la réflexion
 *   (`agent_thought_chunk`) n'est pas montrée ;
 * - `tool_call`, `tool_call_update` : une action, mise à jour sur place ; un appel retiré par le pont
 *   (`_meta.regie.hidden`) disparaît ; la sortie d'une commande s'ajoute (`delta`) ou se remplace
 *   (`final`, ou sans indication) ;
 * - `plan`, `usage_update` : le plan et le contexte ;
 * - `session_error`, `turn_end` interrompu : une ligne chacun.
 *
 * Borné à [maxEntries] éléments, les plus anciens oubliés. Les éléments ne sont recopiés que quand
 * ils changent : un texte de Codex, qui arrive par morceaux minuscules, n'est pas reconstruit en entier
 * à chaque morceau lu, seulement à chaque publication.
 *
 * Pas thread-safe, comme [BridgeState].
 */
internal class ThreadBuilder(private val cwd: () -> String?, private val maxEntries: Int = MAX_ENTRIES) {
    private var nextId = 0L
    private var lastSequence = -1L
    private val items = ArrayDeque<Item>()
    private val calls = HashMap<String, Call>()
    private var plan: List<PlanStep>? = null
    private var context: ContextUsage? = null
    // ACP queues prompts FIFO; a queued user_message is emitted before the running turn ends.
    private var responseTo: Long? = null
    private val pendingMessages = ArrayDeque<Long>()
    private val completedMessages = mutableSetOf<Long>()

    fun clear() {
        items.clear()
        calls.clear()
        plan = null
        context = null
        responseTo = null
        pendingMessages.clear()
        completedMessages.clear()
        lastSequence = -1L
    }

    fun apply(event: BridgeEvent) {
        lastSequence = event.seq
        val data = event.data
        when (event.type) {
            "user_message" -> data.string("text")?.let { addUserMessage(it, data.string("delivery"), event.seq) }
            "session_error" -> {
                add(Fixed(ThreadEntry.TurnFailed(nextId++, data.string("message")?.let(::oneLine)?.ifEmpty { null }, responseTo)))
                finishResponse()
            }
            "turn_end" -> {
                val cancelled = data.string("stopReason") == "cancelled"
                // Fin du tour : aucun résultat définitif connu ; une mise à jour tardive reste possible.
                items.forEach { if (it is Call) it.endTurn(cancelled) }
                if (cancelled) addInterruption()
                finishResponse()
            }
            "session_update" -> applyUpdate(data)
        }
    }

    fun addUserMessage(text: String, delivery: String? = null, sequence: Long = lastSequence + 1) {
        val id = nextId++
        lastSequence = sequence
        add(Fixed(ThreadEntry.UserMessage(id, text, sequence)))
        if (delivery == "queued") pendingMessages.addLast(id)
        else {
            // Steering owns subsequent chunks of the current turn, not an extra queued turn.
            completeResponse()
            responseTo = id
        }
    }

    private fun completeResponse() {
        responseTo?.takeIf { id -> items.any { it.entry() is ThreadEntry.UserMessage && it.entry().id == id } }
            ?.let(completedMessages::add)
    }

    private fun finishResponse() {
        completeResponse()
        responseTo = pendingMessages.removeFirstOrNull()
    }

    fun addInterruption() = add(Fixed(ThreadEntry.Interrupted(nextId++, responseTo)))

    fun snapshot(loaded: Boolean) = SessionThread(
        entries = items.map { it.entry() },
        plan = plan,
        context = context,
        loaded = loaded,
        completedMessages = completedMessages.toSet(),
        lastSequence = lastSequence,
    )

    private fun applyUpdate(update: JsonObject) {
        when (update.string("sessionUpdate")) {
            "agent_message_chunk" -> {
                val text = update.obj("content")?.takeIf { it.string("type") == "text" }?.string("text") ?: return
                if (text.isEmpty()) return
                val last = (items.lastOrNull() as? Text)?.takeIf { it.responseTo == responseTo }
                    ?: Text(nextId++, responseTo).also(::add)
                last.append(text)
            }
            "tool_call" -> update.string("toolCallId")?.let { id ->
                calls[id]?.merge(update) ?: add(Call(nextId++, id).also { calls[id] = it; it.merge(update) })
            }
            "tool_call_update" -> update.string("toolCallId")?.let { id ->
                if (update.obj("_meta")?.obj("regie")?.string("hidden") != null) {
                    // Échec du sandbox de Codex, déjà relayé : l'app retire cet appel (ADR 02).
                    calls.remove(id)?.let(items::remove)
                } else {
                    calls[id]?.merge(update) ?: add(Call(nextId++, id).also { calls[id] = it; it.merge(update) })
                }
            }
            "plan" -> plan = update.array("entries")?.objects()?.mapNotNull(::planStepOf)?.ifEmpty { null }
            "usage_update" -> {
                val used = update.long("used")
                val size = update.long("size")
                if (used != null && size != null && size > 0 && used >= 0) context = ContextUsage(used, size)
            }
        }
    }

    private fun add(item: Item) {
        items.addLast(item)
        while (items.size > maxEntries) {
            val dropped = items.removeFirst()
            if (dropped is Call) calls.remove(dropped.toolCallId)
            completedMessages.remove(dropped.entry().id)
        }
    }

    private sealed interface Item {
        fun entry(): ThreadEntry
    }

    private class Fixed(private val entry: ThreadEntry) : Item {
        override fun entry() = entry
    }

    private class Text(private val id: Long, val responseTo: Long?) : Item {
        private val text = StringBuilder()
        private var cached: ThreadEntry.AgentText? = null

        fun append(chunk: String) {
            text.append(chunk)
            cached = null
        }

        override fun entry() = cached ?: ThreadEntry.AgentText(id, text.toString(), responseTo).also { cached = it }
    }

    private inner class Call(private val id: Long, val toolCallId: String) : Item {
        private var kind: String? = null
        private var title: String? = null
        private var command: String? = null
        private var path: String? = null
        private val paths = LinkedHashSet<String>()
        private var status: ActionStatus? = null

        /** Lignes ajoutées et retirées, par fichier : le dernier diff d'un fichier remplace le précédent. */
        private val diffs = LinkedHashMap<String, LineChanges>()
        private val fragments = LinkedHashMap<String, List<app.regie.domain.model.DiffFragment>>()

        /** La fin de la sortie d'une commande : il n'en faut que la dernière ligne. */
        private var output: StringBuilder? = null
        private var cached: ThreadEntry.ToolCall? = null

        /** Fin du tour : interrompue si le tour l'a été, sinon sans statut plutôt que « en cours » à jamais. */
        fun endTurn(cancelled: Boolean) {
            if (status != ActionStatus.PENDING && status != ActionStatus.IN_PROGRESS) return
            status = if (cancelled) ActionStatus.INTERRUPTED else null
            cached = null
        }

        fun merge(update: JsonObject) {
            cached = null
            update.string("kind")?.let { kind = it }
            update.string("title")?.let(::oneLine)?.ifEmpty { null }?.let { title = it }
            update.obj("rawInput")?.let(::commandOf)?.let { command = it }
            actionStatusOf(update.string("status"))?.let { status = it }
            update.array("locations")?.objects()?.mapNotNull { it.string("path") }?.forEach {
                val file = relativeTo(it, cwd())
                paths += file
                if (path == null) path = file
            }
            val content = update.array("content")?.objects() ?: return
            for (item in content.filter { it.string("type") == "diff" }) {
                val file = item.string("path")?.let { relativeTo(it, cwd()) } ?: continue
                val newText = item.string("newText") ?: continue
                diffs[file] = LineDiff.changes(item.string("oldText"), newText)
                fragments[file] = DiffDecoder.read(file, item, update)
                if (path == null) path = file
            }
            if (kind == "execute") readOutput(update, content)
        }

        private fun readOutput(update: JsonObject, content: List<JsonObject>) {
            val text = content.filter { it.string("type") == "content" }
                .mapNotNull { it.obj("content")?.takeIf { c -> c.string("type") == "text" }?.string("text") }
                .joinToString("")
            val delta = update.obj("_meta")?.obj("regie")?.string("output") == "delta"
            val buffer = output?.takeIf { delta } ?: StringBuilder().also { output = it }
            buffer.append(text)
            if (buffer.length > OUTPUT_TAIL) buffer.delete(0, buffer.length - OUTPUT_TAIL)
        }

        override fun entry(): ThreadEntry.ToolCall = cached ?: ThreadEntry.ToolCall(
            id = id,
            kind = kindOf(kind),
            title = title,
            command = command,
            path = path,
            changes = diffs.values.takeIf { it.isNotEmpty() }?.reduce(LineChanges::plus),
            status = status,
            result = output?.let(::lastLine),
            files = (paths + diffs.keys).map { ModifiedFile(it, diffs[it]) },
            diffs = fragments.values.flatten(),
            persistentId = toolCallId,
        ).also { cached = it }
    }

    companion object {
        const val MAX_ENTRIES = 300
        private const val OUTPUT_TAIL = 4096

        private fun kindOf(kind: String?): ToolKind = when (kind) {
            "read" -> ToolKind.READ
            "edit", "delete", "move" -> ToolKind.EDIT
            "execute" -> ToolKind.EXECUTE
            else -> ToolKind.OTHER
        }

        private fun planStepOf(entry: JsonObject): PlanStep? {
            val text = entry.string("content")?.let(::oneLine)?.ifEmpty { null } ?: return null
            val status = when (entry.string("status")) {
                "completed" -> PlanStepStatus.DONE
                "in_progress" -> PlanStepStatus.IN_PROGRESS
                else -> PlanStepStatus.PENDING
            }
            return PlanStep(text, status)
        }

        /** La dernière ligne non vide d'une sortie, sans les clôtures de bloc de Claude (« ```console »). */
        fun lastLine(output: CharSequence): String? =
            output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("```") }.lastOrNull()?.let(::oneLine)
    }
}
