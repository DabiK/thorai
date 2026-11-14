package app.regie.gateway.bridge

import app.regie.domain.model.*
import kotlinx.serialization.json.*

internal fun decodeOptions(raw: JsonElement?): List<SessionOption> = (raw as? JsonArray).orEmpty().mapNotNull { entry ->
    val option = entry as? JsonObject ?: return@mapNotNull null
    SessionOption(option.string("id") ?: return@mapNotNull null, option.string("name").orEmpty(), option.string("category"), option.string("currentValue"),
        (option["options"] as? JsonArray).orEmpty().mapNotNull { value ->
            val choice = value as? JsonObject ?: return@mapNotNull null
            OptionValue(choice.string("value") ?: return@mapNotNull null, choice.string("name").orEmpty())
        })
}
