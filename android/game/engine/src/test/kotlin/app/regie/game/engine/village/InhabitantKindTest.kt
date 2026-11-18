package app.regie.game.engine.village

import app.regie.domain.model.Agent
import kotlin.test.Test
import kotlin.test.assertEquals

class InhabitantKindTest {
    @Test
    fun `chaque agent connu a son habitant`() {
        assertEquals(InhabitantKind.SCRIBE, InhabitantKind.of(Agent.CLAUDE))
        assertEquals(InhabitantKind.BRICOLEUR, InhabitantKind.of(Agent.CODEX))
        assertEquals(InhabitantKind.CARTOGRAPHE, InhabitantKind.of(Agent.fromId("opencode")!!))
    }

    @Test
    fun `un autre agent annonce par le pont prend l'habitant par defaut`() {
        assertEquals(InhabitantKind.FALLBACK, InhabitantKind.of(Agent.fromId("gemini")!!))
        // Seul l'identifiant compte, pas le libellé affiché.
        assertEquals(InhabitantKind.FALLBACK, InhabitantKind.of(Agent("autre", label = "opencode")))
        assertEquals(InhabitantKind.CARTOGRAPHE, InhabitantKind.of(Agent("opencode", label = "OpenCode Go")))
    }

    @Test
    fun `deux sessions du meme agent donnent deux maisons, chacune avec son habitant`() {
        val opencode = Agent.fromId("opencode")!!
        val village = VillageGenerator(InMemoryVillageLayoutStore())
            .generate(overview(session("a", "regie", opencode), session("b", "regie", opencode)))
        assertEquals(2, village.houses.size)
        assertEquals(listOf(InhabitantKind.CARTOGRAPHE, InhabitantKind.CARTOGRAPHE), village.houses.map { it.inhabitant })
    }
}
