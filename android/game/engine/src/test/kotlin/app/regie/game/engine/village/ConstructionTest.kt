package app.regie.game.engine.village

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les quatre étapes du chantier suivent ce que le domaine expose, et ne reculent jamais. */
class ConstructionTest {
    private val id = SessionId("n")
    private val planned = Construction("regie")
    private val created = planned.launched(prompted = true).created(id)
    private fun live(status: SessionStatus, active: Boolean = true) = session("n", "regie", status = status).copy(active = active)
    private fun thread(vararg entries: ThreadEntry) = SessionThread(entries = entries.toList(), loaded = true)

    @Test
    fun `fiche, session creee, agent lance, premiere reponse`() {
        assertNull(planned.stage)
        assertEquals(ConstructionStage.PLOT, planned.launched(prompted = true).stage)
        assertEquals(ConstructionStage.FOUNDATIONS, created.stage)
        assertEquals(id, created.session)
        // Pas encore dans la vue d'ensemble : rien ne change.
        assertEquals(created, created.observed(null, null))

        val started = created.observed(live(SessionStatus.WORKING), thread(ThreadEntry.UserMessage(1, "Lis notes.md")))
        assertEquals(ConstructionStage.FRAME, started.stage)

        val answered = started.observed(live(SessionStatus.WORKING), thread(ThreadEntry.UserMessage(1, "Lis notes.md"), ThreadEntry.AgentText(2, "Je lis.")))
        assertEquals(ConstructionStage.FINISHED, answered.stage)
        assertTrue(answered.finished)
        // Une étape ne recule pas.
        assertEquals(answered, answered.observed(live(SessionStatus.WORKING), null))
    }

    @Test
    fun `une action ou une validation demandee comptent comme premiere reponse`() {
        val tool = created.observed(live(SessionStatus.WORKING), thread(ThreadEntry.ToolCall(2, app.regie.domain.model.ToolKind.READ, "Lecture")))
        assertEquals(ConstructionStage.FINISHED, tool.stage)
        val approval = live(SessionStatus.AWAITING_APPROVAL).copy(pendingApproval = Approval(ApprovalId("a"), id, ApprovalKind.COMMAND, "npm test"))
        assertEquals(ConstructionStage.FINISHED, created.observed(approval, null).stage)
    }

    @Test
    fun `sans consigne, la maison est finie quand l'agent attend la sienne`() {
        val idle = planned.launched(prompted = false).created(id).observed(live(SessionStatus.IDLE), thread())
        assertEquals(ConstructionStage.FINISHED, idle.stage)
        assertFalse(idle.prompted)
    }

    @Test
    fun `un echec arrete le chantier a son etape, avec le message du pont ou de l'agent`() {
        val refused = planned.launched(prompted = true).failedWith("Ce dossier existe déjà.")
        assertEquals(ConstructionStage.PLOT, refused.stage)
        assertEquals("Ce dossier existe déjà.", refused.error)
        assertEquals(refused, refused.created(id), "un chantier en échec ne repart pas")

        val failed = created.observed(live(SessionStatus.FAILED).copy(lastError = "Modèle refusé"), null)
        assertEquals(ConstructionStage.FRAME, failed.stage, "l'agent était lancé")
        assertEquals("Modèle refusé", failed.error)
        assertEquals(failed, failed.observed(live(SessionStatus.WORKING), thread(ThreadEntry.AgentText(3, "Bonjour"))))

        val turn = created.observed(live(SessionStatus.WORKING), thread(ThreadEntry.TurnFailed(4, "quota dépassé")))
        assertEquals("quota dépassé", turn.error)
        assertTrue(turn.failed)
    }
}
