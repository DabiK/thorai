package app.regie.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

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

/** Ticks d'une seconde, pour que l'ancienneté avance à l'écran. */
@Composable
internal fun rememberNow(): State<Instant> = produceState(Clock.System.now()) {
    while (true) {
        delay(1_000)
        value = Clock.System.now()
    }
}
