package app.regie.gateway.bridge

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalChange
import app.regie.domain.model.ApprovalOption
import app.regie.domain.model.ApprovalOptionKind
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.LastAction
import app.regie.domain.model.Overview
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** La réponse à `hello` (docs/protocole-pont.md, « Numéros de séquence et rattrapage »). */
internal data class Hello(
    val journal: String?,
    val lastSeq: Long?,
    val resync: Boolean,
    val sessions: List<JsonObject>,
    val approvals: List<JsonObject>,
) {
    companion object {
        fun parse(result: JsonObject): Hello? {
            if ("journal" !in result && "lastSeq" !in result) return null
            return Hello(
                journal = result.string("journal"),
                lastSeq = result.long("lastSeq"),
                resync = result.boolean("resync") ?: false,
                sessions = result.array("sessions")?.objects().orEmpty(),
                approvals = result.array("approvals")?.objects().orEmpty(),
            )
        }
    }
}

/**
 * Ce que l'app sait du pont, reconstruit à partir de ses trames : sessions, validations en attente,
 * versions des agents, et le dernier numéro de séquence vu. Partagé par la vraie passerelle et par
 * la passerelle factice, qui rejoue des enregistrements : c'est le même décodage.
 *
 * Pas thread-safe : un seul appelant à la fois.
 */
internal class BridgeState {
    var journal: String? = null
        private set

    /** Dernier `seq` appliqué ; `null` tant qu'aucun `hello` n'a abouti ni aucun événement n'est arrivé. */
    var lastSeq: Long? = null
        private set

    var agents: List<AgentVersion> = emptyList()
        private set

    private val records = LinkedHashMap<String, SessionRecord>()
    private val approvals = LinkedHashMap<String, Approval>()

    /**
     * Applique la réponse à `hello`. [sentSince] est le `since` envoyé. Sans `since`, ou si le pont
     * demande un `resync`, on oublie tout et on repart de sa réponse ; vrai dans ce cas.
     * Sinon, son état fait foi et les événements manqués vont suivre.
     */
    fun onHello(hello: Hello, sentSince: Long?): Boolean {
        val fresh = sentSince == null || hello.resync || journal == null
        if (fresh) {
            records.clear()
            lastSeq = hello.lastSeq
        } else {
            val listed = hello.sessions.mapNotNull { it.string("sessionId") }.toSet()
            records.keys.retainAll(listed)
        }
        journal = hello.journal
        hello.sessions.forEach { updateMeta(it) }
        approvals.clear()
        hello.approvals.forEach(::addApproval)
        return fresh
    }

    /**
     * Réponse du pont à une requête de l'app, aiguillée par sa méthode. Vrai si l'état est reparti
     * de zéro (`hello` sans rattrapage) : il faut alors recharger le journal des sessions affichées.
     */
    fun onResponse(method: String, params: JsonObject, result: JsonObject): Boolean {
        when (method) {
            "hello" -> return Hello.parse(result)?.let { onHello(it, params.long("since")) } ?: false
            "session.snapshot" -> result.obj("session")?.let(::updateMeta)
            "agents.versions", "agents.list" -> onVersions(result)
            "session.events" -> params.string("sessionId")?.let { id ->
                onHistory(id, result.array("events")?.mapNotNull(Frames::parseEvent).orEmpty())
            }
        }
        return false
    }

    /** Réponse à `agents.versions`. */
    fun onVersions(result: JsonObject) {
        agents = result.array("agents")?.objects().orEmpty().mapNotNull { entry ->
            val agent = entry.string("agent")?.let { Agent.fromId(it, entry.string("name")) } ?: return@mapNotNull null
            AgentVersion(
                agent = agent,
                version = entry.string("binary")?.let(::versionNumber),
                // Pas d'avertissement si le pont ne dit rien : on n'affirme que ce qu'il affirme.
                isTested = entry.boolean("isTested") ?: true,
                available = entry.boolean("available") ?: true,
                connected = entry.obj("authentication")?.boolean("connected"),
                running = entry.boolean("running") ?: false,
                configOptions = decodeOptions(entry["configOptions"]),
            )
        }
        records.values.forEach { record ->
            agents.firstOrNull { it.agent == record.meta.agent }?.let { record.meta = record.meta.copy(agent = it.agent) }
        }
    }

