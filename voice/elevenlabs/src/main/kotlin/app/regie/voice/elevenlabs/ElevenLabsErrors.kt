package app.regie.voice.elevenlabs

import app.regie.voice.api.VoiceError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps ElevenLabs HTTP errors (`{"detail": {"status": …, "message": …}}`) to [VoiceError]. */
internal object ElevenLabsErrors {
    fun http(code: Int, body: String?): VoiceError {
        val detail = runCatching { Json.parseToJsonElement(body.orEmpty()).jsonObject["detail"] as? JsonObject }.getOrNull()
        val status = detail?.get("status")?.jsonPrimitive?.contentOrNull
        val message = detail?.get("message")?.jsonPrimitive?.contentOrNull
        return when {
            status == "quota_exceeded" -> VoiceError.QuotaExceeded
            code == 401 || code == 403 -> VoiceError.KeyRejected
            code == 429 -> VoiceError.RateLimited
            else -> VoiceError.Provider(status ?: "http_$code", message ?: body?.take(200))
        }
    }
}
