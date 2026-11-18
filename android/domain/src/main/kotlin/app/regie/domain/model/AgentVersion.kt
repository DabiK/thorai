package app.regie.domain.model

/**
 * Version installée d'un agent, relevée par le pont. [version] est absente si le pont ne l'a pas lue ;
 * [isTested] est faux quand l'agent ou son adaptateur n'est pas la version testée (ADR 02).
 */
data class AgentVersion(val agent: Agent, val version: String?, val isTested: Boolean, val available: Boolean = true, val connected: Boolean? = null, val running: Boolean = false, val configOptions: List<SessionOption> = emptyList())
