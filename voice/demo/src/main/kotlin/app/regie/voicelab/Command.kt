package app.regie.voicelab

/**
 * What the trial app can be asked by adb, without touching the screen:
 * `adb shell am start -n app.regie.voicelab/.Adb --es <name> <value> …`.
 *
 * Some commands spend the user's ElevenLabs credits or download hundreds of megabytes: they are
 * only read from [ADB_ENTRY], an alias of the activity that the manifest reserves to callers
 * holding `android.permission.DUMP`, that is the adb shell, never another app.
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

    /**
     * `--es bench local|elevenlabs [--es only 02,13] [--es corpus user]`: replays the recordings
     * of `files/corpus/` (or of its sub-folder [corpus]) in a provider.
     */
    data class RunBench(val provider: String, val only: Set<Int>?, val corpus: String?) : Command

    /** `--es guided local|elevenlabs`: starts the guided reading of the corpus with this provider. */
    data class StartGuided(val provider: String) : Command

    /** `--es guided stop`: leaves the guided reading. */
    data object StopGuided : Command

    /** `--es speak "texte"`: reads a text aloud with the current provider. */
    data class Speak(val text: String) : Command

    companion object {
        /** The activity alias that only the shell may start (see the manifest). */
        const val ADB_ENTRY = "app.regie.voicelab.Adb"

        private val folderName = Regex("[A-Za-z0-9_-]+")

        /** Commands of an intent sent to [component]: none unless it is [ADB_ENTRY]. */
        fun parse(component: String?, string: (String) -> String?, boolean: (String) -> Boolean?): List<Command> =
            if (component == ADB_ENTRY) parse(string, boolean) else emptyList()

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
                val corpus = string("corpus")?.trim()?.takeIf { folderName.matches(it) }
                add(RunBench(provider.trim(), only, corpus))
            }
            string("guided")?.trim()?.let { add(if (it == "stop") StopGuided else StartGuided(it)) }
            string("speak")?.takeIf { it.isNotBlank() }?.let { add(Speak(it)) }
        }
    }
}
