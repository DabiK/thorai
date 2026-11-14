package app.regie.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import app.regie.ui.theme.Palette

// Lightweight, language-neutral lexical colouring. Never changes or executes source text.
private val tokens = Regex("""(?m)(//[^\n]*|\#[^\n]*)|("(?:\\.|[^"\\])*"?|'(?:\\.|[^'\\])*'?)|\b(true|false|null|val|var|fun|class|const|let|function|return|if|else|for|while|import|from|def|public|private|suspend|async|await)\b|\b\d+(?:\.\d+)?\b""")

fun highlightCode(source: String): AnnotatedString = buildAnnotatedString {
    append(source)
    tokens.findAll(source).forEach { token ->
        val color = when {
            token.groups[1] != null -> Palette.textSubtle
            token.groups[2] != null -> Palette.done
            token.groups[3] != null -> Palette.running
            else -> Palette.waiting
        }
        addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
    }
}
