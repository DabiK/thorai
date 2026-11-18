package app.regie.gateway.bridge

import app.regie.domain.model.Agent
import app.regie.domain.model.AgentLogin
import app.regie.domain.model.AgentAuthentication
import app.regie.domain.model.AuthenticationPhase
import kotlinx.serialization.json.JsonObject
import java.util.Locale

internal fun decodeAuthentication(value: JsonObject?): AgentAuthentication? {
    val source = value ?: return null
    val agent = source.string("agent")?.let { Agent.fromId(it, source.string("name")) } ?: return null
    val phase = AuthenticationPhase.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == source.string("phase") } ?: AuthenticationPhase.UNKNOWN
    val connected = source.boolean("connected")
    val url = source.string("url")?.takeIf { it == "https://auth.openai.com/codex/device" && phase == AuthenticationPhase.WAITING }
    val code = source.string("code")?.takeIf { url != null && it.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4,5}")) }
    return AgentAuthentication(agent, connected, source.string("account"), phase, url?.takeIf { code != null }, code, source.string("message"), source.obj("login")?.let { AgentLogin(it.string("method") ?: "manual", it.string("label") ?: "Connecter", it.string("help").orEmpty(), it.string("provider")) } ?: AgentLogin())
}
