package app.regie.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {

    @Test
    fun `bold, italic, inline code and links become styled text`() {
        assertEquals(
            listOf(
                MdBlock.Text(
                    listOf(
                        MdSpan("J'ai créé "),
                        MdSpan("resume.md", bold = true),
                        MdSpan(", lancé "),
                        MdSpan("npm test", code = true),
                        MdSpan(" "),
                        MdSpan("sans", italic = true),
                        MdSpan(" erreur, voir la doc."),
                    ),
                ),
            ),
            Markdown.parse("J'ai créé **resume.md**, lancé `npm test` *sans* erreur, voir [la doc](https://example.com)."),
        )
    }

    @Test
    fun `headings, lists and code blocks become blocks`() {
        val blocks = Markdown.parse(
            """
            ## Résumé

            - lu `notes.md`
            - modifié **todo.md**
              1. une ligne

            ```kotlin
            val x = 1
            ```
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MdBlock.Text(listOf(MdSpan("Résumé")), heading = true),
                MdBlock.Text(listOf(MdSpan("lu "), MdSpan("notes.md", code = true)), marker = "•", depth = 1),
                MdBlock.Text(listOf(MdSpan("modifié "), MdSpan("todo.md", bold = true)), marker = "•", depth = 1),
                MdBlock.Text(listOf(MdSpan("une ligne")), marker = "1.", depth = 2),
                MdBlock.CodeBlock("val x = 1"),
            ),
            blocks,
        )
    }

    @Test
    fun `a code block still open while the agent writes runs to the end, and closing it keeps it`() {
        val open = Markdown.parse("Voici :\n\n```console\n4 todo.md\n5 not")
        val closed = Markdown.parse("Voici :\n\n```console\n4 todo.md\n5 notes.md\n```\n")

        assertEquals(listOf(MdBlock.Text(listOf(MdSpan("Voici :"))), MdBlock.CodeBlock("4 todo.md\n5 not")), open)
        assertEquals(listOf(MdBlock.Text(listOf(MdSpan("Voici :"))), MdBlock.CodeBlock("4 todo.md\n5 notes.md")), closed)
    }

    @Test
    fun `every prefix of a streamed answer parses`() {
        val answer = "## Fait\n\n**Bilan** : `todo.md` modifié.\n\n```diff\n- [ ] x\n+ [x] x\n```\n\n1. *un*\n2. deux\n\n> cité"
        for (end in 0..answer.length) Markdown.parse(answer.substring(0, end))
    }

    @Test
    fun `a single line break stays a line break, and a quote is marked`() {
        assertEquals(
            listOf(MdBlock.Text(listOf(MdSpan("ligne 1\nligne 2")), quote = true)),
            Markdown.parse("> ligne 1\n> ligne 2"),
        )
    }
}
