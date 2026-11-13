package app.regie.gateway.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Les trames du pont (docs/protocole-pont.md, « Trames »). On lit l'arbre JSON tel quel :
// le pont relaie le schéma ACP sans le figer, l'app n'en prend que ce qu'elle montre.

/** Un événement du journal du pont. */
internal data class BridgeEvent(
    val seq: Long,
    val at: Instant?,
    val sessionId: String?,
    val type: String,
    val data: JsonObject,
)

/** Ce que le pont envoie : une réponse à une requête, ou un événement. */
internal sealed interface Incoming {
    data class Response(val id: Long?, val result: JsonObject?, val error: String?) : Incoming

    data class Event(val event: BridgeEvent) : Incoming
}

internal object Frames {
    private val json = Json { ignoreUnknownKeys = true }

    /** Lit une trame du pont ; `null` si elle n'est pas comprise (elle est alors ignorée). */
    fun parse(text: String): Incoming? = parseObject(text)?.let(::parse)

    fun parse(root: JsonObject): Incoming? {
        root.obj("event")?.let { return parseEvent(it)?.let(Incoming::Event) }
        if ("result" !in root && "error" !in root) return null
        return Incoming.Response(
            id = root.long("id"),
            result = root.obj("result"),
            error = root.obj("error")?.string("code"),
        )
    }

    fun parseEvent(element: JsonElement): BridgeEvent? {
        val event = element as? JsonObject ?: return null
        return BridgeEvent(
            seq = event.long("seq") ?: return null,
            at = event.instant("at"),
            sessionId = event.string("sessionId"),
            type = event.string("type") ?: return null,
            data = event.obj("data") ?: JsonObject(emptyMap()),
        )
    }

    fun request(id: Long, method: String, params: JsonObject = JsonObject(emptyMap())): String =
        buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()

    fun parseObject(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
}

// Lecture tolérante : une valeur absente, nulle ou d'un autre type vaut `null`.

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

internal fun JsonObject.instant(key: String): Instant? = string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()
