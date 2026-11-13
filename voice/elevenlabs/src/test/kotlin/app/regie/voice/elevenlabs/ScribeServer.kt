package app.regie.voice.elevenlabs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/** Plays the Scribe server from a fixture (see resources/scribe/README.md). */
class ScribeServer(
    private val onOpen: List<JsonElement> = emptyList(),
    private val onAudio: List<JsonElement> = emptyList(),
    private val onCommit: List<JsonElement> = emptyList(),
    /** Drops the TCP connection after this many audio chunks, like a network cut. */
    private val cutAfterChunks: Int? = null,
) : WebSocketListener() {
    val received = CopyOnWriteArrayList<JsonObject>()
    val clientGone = CountDownLatch(1)
    private var audioChunks = 0

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onOpen.forEach { webSocket.send(it.toString()) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = Json.parseToJsonElement(text).jsonObject
        received += message
        if (message["commit"]!!.jsonPrimitive.boolean) {
            onCommit.forEach { webSocket.send(it.toString()) }
            return
        }
        val index = audioChunks++
        if (cutAfterChunks != null && audioChunks >= cutAfterChunks) {
            webSocket.cancel()
            return
        }
        onAudio.getOrNull(index)?.let { webSocket.send(it.toString()) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        clientGone.countDown()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        clientGone.countDown()
    }

    companion object {
        fun resource(name: String): String =
            requireNotNull(ScribeServer::class.java.getResourceAsStream("/scribe/$name")) { "missing fixture $name" }
                .use { it.readBytes().decodeToString() }

        fun fromFixture(name: String): ScribeServer {
            val fixture = Json.parseToJsonElement(resource(name)).jsonObject
            fun list(key: String) = (fixture[key] as? JsonArray).orEmpty()
            return ScribeServer(list("onOpen"), list("onAudio"), list("onCommit"))
        }

        fun errors(): List<JsonObject> = Json.parseToJsonElement(resource("erreurs.json")) .let { it as JsonArray }.map { it.jsonObject }
    }
}