    /** Applique un événement. Faux s'il est déjà connu (`seq` ≤ dernier vu) : jamais de doublon. */
    fun apply(event: BridgeEvent): Boolean {
        lastSeq?.let { if (event.seq <= it) return false }
        lastSeq = event.seq
        when (event.type) {
            "session" -> updateMeta(event.data)
            "approval_request" -> addApproval(event.data)
            "approval_resolved" -> event.data.string("approvalId")?.let(approvals::remove)
            else -> event.sessionId?.let(records::get)?.applyActivity(event)
        }
        if (isActivity(event)) event.sessionId?.let(records::get)?.touch(event.at)
        return true
    }

    /**
     * Journal d'une session (`session.events`) : refait son plan et sa dernière action. Les événements
     * plus récents que le dernier vu sont laissés au direct, qui les apportera.
     */
    fun onHistory(sessionId: String, events: List<BridgeEvent>) {
        val record = records[sessionId] ?: return
        record.clearActivity()
        val upTo = lastSeq
        for (event in events) {
            if (event.sessionId != sessionId || (upTo != null && event.seq > upTo)) continue
            record.applyActivity(event)
            if (isActivity(event)) record.touch(event.at)
        }
    }

    fun removeApproval(id: ApprovalId) {
        approvals.remove(id.value)
    }

    fun pendingApprovals(): List<Approval> = approvals.values.toList()

    fun sessions(): List<Session> = records.values.mapNotNull { it.toSession() }

    /** Le fil d'une session ; `null` si elle est inconnue. [loaded] : son journal a été lu. */
    fun thread(sessionId: String, loaded: Boolean): SessionThread? = records[sessionId]?.thread?.snapshot(loaded)

    fun knows(sessionId: String) = sessionId in records

    /**
     * Ajoute au fil un message envoyé, ou une interruption, sans passer par le pont : la passerelle
     * factice n'en a pas. Faux si la session est inconnue.
     */
    fun addLocalMessage(sessionId: String, text: String): Boolean =
        records[sessionId]?.thread?.addUserMessage(text) != null

    fun addLocalInterruption(sessionId: String): Boolean = records[sessionId]?.thread?.addInterruption() != null

    /** Les [count] premières sessions de la vue d'ensemble : celles dont on charge le journal. */
    fun firstSessions(count: Int): List<String> =
        sessions().sortedWith(Overview.order).take(count).map { it.id.value }

    private fun updateMeta(data: JsonObject) {
        val meta = Meta.parse(data, agents.map { it.agent }) ?: return
        val record = records[meta.id]
        when {
            record == null -> records[meta.id] = SessionRecord(meta)
            // Un rattrapage rejoue des états plus anciens que celui de `hello` : on ne recule pas.
            meta.updatedAt >= record.meta.updatedAt -> record.meta = meta
        }
    }

    private fun addApproval(data: JsonObject) {
        val id = data.string("approvalId") ?: return
        val sessionId = data.string("sessionId") ?: return
        val toolCall = data.obj("toolCall")
        val raw = toolCall?.obj("rawInput")
        val command = raw?.get("command")?.let { value ->
            when (value) {
                is JsonPrimitive -> value.takeIf { it.isString }?.content
                is JsonArray -> value.strings().joinToString(" ")
                else -> null
            }
        }
        val title = toolCall?.string("title")?.let(::oneLine)
        val kind = toolCall?.string("kind")
        val path = toolCall?.let(::pathOf)?.let { relativeTo(it, records[sessionId]?.meta?.cwd) }
        approvals[id] = when {
            kind == "switch_mode" || data.string("plan") != null -> Approval(ApprovalId(id), SessionId(sessionId), ApprovalKind.OTHER, title)
            command != null || kind == "execute" -> Approval(ApprovalId(id), SessionId(sessionId), ApprovalKind.COMMAND, command ?: title)
            kind in FILE_KINDS || path != null -> Approval(ApprovalId(id), SessionId(sessionId), ApprovalKind.FILE_CHANGE, path ?: title)
            else -> Approval(ApprovalId(id), SessionId(sessionId), ApprovalKind.OTHER, title)
        }.copy(
            reason = raw?.string("description") ?: raw?.string("justification"),
            workingDirectory = raw?.string("cwd"),
            paths = (toolCall?.array("locations")?.objects().orEmpty().mapNotNull { it.string("path") } +
                toolCall?.array("content")?.objects().orEmpty().mapNotNull { it.string("path") }).distinct(),
            plan = data.string("plan") ?: raw?.string("plan"),
            changes = toolCall?.array("content")?.objects().orEmpty().filter { it.string("type") == "diff" }.mapNotNull {
                ApprovalChange(it.string("path") ?: return@mapNotNull null, it.string("oldText"), it.string("newText") ?: return@mapNotNull null)
            },
            options = data.array("options")?.objects().orEmpty().mapNotNull {
                ApprovalOption(it.string("optionId") ?: return@mapNotNull null, when (it.string("kind")) {
                    "allow_once" -> ApprovalOptionKind.ALLOW_ONCE
                    "allow_always" -> ApprovalOptionKind.ALLOW_ALWAYS
                    "reject_once" -> ApprovalOptionKind.REJECT_ONCE
                    "reject_always" -> ApprovalOptionKind.REJECT_ALWAYS
                    else -> ApprovalOptionKind.OTHER
                }, it.string("name").orEmpty())
            },
        )
    }

