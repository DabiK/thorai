package app.regie.domain.model

import java.util.Locale

/** Opaque bridge identity; a new connector needs no Android release. Equality is by id. */
class Agent(val id: String, val label: String = id) {
    val name: String get() = id.uppercase(Locale.ROOT)
    override fun equals(other: Any?): Boolean = other is Agent && id == other.id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = label
    companion object {
        // Historical session/test identifiers, never a catalogue of selectable agents.
        val CLAUDE = Agent("claude", "Claude")
        val CODEX = Agent("codex", "Codex")
        fun fromId(id: String, label: String? = null): Agent? =
            id.takeIf { it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) }?.let {
                Agent(it, label ?: when (it) { "claude" -> "Claude"; "codex" -> "Codex"; else -> it })
            }
    }
}
