package app.regie.domain.port

import kotlinx.coroutines.flow.StateFlow

data class CaptureState(
    val text: String = "",
    val recording: Boolean = false,
    val complete: Boolean = false,
    val level: Float = 0f,
    val durationMs: Long = 0,
    val notice: String? = null,
    val error: String? = null,
)

/** A capture session. start replaces the previous state synchronously; finish keeps decoding. */
interface DictationCapture {
    val state: StateFlow<CaptureState>
    fun start(vocabulary: List<String>)
    fun finish()
    fun cancel()
}
