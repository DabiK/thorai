package app.regie.game.ui.atlas

import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.render.GroundPalette
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetJsonTest {
    private fun fixture(name: String) =
        checkNotNull(javaClass.classLoader?.getResource("game-fixture/$name")).readText()

    /** L'exemple du contrat, dans `docs/rebrand/plan-dev-jeu.md`. */
    private val contractExample = """
        {
          "pixel": 1,
          "frames": { "coursier.marche.bas.0": {"x":0,"y":0,"w":24,"h":32,"ax":12,"ay":31},
                      "coursier.marche.bas.1": {"x":24,"y":0,"w":24,"h":32,"ax":12,"ay":31},
                      "coursier.marche.bas.2": {"x":48,"y":0,"w":24,"h":32,"ax":12,"ay":31},
                      "maison.chaumiere": {"x":0,"y":32,"w":96,"h":80,"ax":48,"ay":79} },
          "anims":  { "coursier.marche.bas": {"frames":["coursier.marche.bas.0","coursier.marche.bas.1","coursier.marche.bas.2","coursier.marche.bas.1"], "ms":140} },
          "objects":{ "maison.chaumiere": {"frame":"maison.chaumiere","footprint":{"w":6,"h":4},"door":{"tx":3,"ty":3},"roofKeys":["#FF00A0","#C8007E","#900060"]} }
        }
    """.trimIndent()

    @Test
    fun `the contract example is read entirely`() {
        val spec = AssetJson.parseAtlas(contractExample)
        assertEquals(1, spec.pixel)
        assertEquals(AtlasFrame(0, 0, 24, 32, 12, 31), spec.frames["coursier.marche.bas.0"])
        val walk = spec.anims.getValue("coursier.marche.bas")
        assertEquals(140, walk.ms)
        assertEquals("coursier.marche.bas.0", walk.frameAt(0))
        assertEquals("coursier.marche.bas.1", walk.frameAt(140))
        assertEquals("coursier.marche.bas.1", walk.frameAt(3 * 140 + 10))
        assertEquals("coursier.marche.bas.0", walk.frameAt(4 * 140))
        val house = spec.objects.getValue("maison.chaumiere")
        assertEquals(Footprint(6, 4), house.footprint)
        assertEquals(TilePos(3, 3), house.door)
        assertEquals(listOf(0xFFFF00A0.toInt(), 0xFFC8007E.toInt(), 0xFF900060.toInt()), house.roofKeys)
    }

    @Test
    fun `the fixture atlas is valid`() {
        val spec = AssetJson.parseAtlas(fixture("maisons.json"))
        assertEquals(setOf("maison.test", "arbre.0", "arbre.1"), spec.frames.keys)
        assertEquals(3, spec.objects.getValue("maison.test").roofKeys.size)
    }

    @Test
    fun `a broken contract is refused with its reason`() {
        assertFailsWith<IllegalArgumentException> { AssetJson.parseAtlas(fixture("decor.json")) }
        val unknownFrame = """{"frames": {}, "anims": {"a": {"frames": ["x"], "ms": 100}}}"""
        assertEquals("Animation a : image x inconnue", assertFailsWith<IllegalArgumentException> { AssetJson.parseAtlas(unknownFrame) }.message)
        val missingField = """{"frames": {"a": {"x": 0, "y": 0, "w": 1, "h": 1, "ax": 0}}}"""
        assertEquals("Champ ay manquant", assertFailsWith<IllegalArgumentException> { AssetJson.parseAtlas(missingField) }.message)
        val twoKeys = """{"frames": {"a": {"x":0,"y":0,"w":1,"h":1,"ax":0,"ay":0}}, "objects": {"a": {"roofKeys": ["#000000", "#111111"]}}}"""
        assertFailsWith<IllegalArgumentException> { AssetJson.parseAtlas(twoKeys) }
    }

    @Test
    fun `the palette gives roles and colours, defaults fill the gaps`() {
        val palette = AssetJson.parsePalette(fixture("palette.json"))
        assertEquals(0xFF63A53A.toInt(), palette.grass.base)
        assertEquals(0xFF222F36.toInt(), palette.outline)
        assertEquals(GroundPalette.Default.grass.light, palette.grass.light)
        assertEquals(listOf(0xFF222F36.toInt(), 0xFF1F583A.toInt()), palette.colors)
    }

    @Test
    fun `colours are read in RGB or ARGB`() {
        assertEquals(0xFF1F583A.toInt(), AssetJson.color("#1F583A"))
        assertEquals(0x801F583A.toInt(), AssetJson.color("#801F583A"))
        assertFailsWith<IllegalArgumentException> { AssetJson.color("1F583A") }
        assertFailsWith<IllegalArgumentException> { AssetJson.color("#1F58") }
    }

    @Test
    fun `roof keys become the session colours, other pixels stay`() {
        val keys = listOf(0xFFFF00A0.toInt(), 0xFFC8007E.toInt(), 0xFF900060.toInt())
        val roof = RoofColors(light = 0xFF111111.toInt(), base = 0xFF222222.toInt(), shadow = 0xFF333333.toInt())
        val wall = 0xFFF1D8A6.toInt()
        val transparentKey = 0x00FF00A0
        val pixels = intArrayOf(keys[0], keys[1], keys[2], wall, transparentKey)
        assertContentEquals(
            intArrayOf(roof.light, roof.base, roof.shadow, wall, transparentKey),
            Recolor.roof(pixels, keys, roof),
        )
        assertContentEquals(pixels, Recolor.roof(pixels, emptyList(), roof))
    }
}
