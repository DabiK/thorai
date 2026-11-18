package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import kotlin.time.Instant

private val T0: Instant = Instant.parse("2026-11-18T09:00:00Z")

fun session(id: String, project: String, agent: Agent = Agent.CLAUDE, status: SessionStatus = SessionStatus.IDLE) =
    Session(
        id = SessionId(id),
        agent = agent,
        project = project,
        branch = null,
        title = id,
        status = status,
        active = true,
        updatedAt = T0,
        lastActivityAt = T0,
    )

fun overview(vararg sessions: Session) = Overview.of(sessions.toList(), agents = emptyList())

fun overview(sessions: List<Session>) = Overview.of(sessions, agents = emptyList())

/** Le hameau en texte, une ligne par rangée : pour comparer deux hameaux et lire un échec. */
fun Village.ascii(): String {
    val map = world.map
    val doors = houses.associateBy { it.door }
    return (0 until map.height).joinToString("\n") { y ->
        (0 until map.width).joinToString("") { x ->
            val p = TilePos(x, y)
            when {
                p == spawn -> "@"
                p in doors -> "D"
                houses.any { p in it.footprint } -> "H"
                decorations.any { p in it.footprint && it.blocking } -> "T"
                decorations.any { p in it.footprint } -> "*"
                else -> when (map.ground(p)) {
                    Ground.GRASS -> "."
                    Ground.PATH -> "="
                    Ground.PLAZA -> "#"
                    Ground.WATER -> "~"
                    Ground.BRIDGE -> "b"
                }
            }
        }
    }
}

/** Toute la carte (sol et collisions) et les objets, en texte. */
fun Village.fingerprint(): String {
    val map = world.map
    val tiles = (0 until map.height).joinToString("\n") { y ->
        (0 until map.width).joinToString("") { x ->
            val p = TilePos(x, y)
            "${map.ground(p).ordinal}${if (map.isBlocked(p)) "x" else "o"}"
        }
    }
    return "${map.width}x${map.height}\n$tiles\n${world.objects}"
}

/** Une position en coordonnées relatives à la place : stable quand la carte s'élargit. */
fun Village.relative(p: TilePos) = TilePos(p.x - origin.x, p.y - origin.y)

/** [this] est tout entier dans [other]. */
fun TileRect.within(other: TileRect) =
    left >= other.left && right <= other.right && top >= other.top && bottom <= other.bottom
