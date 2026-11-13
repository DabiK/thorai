package app.regie.voice.api

import kotlinx.coroutines.flow.Flow

/** Identifier of a provider, as stored in the settings ("elevenlabs", "local", "fake"…). */
@JvmInline
value class VoiceProviderId(val value: String) {
    override fun toString() = value
}

/** Whether a provider can be used right now. */
sealed interface ProviderStatus {
    data object Ready : ProviderStatus

    /** A model is being downloaded; [progress] in 0..1 when known. */
    data class Downloading(val progress: Float?) : ProviderStatus

    data class Unavailable(val error: VoiceError) : ProviderStatus
}

/** A voice provider: one adapter offering transcription and synthesis. */
interface VoiceProvider {
    val id: VoiceProviderId

    /** Name shown in the settings. */
    val displayName: String

    val speechToText: SpeechToText

    val textToSpeech: TextToSpeech

    /**
     * Gets the provider ready (checks its settings, downloads its models) and reports progress.
     * The flow ends with [ProviderStatus.Ready] or [ProviderStatus.Unavailable].
     */
    fun prepare(): Flow<ProviderStatus>
}

/**
 * The providers known to the app, chosen by id from a setting. Built by the composition root;
 * the API does not know the adapters.
 */
class VoiceProviders(providers: List<VoiceProvider>) {
    private val byId: Map<VoiceProviderId, VoiceProvider> = providers.associateBy { it.id }

    init {
        require(byId.size == providers.size) { "Duplicate voice provider ids: ${providers.map { it.id }}" }
    }

    val all: List<VoiceProvider> = providers.toList()

    operator fun get(id: VoiceProviderId): VoiceProvider =
        byId[id] ?: throw IllegalArgumentException("Unknown voice provider '$id', known: ${byId.keys}")

    fun getOrNull(id: VoiceProviderId): VoiceProvider? = byId[id]
}
