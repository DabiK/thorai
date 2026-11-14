package app.regie.domain.model

enum class DictationProvider { LOCAL, ELEVENLABS }
data class VoiceSettings(val provider: DictationProvider = DictationProvider.LOCAL, val keyConfigured: Boolean = false)