    private inner class SessionRecord(var meta: Meta) {
        private var lastEventAt: Instant? = null
        private var plan: Progress? = null

        /** Appels d'outils, du plus ancien au plus récent commencé. */
        private val calls = LinkedHashMap<String, ToolCall>()

        /** Le fil complet, pour l'écran de la session. */
        val thread = ThreadBuilder(cwd = { meta.cwd })

        fun touch(at: Instant?) {
            if (at != null && (lastEventAt == null || at > lastEventAt!!)) lastEventAt = at
        }

        fun clearActivity() {
            lastEventAt = null
            plan = null
            calls.clear()
            thread.clear()
        }

        fun applyActivity(event: BridgeEvent) {
            thread.apply(event)
            if (event.type == "turn_end") {
                val cancelled = event.data.string("stopReason") == "cancelled"
                calls.replaceAll { _, call -> call.endTurn(cancelled) }
                return
            }
            if (event.type != "session_update") return
            val update = event.data
            when (update.string("sessionUpdate")) {
                "plan" -> plan = progressOf(update.array("entries"))
                "tool_call" -> update.string("toolCallId")?.let { id ->
                    calls.remove(id)
                    calls[id] = ToolCall.EMPTY.merge(update)
                    while (calls.size > MAX_CALLS) calls.remove(calls.keys.first())
                }
                "tool_call_update" -> update.string("toolCallId")?.let { id ->
                    // Échec du sandbox de Codex, déjà relayé : l'app retire cet appel (ADR 02).
                    if (update.obj("_meta")?.obj("regie")?.string("hidden") != null) {
                        calls.remove(id)
                    } else {
                        calls[id] = (calls[id] ?: ToolCall.EMPTY).merge(update)
                    }
                }
            }
        }

        fun toSession(): Session {
            val m = meta
            val pending = m.pendingApprovals.firstNotNullOfOrNull { approvals[it] }
                ?: m.pendingApprovals.firstOrNull()?.let { Approval(ApprovalId(it), SessionId(m.id), ApprovalKind.OTHER, null) }
            val lastCall = calls.values.lastOrNull { it.description != null }
            return Session(
                id = SessionId(m.id),
                agent = m.agent,
                project = m.project,
                branch = m.branch,
                title = m.title,
                status = m.status,
                active = m.active,
                updatedAt = m.updatedAt,
                lastActivityAt = lastEventAt?.takeIf { it > m.updatedAt } ?: m.updatedAt,
                progress = plan,
                lastAction = lastCall?.let { LastAction(it.description!!, it.status) },
                pendingApproval = pending,
                lastError = m.lastError,
                modifiedFiles = m.modifiedFiles,
                approvalMode = m.approvalMode, configOptions = m.configOptions,
            )
        }
    }

    private data class ToolCall(val title: String?, val command: String?, val status: ActionStatus?) {
        val description: String? get() = command ?: title

        /** Fin du tour : une action pas finie est interrompue, ou n'a plus de statut. */
        fun endTurn(cancelled: Boolean) =
            if (status == ActionStatus.PENDING || status == ActionStatus.IN_PROGRESS) copy(status = if (cancelled) ActionStatus.INTERRUPTED else null) else this

        fun merge(update: JsonObject) = ToolCall(
            title = update.string("title")?.let(::oneLine) ?: title,
            command = update.obj("rawInput")?.let(::commandOf) ?: command,
            status = actionStatusOf(update.string("status")) ?: status,
        )

        companion object {
            val EMPTY = ToolCall(null, null, null)
        }
    }

