package app.regie.game.ui.approval

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalAnswer
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.ApprovalOption
import app.regie.domain.model.ApprovalOptionKind
import app.regie.domain.model.ApprovalReply
import app.regie.domain.model.SessionId
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.kit.TagState
import org.junit.Test
import kotlin.test.assertEquals

class ApprovalMenuModelTest {
    private val once = ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes")
    private val session = ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Always")
    private val reject = ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")
    private fun request(id: String = "a", vararg options: ApprovalOption) =
        Approval(ApprovalId(id), SessionId("s1"), ApprovalKind.COMMAND, "./gradlew check", options = options.toList())

    @Test
    fun `every existing choice is offered in key order, X only for the session option`() {
        val full = request("a", once, session, reject)
        val review = ApprovalReview(full, listOf(full), pending = true)
        assertEquals(
            listOf(
                ApprovalChoice(ApprovalDecision.ONCE, "A", true),
                ApprovalChoice(ApprovalDecision.REJECT, "B", true),
                ApprovalChoice(ApprovalDecision.SESSION, "X", true),
                ApprovalChoice(ApprovalDecision.REJECT, "L2", true, dictate = true),
            ),
            ApprovalMenuModel.choices(review, withInstruction = true),
        )
        assertEquals(0, ApprovalMenuModel.cursor(ApprovalMenuModel.choices(review, true)))

        // Une permission durable n'est jamais « pour la session » (ADR 08) ; dans une maison, L2 parle à l'agent.
        val durable = request("b", once, ApprovalOption("always", ApprovalOptionKind.ALLOW_ALWAYS, "Always"), reject)
        assertEquals(listOf("A", "B"), ApprovalMenuModel.choices(ApprovalReview(durable, listOf(durable), pending = true), withInstruction = false).map { it.key })
    }

    @Test
    fun `an answered request keeps its choices, all disabled, without cursor`() {
        val full = request("a", once, session, reject)
        val choices = ApprovalMenuModel.choices(ApprovalReview(full, listOf(full), pending = false, answer = ApprovalAnswer(ApprovalReply.ACCEPTED)), true)
        assertEquals(listOf(false, false, false, false), choices.map { it.enabled })
        assertEquals(-1, ApprovalMenuModel.cursor(choices))
    }

    @Test
    fun `the status says what the classic screen said`() {
        val a = request("a", once)
        val b = request("b", once)
        assertEquals(ApprovalStatus.WAITING, ApprovalMenuModel.status(ApprovalReview(a, listOf(a), pending = true)))
        assertEquals(ApprovalStatus.SENDING, ApprovalMenuModel.status(ApprovalReview(a, listOf(a), pending = true, sending = true)))
        assertEquals(ApprovalStatus.ACCEPTED, ApprovalMenuModel.status(ApprovalReview(a, listOf(a), answer = ApprovalAnswer(ApprovalReply.ACCEPTED))))
        assertEquals(ApprovalStatus.ACCEPTED_NEXT, ApprovalMenuModel.status(ApprovalReview(a, listOf(a, b), answer = ApprovalAnswer(ApprovalReply.ACCEPTED))))
        assertEquals(ApprovalStatus.UNKNOWN, ApprovalMenuModel.status(ApprovalReview(a, listOf(a), answer = ApprovalAnswer(ApprovalReply.UNKNOWN))))
        assertEquals(ApprovalStatus.NOT_SENT, ApprovalMenuModel.status(ApprovalReview(a, listOf(a), pending = true, answer = ApprovalAnswer(ApprovalReply.NOT_SENT))))
        assertEquals(ApprovalStatus.UNAVAILABLE, ApprovalMenuModel.status(ApprovalReview(a, emptyList())))
    }

    @Test
    fun `the session tag waits only while a decision is still to take`() {
        assertEquals(
            listOf(TagState.WAITING, TagState.WAITING, TagState.WAITING, TagState.IDLE, TagState.IDLE, TagState.IDLE, TagState.IDLE),
            listOf(
                ApprovalStatus.WAITING, ApprovalStatus.SENDING, ApprovalStatus.NOT_SENT,
                ApprovalStatus.ACCEPTED, ApprovalStatus.ACCEPTED_NEXT, ApprovalStatus.UNKNOWN, ApprovalStatus.UNAVAILABLE,
            ).map { ApprovalMenuModel.tag(it) },
        )
    }

    @Test
    fun `a decision is relayed with the gamepad key`() {
        assertEquals(PadKey.A, ApprovalMenuModel.key(ApprovalDecision.ONCE))
        assertEquals(PadKey.X, ApprovalMenuModel.key(ApprovalDecision.SESSION))
        assertEquals(PadKey.B, ApprovalMenuModel.key(ApprovalDecision.REJECT))
    }
}
