package app.regie.gateway.bridge

import kotlinx.serialization.json.JsonObject

/** Les enregistrements du vrai pont (`bridge/fixtures/bridge-ws/`, ticket 02), embarqués dans le module. */
internal object Fixtures {
    const val DEMO_RUN = "demo-run.jsonl"
    const val CLAUDE_SESSION = "claude-session.jsonl"
    const val CODEX_SESSION = "codex-session.jsonl"

    fun lines(name: String): List<JsonObject> {
        val stream = Fixtures::class.java.getResourceAsStream("/bridge-ws/$name")
            ?: error("enregistrement $name introuvable dans les ressources")
        return stream.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.mapNotNull(Frames::parseObject).toList() }
    }

    /** `demo-run.jsonl` : toute la démo, dans les deux sens, à l'heure près (`t`, en millisecondes). */
    fun demoRun(): List<RecordedFrame> = lines(DEMO_RUN).mapNotNull { line ->
        RecordedFrame(
            atMillis = line.long("t") ?: return@mapNotNull null,
            fromBridge = line.string("dir") == "bridge->client",
            frame = line.obj("frame") ?: return@mapNotNull null,
        )
    }

    /** `claude-session.jsonl`, `codex-session.jsonl` : les événements d'une session, enveloppes nues. */
    fun events(name: String): List<BridgeEvent> = lines(name).mapNotNull(Frames::parseEvent)
}

internal data class RecordedFrame(val atMillis: Long, val fromBridge: Boolean, val frame: JsonObject)