    private data class Meta(
        val id: String,
        val agent: Agent,
        val project: String,
        val cwd: String?,
        val branch: String?,
        val title: String?,
        val status: SessionStatus,
        val active: Boolean,
        val updatedAt: Instant,
        val lastError: String?,
        val pendingApprovals: List<String>,
        val modifiedFiles: List<String>,
        val approvalMode: String, val configOptions: List<app.regie.domain.model.SessionOption>,
    ) {
        companion object {
            fun parse(data: JsonObject, agents: List<Agent>): Meta? = Meta(
                id = data.string("sessionId") ?: return null,
                agent = data.string("agent")?.let { id -> agents.firstOrNull { it.id == id } ?: Agent.fromId(id) } ?: return null,
                project = data.string("project") ?: return null,
                cwd = data.string("cwd"),
                branch = data.string("branch"),
                title = data.string("title")?.let(::oneLine),
                status = statusOf(data.string("status")) ?: return null,
                active = data.boolean("active") ?: false,
                updatedAt = data.instant("updatedAt") ?: data.instant("createdAt") ?: Instant.fromEpochMilliseconds(0),
                lastError = data.string("lastError")?.let(::oneLine),
                pendingApprovals = data.array("pendingApprovals")?.strings().orEmpty(),
                modifiedFiles = data.array("modifiedFiles")?.strings().orEmpty(),
                approvalMode = data.string("approvalMode") ?: "unknown", configOptions = decodeOptions(data["configOptions"]),
            )
        }
    }

    companion object {
        private const val MAX_CALLS = 32

        /** Mises à jour de réglages, pas d'activité de l'agent : elles ne rajeunissent pas la session. */
        private val SETTINGS_UPDATES = setOf(
            "available_commands_update", "usage_update", "config_option_update", "current_mode_update", "session_info_update",
        )

        private fun isActivity(event: BridgeEvent) =
            event.type != "session_update" || event.data.string("sessionUpdate") !in SETTINGS_UPDATES
        private val FILE_KINDS = setOf("edit", "delete", "move")
        private val VERSION = Regex("""\d+(?:\.\d+)+""")
        private val SPACES = Regex("""\s+""")

        /** « 2.1.322 (Claude Code) » → « 2.1.322 » ; « codex-cli 0.167.2 » → « 0.167.2 ». */
        fun versionNumber(binary: String): String? = VERSION.find(binary)?.value

        private fun agentOf(name: String?): Agent? = name?.let { Agent.fromId(it) }

        private fun statusOf(status: String?): SessionStatus? = when (status) {
            "working" -> SessionStatus.WORKING
            "awaiting_approval" -> SessionStatus.AWAITING_APPROVAL
            "idle" -> SessionStatus.IDLE
            "stopped" -> SessionStatus.STOPPED
            "failed" -> SessionStatus.FAILED
            else -> null
        }

        internal fun actionStatusOf(status: String?): ActionStatus? = when (status) {
            "pending" -> ActionStatus.PENDING
            "in_progress" -> ActionStatus.IN_PROGRESS
            "completed" -> ActionStatus.COMPLETED
            "failed" -> ActionStatus.FAILED
            else -> null
        }

        /** Le plan ACP : étapes faites, total, et l'étape en cours. Un plan vide n'en est pas un. */
        private fun progressOf(entries: JsonArray?): Progress? {
            val steps = entries?.objects().orEmpty()
            if (steps.isEmpty()) return null
            return Progress(
                done = steps.count { it.string("status") == "completed" },
                total = steps.size,
                currentStep = steps.firstOrNull { it.string("status") == "in_progress" }?.string("content")?.let(::oneLine),
            )
        }

        /** La commande d'un appel d'outil (`rawInput.command`, texte ou liste d'arguments). */
        internal fun commandOf(rawInput: JsonObject): String? = when (val command = rawInput["command"]) {
            is JsonPrimitive -> command.takeIf { it.isString }?.content
            is JsonArray -> command.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" ")
            else -> null
        }?.let(::oneLine)?.ifEmpty { null }

        private fun pathOf(toolCall: JsonObject): String? =
            toolCall.array("locations")?.objects()?.firstNotNullOfOrNull { it.string("path") }
                ?: toolCall.array("content")?.objects()?.firstNotNullOfOrNull { it.string("path") }

        internal fun relativeTo(path: String, cwd: String?): String =
            if (cwd != null && path.startsWith("$cwd/")) path.removePrefix("$cwd/") else path

        internal fun oneLine(text: String): String = text.replace(SPACES, " ").trim()
    }
}
