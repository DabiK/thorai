package app.regie.game.ui.menu

import android.graphics.Bitmap
import android.app.Activity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.ComposeTestRule
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthentication
import app.regie.domain.model.AgentLogin
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.ApprovalOption
import app.regie.domain.model.ApprovalOptionKind
import app.regie.domain.model.AuthenticationPhase
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.courier.Courier
import app.regie.game.engine.courier.Letter
import app.regie.game.engine.courier.LetterKind
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import java.io.File
import kotlin.time.Instant

/** Des données d'exemple pour les écrans du ticket 49 : ce sont des données d'agent, jamais réécrites. */
object Samples {
    val OPENCODE = Agent("opencode", "opencode")
    val now: Instant = Instant.fromEpochMilliseconds(10 * 3_600_000L)
    private val t = Instant.fromEpochMilliseconds(0)

    val approval = Approval(
        ApprovalId("p1"), SessionId("meteo"), ApprovalKind.COMMAND, "npm run db:migrate",
        options = listOf(
            ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Oui"),
            ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Toujours"),
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "Non"),
        ),
    )

    val sessions = listOf(
        Session(SessionId("meteo"), OPENCODE, "api-meteo", "main", "migration", SessionStatus.AWAITING_APPROVAL, true, t, t, pendingApproval = approval),
        Session(SessionId("regie"), Agent.CLAUDE, "regie", "main", "tri des sessions", SessionStatus.WORKING, true, t, t),
        Session(SessionId("site"), Agent.CODEX, "site-perso", "main", null, SessionStatus.IDLE, true, t, t),
    )

    val overview = Overview(
        sessions,
        listOf(
            AgentVersion(Agent.CLAUDE, "2.1", true, connected = true),
            AgentVersion(Agent.CODEX, "0.167", true, connected = false),
            AgentVersion(OPENCODE, "1.0", true, connected = true),
        ),
        BridgeStatus.Connected,
    )

    val courier: Courier = listOf(
        Letter("a", LetterKind.FAILED, SessionId("regie"), null, "Session en échec", "regie · La commande npm test a échoué.", Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3 * 3_600_000L), read = true),
        Letter("b", LetterKind.FINISHED, SessionId("site"), null, "Session terminée", "site-perso · La refonte est prête, tous les tests passent.", Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 2 * 3_600_000L), read = true),
        Letter("c", LetterKind.QUESTION, SessionId("regie"), null, "L’agent te pose une question", "regie · Je mets aussi les sessions en pause en bas ?", Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 5 * 60_000L)),
        Letter("d", LetterKind.APPROVAL, SessionId("meteo"), ApprovalId("p1"), "opencode attend ta validation", "api-meteo · npm run db:migrate", Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 60_000L)),
    ).fold(Courier()) { c, l -> c.receive(l) }

    const val CODEX_URL = "https://auth.openai.com/codex/device"

    val authentication = listOf(
        AgentAuthentication(Agent.CLAUDE, true, login = AgentLogin("manual", "Connecter", "Ouvre Termux, puis lance claude auth login.")),
        AgentAuthentication(Agent.CODEX, false, phase = AuthenticationPhase.WAITING, url = CODEX_URL, code = "WXYZ-1234"),
        AgentAuthentication(OPENCODE, false, login = AgentLogin("api_key", "Clé OpenCode Go", "Colle ta clé OpenCode Go.", provider = "opencode-go")),
    )

    /** Les vrais atlas du jeu, lus depuis `src/main/assets`. */
    fun realAssets(): GameAssets = GameAssetLoader.load({ path -> File("src/main/assets", path).inputStream() })
}

/**
 * Les captures des écrans du ticket 49, rangées dans `docs/images/jeu/` quand `-PgameCaptures`
 * donne ce dossier (`./gradlew :game-ui:testDebugUnitTest --rerun -PgameCaptures=$PWD/../docs/images/jeu`).
 * Sans la propriété, rien n'est écrit : la suite de tests ne touche pas au dépôt.
 */
object Captures {
    private val directory: File? = System.getProperty("game.captures")?.let(::File)

    /** Dessine la fenêtre de l'activité, telle quelle, au pixel de l'écran de la Thor. */
    fun save(compose: ComposeTestRule, activity: Activity, name: String) {
        val dir = directory ?: return
        compose.waitForIdle()
        dir.mkdirs()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "49-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
