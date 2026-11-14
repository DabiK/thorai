package app.regie.gateway.bridge

import app.regie.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffTest {
    @Test fun `replacement keeps both line coordinates and whitespace`() {
        assertEquals(listOf(
            DiffLine(DiffKind.CONTEXT, "header", 1, 1),
            DiffLine(DiffKind.REMOVED, "  old", 2, null),
            DiffLine(DiffKind.ADDED, "  new", null, 2),
            DiffLine(DiffKind.ADDED, "extra", null, 3),
            DiffLine(DiffKind.CONTEXT, "tail", 3, 4),
        ), LineDiff.render("header\n  old\ntail\n", "header\n  new\nextra\ntail\n"))
    }
    @Test fun `creation deletion empty and CRLF keep exact contents without a phantom trailing row`() {
        assertEquals(listOf(DiffLine(DiffKind.ADDED, "a", null, 1), DiffLine(DiffKind.ADDED, "", null, 2)), LineDiff.render(null, "a\r\n\r\n"))
        assertEquals(listOf(DiffLine(DiffKind.REMOVED, "x", 1, null)), LineDiff.render("x\n", ""))
        assertEquals(emptyList(), LineDiff.render("", ""))
    }
    @Test fun `an update excerpt never claims coordinates in the whole file`() {
        val item = Frames.parseObject("""{"path":"/p/a.kt","oldText":"old\n","newText":"new\n","_meta":{"kind":"update"}}""")!!
        val fragment = DiffDecoder.read("a.kt", item, Frames.parseObject("{}")!!).single()
        assertEquals(false, fragment.fileCoordinates)
        assertEquals(listOf(DiffKind.REMOVED, DiffKind.ADDED), fragment.lines.map { it.kind })
        val created = Frames.parseObject("""{"path":"/p/a.kt","oldText":null,"newText":"new\n","_meta":{"kind":"add"}}""")!!
        assertEquals(true, DiffDecoder.read("a.kt", created, Frames.parseObject("{}")!!).single().fileCoordinates)
    }

}
