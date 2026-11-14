package app.regie.voice

import android.content.Context
import androidx.core.content.edit
import app.regie.domain.model.DictationProvider
import app.regie.domain.model.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoicePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun select(provider: DictationProvider) {
        prefs.edit { putString("provider", provider.name) }
        mutable.value = read()
    }
    fun saveKey(key: String) {
        if (key.isBlank()) secrets.delete(SecretStore.ELEVENLABS_KEY) else secrets.write(SecretStore.ELEVENLABS_KEY, key.trim())
        mutable.value = read()
    }
    fun saveCommandKey(key: String) {
        if (key.isBlank()) secrets.delete("mistral_command_key") else secrets.write("mistral_command_key", key.trim())
        mutable.value = read()
    }
    fun commandKey(): String? = secrets.read("mistral_command_key")
    fun apiKey(): String? = secrets.read(SecretStore.ELEVENLABS_KEY)
    private fun read() = VoiceSettings(
        if (prefs.getString("provider", null) == "ELEVENLABS") DictationProvider.ELEVENLABS else DictationProvider.LOCAL,
        secrets.contains(SecretStore.ELEVENLABS_KEY),
        secrets.contains("mistral_command_key"),
    )
}
