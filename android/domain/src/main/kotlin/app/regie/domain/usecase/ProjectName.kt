package app.regie.domain.usecase

import java.text.Normalizer
import java.util.Locale

/** Same portable folder contract as the bridge; never accepts a path. */
fun projectFolderName(raw: String): String? {
    val text = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).trim()
    if (!text.matches(Regex("[a-z0-9 _’'-]+"))) return null
    return text.replace(Regex("[ _’'-]+"), "-").trim('-').takeIf { it.isNotEmpty() && it.length <= 80 }
}
