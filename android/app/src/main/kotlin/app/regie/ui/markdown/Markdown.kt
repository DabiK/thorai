package app.regie.ui.markdown

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** Un morceau de texte et son style. Les liens sont rendus en texte. */
data class MdSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

/** Un bloc du texte d'un agent, prêt à dessiner. */
sealed interface MdBlock {
    /**
     * Paragraphe, titre (en gras), ou élément de liste : [marker] (« • », « 2. ») et [depth] pour
     * l'imbrication. [quote] : dans une citation.
     */
    data class Text(
        val spans: List<MdSpan>,
        val heading: Boolean = false,
        val marker: String? = null,
        val depth: Int = 0,
        val quote: Boolean = false,
    ) : MdBlock

    /** Bloc de code, tel quel. */
    data class CodeBlock(val code: String) : MdBlock
}

/**
 * Le sous-ensemble de markdown que l'écran rend lisible : gras, italique, code en ligne, blocs de
 * code, listes, titres, citations ; les liens et les images en texte. L'analyse est celle de
 * commonmark-java (ADR 07), qui ne refuse aucun texte : un bloc ``` encore ouvert, pendant que
 * l'agent écrit, va jusqu'à la fin du texte, comme il le fera une fois fermé.
 */
object Markdown {
    private val parser: Parser = Parser.builder().build()

    fun parse(text: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        BlockWalker(blocks).walk(parser.parse(text), depth = 0, quote = false)
        return blocks
    }

    private class BlockWalker(private val out: MutableList<MdBlock>) {
        fun walk(parent: Node, depth: Int, quote: Boolean) {
            var node = parent.firstChild
            while (node != null) {
                block(node, depth, quote, marker = null)
                node = node.next
            }
        }

        private fun block(node: Node, depth: Int, quote: Boolean, marker: String?) {
            when (node) {
                is Paragraph -> text(node, heading = false, marker, depth, quote)
                is Heading -> text(node, heading = true, marker, depth, quote)
                is FencedCodeBlock -> out += MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'))
                is IndentedCodeBlock -> out += MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'))
                is HtmlBlock -> out += MdBlock.Text(listOf(MdSpan(node.literal.orEmpty().trimEnd('\n'))), marker = marker, depth = depth, quote = quote)
                is BlockQuote -> walk(node, depth, quote = true)
                is BulletList -> items(node, depth, quote) { "•" }
                is OrderedList -> {
                    val start = node.markerStartNumber ?: 1
                    val delimiter = node.markerDelimiter ?: "."
                    items(node, depth, quote) { index -> "${start + index}$delimiter" }
                }
                is ThematicBreak -> out += MdBlock.Text(listOf(MdSpan("———")), depth = depth, quote = quote)
                else -> walk(node, depth, quote)
            }
        }

        /** Le premier bloc d'un élément porte la puce ; les suivants s'alignent dessous. */
        private fun items(list: Node, depth: Int, quote: Boolean, markerOf: (Int) -> String) {
            var item = list.firstChild
            var index = 0
            while (item != null) {
                if (item is ListItem) {
                    var child = item.firstChild
                    var first = true
                    if (child == null) out += MdBlock.Text(emptyList(), marker = markerOf(index), depth = depth + 1, quote = quote)
                    while (child != null) {
                        block(child, depth + 1, quote, marker = if (first) markerOf(index) else null)
                        first = false
                        child = child.next
                    }
                    index++
                }
                item = item.next
            }
        }

        private fun text(node: Node, heading: Boolean, marker: String?, depth: Int, quote: Boolean) {
            val spans = SpanWalker().also { it.visitChildren(node) }.spans()
            out += MdBlock.Text(spans, heading = heading, marker = marker, depth = depth, quote = quote)
        }
    }

    /** Les morceaux d'un paragraphe ; deux morceaux voisins de même style n'en font qu'un. */
    private class SpanWalker : AbstractVisitor() {
        private val spans = ArrayList<MdSpan>()
        private var bold = 0
        private var italic = 0

        fun spans(): List<MdSpan> = spans

        private fun add(text: String, code: Boolean = false) {
            if (text.isEmpty()) return
            val span = MdSpan(text, bold = bold > 0, italic = italic > 0, code = code)
            val last = spans.lastOrNull()
            if (last != null && last.copy(text = "") == span.copy(text = "")) {
                spans[spans.lastIndex] = last.copy(text = last.text + text)
            } else {
                spans += span
            }
        }

        override fun visit(text: Text) = add(text.literal.orEmpty())

        override fun visit(code: Code) = add(code.literal.orEmpty(), code = true)

        override fun visit(emphasis: Emphasis) {
            italic++
            visitChildren(emphasis)
            italic--
        }

        override fun visit(strongEmphasis: StrongEmphasis) {
            bold++
            visitChildren(strongEmphasis)
            bold--
        }

        // Les agents écrivent souvent ligne à ligne : un simple retour reste un retour.
        override fun visit(softLineBreak: SoftLineBreak) = add("\n")

        override fun visit(hardLineBreak: HardLineBreak) = add("\n")

        override fun visit(htmlInline: HtmlInline) = add(htmlInline.literal.orEmpty())

        public override fun visitChildren(parent: Node) = super.visitChildren(parent)
    }
}
