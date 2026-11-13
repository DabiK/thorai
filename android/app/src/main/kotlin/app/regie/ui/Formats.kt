package app.regie.ui

import app.regie.domain.model.DiffStat
import kotlin.time.Duration

private const val NBSP = ' '

/** Ancienneté de la dernière action, dans la plus grande unité entière : « 12 s », « 6 min », « 2 h », « 3 j ». */
fun formatAge(age: Duration): String {
    val positive = if (age.isNegative()) Duration.ZERO else age
    return when {
        positive.inWholeMinutes < 1 -> "${positive.inWholeSeconds}${NBSP}s"
        positive.inWholeHours < 1 -> "${positive.inWholeMinutes}${NBSP}min"
        positive.inWholeDays < 1 -> "${positive.inWholeHours}${NBSP}h"
        else -> "${positive.inWholeDays}${NBSP}j"
    }
}

/** Lignes ajoutées et retirées, avec un vrai signe moins : « +86 −12 ». */
fun formatDiffStat(diff: DiffStat): String = "+${diff.added} −${diff.removed}"
