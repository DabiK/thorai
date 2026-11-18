package app.regie.game.engine.courier

import app.regie.domain.model.ApprovalId
import app.regie.domain.model.SessionId
import kotlin.time.Instant

/** Ce qu'apporte l'oiseau messager : les quatre événements qui demandent l'attention (PRD). */
enum class LetterKind { APPROVAL, QUESTION, FINISHED, FAILED }

/**
 * Une lettre du courrier. [title] et [detail] viennent tels quels du domaine (cas d'usage des
 * notifications) : le jeu ne les réécrit pas. [approval] : la validation à ouvrir, s'il y en a une.
 */
data class Letter(
    val key: String,
    val kind: LetterKind,
    val sessionId: SessionId,
    val approval: ApprovalId?,
    val title: String,
    val detail: String,
    val receivedAt: Instant,
    val read: Boolean = false,
)

/** L'ancienneté d'une lettre, arrondie vers le bas : « 5 min », « 2 h ». */
data class LetterAge(val unit: Unit, val value: Long) {
    enum class Unit { NOW, MINUTES, HOURS, DAYS }
}

/**
 * Le courrier : les lettres récentes, la plus récente d'abord, sans doublon d'un même événement.
 * Il ne garde que [MAX_LETTERS] lettres, en mémoire seulement : c'est un rappel, pas un journal.
 */
data class Courier(val letters: List<Letter> = emptyList()) {
    val unread: Int get() = letters.count { !it.read }

    /** La dernière lettre arrivée : celle que l'oiseau apporte. */
    val latest: Letter? get() = letters.firstOrNull()

    fun receive(letter: Letter): Courier =
        Courier((listOf(letter) + letters.filterNot { it.key == letter.key }).take(MAX_LETTERS))

    fun markAllRead(): Courier = Courier(letters.map { it.copy(read = true) })

    /** Une lettre ouverte est lue. */
    fun read(key: String): Courier = Courier(letters.map { if (it.key == key) it.copy(read = true) else it })

    /** La validation a été réglée, ici ou ailleurs : sa lettre n'attend plus rien. */
    fun settle(approval: ApprovalId): Courier = Courier(letters.map { if (it.approval == approval) it.copy(read = true) else it })

    companion object {
        const val MAX_LETTERS = 30

        fun age(receivedAt: Instant, now: Instant): LetterAge {
            val minutes = ((now - receivedAt).inWholeMinutes).coerceAtLeast(0)
            return when {
                minutes < 1 -> LetterAge(LetterAge.Unit.NOW, 0)
                minutes < 60 -> LetterAge(LetterAge.Unit.MINUTES, minutes)
                minutes < 24 * 60 -> LetterAge(LetterAge.Unit.HOURS, minutes / 60)
                else -> LetterAge(LetterAge.Unit.DAYS, minutes / (24 * 60))
            }
        }
    }
}
