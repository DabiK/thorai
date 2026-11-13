package app.regie.voicelab

import android.content.Context
import androidx.core.content.edit
import app.regie.voice.elevenlabs.ScribeModel

/** Settings of the trial app, read at each use: changing one applies at once, without restarting. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var provider: String
        get() = prefs.getString("provider", "elevenlabs") ?: "elevenlabs"
        set(value) = prefs.edit { putString("provider", value) }

    var scribeModel: ScribeModel
        get() = ScribeModel.entries.firstOrNull { it.id == prefs.getString("scribe", null) } ?: ScribeModel.V3_LIVE
        set(value) = prefs.edit { putString("scribe", value.id) }

    var voiceId: String?
        get() = prefs.getString("voice_id", null)
        set(value) = prefs.edit { putString("voice_id", value) }

    var voiceName: String?
        get() = prefs.getString("voice_name", null)
        set(value) = prefs.edit { putString("voice_name", value) }

    var hotwords: Boolean
        get() = prefs.getBoolean("hotwords", false)
        set(value) = prefs.edit { putBoolean("hotwords", value) }
}
