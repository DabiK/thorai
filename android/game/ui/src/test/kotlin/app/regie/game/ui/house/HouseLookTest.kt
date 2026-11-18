package app.regie.game.ui.house

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Agent
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.ThreadLine
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.render.ArtPoint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HouseLookTest {
    @Test
    fun `the inhabitant and its room follow the agent of the session`() {
        assertEquals("fond.interieur.scribe", HouseLook.interior(InhabitantKind.of(Agent.CLAUDE)))
        assertEquals("fond.interieur.bricoleur", HouseLook.interior(InhabitantKind.of(Agent.CODEX)))
        assertEquals("fond.interieur.cartographe", HouseLook.interior(InhabitantKind.of(Agent("opencode", "OpenCode"))))
        assertEquals("fond.interieur.scribe", HouseLook.interior(InhabitantKind.of(Agent("autre"))))
    }

    @Test
    fun `the inhabitant works, waits, rests or sleeps with the session`() {
        val kind = InhabitantKind.BRICOLEUR
        fun anim(status: SessionStatus, active: Boolean = true) = HouseLook.inhabitant(kind, HouseLook.pose(session(status, active)))
        assertEquals("bricoleur.travail", anim(SessionStatus.WORKING))
        assertEquals("bricoleur.attente", anim(SessionStatus.AWAITING_APPROVAL))
        assertEquals("bricoleur.repos", anim(SessionStatus.IDLE))
        assertEquals("bricoleur.repos", anim(SessionStatus.FAILED))
        assertEquals("bricoleur.sommeil", anim(SessionStatus.STOPPED))
        assertEquals("bricoleur.sommeil", anim(SessionStatus.IDLE, active = false))
    }

    @Test
    fun `the courier faces the inhabitant and speaks while dictating`() {
        assertEquals("coursier.debout.haut", HouseLook.courier(speaking = false))
        assertEquals("coursier.parle", HouseLook.courier(speaking = true))
    }

    @Test
    fun `the activity bubble shows the latest action only while the agent works`() {
        val edit = ThreadLine.Edit(3, "android/Overview.kt", null, ActionStatus.IN_PROGRESS)
        val lines = listOf(ThreadLine.Read(1, listOf("a.kt"), false), edit, ThreadLine.AgentText(4, "Je modifie.", streaming = true))
        assertSame(edit, HouseLook.activity(session(SessionStatus.WORKING), lines))
        assertNull(HouseLook.activity(session(SessionStatus.IDLE), lines))
    }

    @Test
    fun `the dialog box takes the latest agent text as it is`() {
        val lines = listOf(ThreadLine.AgentText(1, "Premier.", false), ThreadLine.UserMessage(2, "Et ?", false), ThreadLine.AgentText(3, "  **Dernier**, tel quel.  ", false))
        assertEquals("  **Dernier**, tel quel.  ", HouseLook.lastReply(lines)?.text)
        assertNull(HouseLook.lastReply(listOf(ThreadLine.UserMessage(1, "Bonjour", false))))
    }

    @Test
    fun `interior scenes are read from the backgrounds atlas`() {
        val json = """{"pixel":1,"frames":{},"scenes":{
            "fond.interieur.scribe":{"piece":{"x":86,"y":33,"w":307,"h":204},"habitant":{"x":236,"y":178},"entree":{"x":238,"y":218}},
            "fond.cassé":{"habitant":{"x":1}}}}"""
        val scenes = InteriorScenes.parse(json)
        assertEquals(InteriorScene(33, ArtPoint(236, 178), ArtPoint(238, 218)), scenes["fond.interieur.scribe"])
        assertNull(scenes["fond.cassé"])
        assertEquals(emptyMap(), InteriorScenes.parse("""{"frames":{}}"""))
    }

    @Test
    fun `actions sit close together, messages breathe`() {
        val read = ThreadLine.Read(1, listOf("a"), false)
        val run = ThreadLine.Run(2, "ls", null, null)
        assertEquals(0, threadGapBefore(null, read))
        assertEquals(6, threadGapBefore(read, run))
        assertEquals(26, threadGapBefore(run, ThreadLine.AgentText(3, "x", false)))
    }
}
