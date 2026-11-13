package app.regie.voicelab

/**
 * What the trial app can be asked by adb, without touching the screen:
 * `adb shell am start -n app.regie.voicelab/.MainActivity --es <name> <value> …`.
 */
sealed interface Command {
    /** `--es provider local|elevenlabs`: chooses the provider (kept in the settings). */
    data class UseProvider(val provider: String) : Command

    /** `--es scribe scribe_v3_live|scribe_v2_realtime`: chooses the Scribe model. */
    data class UseScribe(val model: String) : Command

    /** `--ez hotwords true|false`: local hotwords on or off. */
    data class UseHotwords(val enabled: Boolean) : Command

    /** `--es download local`: downloads the local models. */
    data object DownloadLocal : Command

    /** `--es bench local|elevenlabs [--es only 02,13]`: replays the corpus in a provider. */
    data class RunBench(val provider: String, val only: Set<Int>?) : Command

    /** `--es speak "texte"`: reads a text aloud with the current provider. */
    data class Speak(val text: String) : Command

    companion object {
        /**
         * Commands of an intent, settings first so that `--es provider local --es speak …` speaks
         * with the local voice. [string] and [boolean] read the extras.
         */
        fun parse(string: (String) -> String?, boolean: (String) -> Boolean?): List<Command> = buildList {
            string("provider")?.let { add(UseProvider(it.trim())) }
            string("scribe")?.let { add(UseScribe(it.trim())) }
            boolean("hotwords")?.let { add(UseHotwords(it)) }
            string("download")?.takeIf { it.trim() == "local" }?.let { add(DownloadLocal) }
            string("bench")?.let { provider ->
                val only = string("only")?.split(',', ' ')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.ifEmpty { null }
                add(RunBench(provider.trim(), only))
            }
            string("speak")?.takeIf { it.isNotBlank() }?.let { add(Speak(it)) }
        }
    }
}
