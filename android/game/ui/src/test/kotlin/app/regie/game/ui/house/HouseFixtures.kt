package app.regie.game.ui.house

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Agent
import app.regie.domain.model.LineChanges
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.OptionValue
import app.regie.domain.model.PlanStep
import app.regie.domain.model.PlanStepStatus
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionOption
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ThreadLine
import app.regie.domain.model.ToolKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/** Des données d'exemple pour les tests, pas des textes de l'app. */
fun session(status: SessionStatus = SessionStatus.WORKING, active: Boolean = true, agent: Agent = Agent.CLAUDE) = Session(
    SessionId("s1"), agent, "regie", "ticket/22-tri", "tri des sessions", status, active,
    Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0),
    approvalMode = "ask",
    configOptions = listOf(
        SessionOption("model", "Model", "model", "opus", listOf(OptionValue("opus", "Opus 5.5"), OptionValue("sonnet", "Sonnet 5"))),
        SessionOption("effort", "Effort", "thought_level", "medium", listOf(OptionValue("low", "Basse"), OptionValue("medium", "Moyenne"))),
        SessionOption("fast", "Mode rapide", null, "off", listOf(OptionValue("off", "Non"), OptionValue("on", "Oui"))),
    ),
)

const val REPLY = "Le tri est en place. J'écris maintenant le test qui vérifie `updatedAt`."

fun live(status: SessionStatus = SessionStatus.WORKING, agent: Agent = Agent.CLAUDE, plan: Boolean = true) = LiveSession(
    session = session(status, agent = agent),
    lines = listOf(
        ThreadLine.UserMessage(1, "Ajoute un tri par date dans la liste des sessions.", dictated = true),
        ThreadLine.Read(2, listOf("android/domain/Overview.kt", "android/app/OverviewScreens.kt"), inProgress = false),
        ThreadLine.Run(3, "./gradlew :domain:test", ActionStatus.COMPLETED, "BUILD SUCCESSFUL in 41s"),
        ThreadLine.AgentText(4, "La liste vient d'Overview. J'ajoute un tri sur updatedAt, puis un test.", streaming = false),
        ThreadLine.Edit(5, "android/domain/Overview.kt", LineChanges(12, 3), ActionStatus.IN_PROGRESS),
        ThreadLine.AgentText(6, REPLY, streaming = status == SessionStatus.WORKING),
    ),
    plan = if (!plan) null else listOf(
        PlanStep("Lire la liste", PlanStepStatus.DONE),
        PlanStep("Trouver où trier", PlanStepStatus.DONE),
        PlanStep("Ajouter le tri par date", PlanStepStatus.IN_PROGRESS),
        PlanStep("Écrire le test", PlanStepStatus.PENDING),
    ),
    modifiedFiles = listOf(ModifiedFile("android/domain/Overview.kt", LineChanges(12, 3))),
    context = null,
    loading = false,
    actionDetails = mapOf(3L to ThreadEntry.ToolCall(3, ToolKind.EXECUTE, "Run", command = "./gradlew :domain:test", output = "OverviewTest PASSED\nBUILD SUCCESSFUL in 41s")),
)

fun scrollInput() = ScrollInput(MutableSharedFlow(extraBufferCapacity = 16), MutableStateFlow(0f))

/** Les rappels reçus, dans l'ordre. */
class RecordingCallbacks : HouseCallbacks, DictationCallbacks {
    val events = mutableListOf<String>()
    override fun onKey(key: PadKey) { events += key.name }
    override fun onSpeak(held: Boolean) { events += "L2 $held" }
    override fun onOpenSettings(row: String?) { events += "settings $row" }
    override fun onSelectSetting(index: Int) { events += "select $index" }
    override fun onDraftChange(text: String) { events += "draft $text" }
    override fun onSend() { events += "send" }
    override fun onVoiceSettings() { events += "voice" }
}
