package app.regie.voice.api

/** What went wrong with a voice provider, in terms the interface can show to the user. */
sealed interface VoiceError {
    /** No network, or the connection dropped. */
    data object Offline : VoiceError

    /** The provider needs an API key and none was entered. */
    data object KeyMissing : VoiceError

    /** The provider refused the API key. */
    data object KeyRejected : VoiceError

    /** The account has used up its quota. */
    data object QuotaExceeded : VoiceError

    /** Too many requests: try again a bit later. */
    data object RateLimited : VoiceError

    /** A local model must be downloaded before use. */
    data object ModelMissing : VoiceError

    /** A setting is missing, for example the synthesis voice. [setting] names it. */
    data class NotConfigured(val setting: String) : VoiceError

    /** Any other provider error, with the provider's own [code] and [message]. */
    data class Provider(val code: String, val message: String? = null) : VoiceError
}

/** Thrown by flows that produce audio or data (synthesis, voice listing) when they fail. */
class VoiceException(val error: VoiceError, cause: Throwable? = null) : Exception(error.toString(), cause)
