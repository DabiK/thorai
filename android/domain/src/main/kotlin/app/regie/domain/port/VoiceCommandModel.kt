package app.regie.domain.port

import app.regie.domain.model.VoiceCommandContext
import app.regie.domain.model.VoiceIntent

/** Proposes one function call, never performs it. null means not understood. */
fun interface VoiceCommandModel {
    suspend fun interpret(text: String, context: VoiceCommandContext): VoiceIntent?
}
