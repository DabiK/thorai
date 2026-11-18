package app.regie.game.ui.chantier

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import app.regie.domain.model.Agent
import app.regie.domain.model.BranchChoice
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.NewSessionField
import app.regie.domain.model.NewSessionForm
import app.regie.domain.model.Overview
import app.regie.domain.model.ProjectChoice
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionOption
import app.regie.domain.model.OptionValue
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.village.Construction
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.village.map
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.render.GroundCache
import app.regie.game.ui.render.GroundKey
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.village.VillageTopScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Bâtir et agrandir (ticket 48) : la fiche, le chantier et la carte, avec les vrais atlas, aux tailles de la Thor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuildScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val frozen = mutableLongStateOf(0L)
    private val assets: GameAssets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)

    private val keys = mutableListOf<BuildKey>()
    private val holds = mutableListOf<Boolean>()
    private val fields = mutableListOf<NewSessionField>()
    private val texts = mutableListOf<String>()
    private val callbacks = object : BuildCallbacks {
        override fun onKey(key: BuildKey) { keys += key }
        override fun onHold(held: Boolean) { holds += held }
        override fun onField(field: NewSessionField) { fields += field }
        override fun onInstruction(text: String) { texts += "consigne:$text" }
        override fun onBranch(text: String) { texts += "branche:$text" }
        override fun onProjectName(text: String) { texts += "projet:$text" }
    }

    private val model = SessionOption("model", "Modèle", "model", "opus", listOf(OptionValue("opus", "Opus 5.5")))
    private val thought = SessionOption("effort", "Réflexion", "thought_level", "medium", listOf(OptionValue("medium", "Moyenne")))

    /** Une fiche pré-remplie par l'ordre « lance Claude sur api-meteo », comme la maquette 10. */
    private val form = NewSessionForm(
        visible = true, heard = "Lance Claude sur météo, ajoute un cache de dix minutes", agent = Agent.CLAUDE,
        agents = listOf(Agent.CLAUDE, Agent.CODEX), project = "api-meteo",
        projects = listOf(ProjectChoice("api-meteo", "main", listOf("main", "cache")), ProjectChoice("regie", "main", listOf("main"))),
        approvalMode = "ask", instruction = "Ajoute un cache de dix minutes sur les prévisions.", configOptions = listOf(model, thought),
    )

    private val sessions = DemoScene.sessions(6)
    private val overview = Overview.of(sessions, emptyList(), BridgeStatus.Connected)

    private fun generator() = VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog()!!)

    private lateinit var activity: ComponentActivity

    private fun show(village: Village? = null, content: @Composable () -> Unit) {
        village?.let { GroundCache.prepare(GroundKey.of(it.world.map), assets.palette) }
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { GameTheme(clock = frozen) { content() } }
        compose.waitForIdle()
    }

    private fun selected(on: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, on)

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `the form has the classic fields, pre-filled by R2, and A B L2 and the cross`() {
        show { NewSessionBottomScreen(form, callbacks) }

        compose.onNodeWithText("Remplie depuis ton ordre vocal").assertIsDisplayed()
        // Les champs de NewSessionScreen, avec les valeurs du domaine.
        listOf("Agent", "Projet", "Branche", "Mode", "Modèle", "Réflexion", "Consigne").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Claude").assertIsDisplayed()
        compose.onNodeWithText("api-meteo").assertIsDisplayed()
        compose.onNodeWithText("~/projects/api-meteo").assertIsDisplayed()
        compose.onNodeWithText("Courante · main").assertIsDisplayed()
        compose.onNodeWithText("Je valide chaque action", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Opus 5.5").assertIsDisplayed()
        compose.onNodeWithText("Moyenne").assertIsDisplayed()
        compose.onNodeWithText("Ajoute un cache de dix minutes sur les prévisions.").assertIsDisplayed()
        compose.onNodeWithText("Nouveau projet : créer un dépôt vide").assertIsDisplayed()
        compose.onNodeWithTag("batir-champ-AGENT").assert(selected(true))
        save(snapshot(), "48-fiche-bas.png")

        compose.onNodeWithText("Bâtir").performClick()
        compose.onNodeWithText("Annuler").performClick()
        compose.onNodeWithTag("batir-champ-PROJECT").performClick()
        compose.onNodeWithText("Dicter la consigne").performTouchInput { down(center); up() }
        assertEquals(listOf(BuildKey.A, BuildKey.B), keys)
        assertEquals(listOf(NewSessionField.PROJECT), fields)
        assertEquals(listOf(true, false), holds)
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `an incomplete form says what is missing and does not build, new branch and project names are editable`() {
        val state = mutableStateOf(form.copy(agent = null))
        show { NewSessionBottomScreen(state.value, callbacks) }
        compose.onNodeWithText("Choisis un agent.").assertIsDisplayed()
        compose.onNodeWithTag("batir-lancer").assert(selected(false))
        compose.onNodeWithText("Bâtir").performClick()
        assertEquals(emptyList(), keys, "A n'est pas proposé")

        compose.runOnIdle { state.value = form.copy(field = NewSessionField.BRANCH, branchChoice = BranchChoice.NEW, base = "main") }
        compose.onNodeWithTag("branch-name").performTextReplacement("cache-previsions")
        compose.runOnIdle { state.value = form.copy(newProject = true, project = "meteo", projectName = "meteo", field = NewSessionField.PROJECT, projects = listOf(ProjectChoice("meteo"))) }
        compose.onNodeWithText("Ce dossier existe déjà. Choisis un autre nom.").assertIsDisplayed()
        compose.onNodeWithText("Dicter le nom du projet").assertIsDisplayed()
        compose.onNodeWithTag("project-name").performTextReplacement("meteo du jour")
        // Un champ qui disparaît peut renvoyer un texte vide : seules les saisies comptent.
        assertEquals(listOf("branche:cache-previsions", "projet:meteo du jour"), texts.filterNot { it.endsWith(":") })
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `the construction shows its four stages on the village, the house built on the reserved plot`() {
        val generator = generator()
        generator.generate(overview)
        val withNew = Overview.of(sessions + DemoScene.sessions(1).single().copy(id = SessionId("n"), project = sessions[0].project, status = SessionStatus.WORKING), emptyList(), BridgeStatus.Connected)
        generator.reserve(overview, sessions[0].project)
        generator.bind(SessionId("n"))
        val construction = Construction(sessions[0].project).launched(prompted = true).created(SessionId("n")).copy(stage = ConstructionStage.FOUNDATIONS)
        val village = generator.generate(withNew, construction)
        val site = assertNotNull(village.site)
        val scene = VillageSceneState(village, start = site.entry)
        show(village) { VillageTopScreen(scene, assets, onEnterHouse = {}, layer = constructionLayer(construction, panel = true)) }

        compose.onNodeWithText("Bâtir une maison").assertIsDisplayed()
        compose.onNodeWithText("Rue ${sessions[0].project}").assertIsDisplayed()
        listOf("Fiche", "Session créée", "Agent lancé", "Première réponse").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("chantier-etape-FOUNDATIONS").assert(selected(true))
        compose.onNodeWithTag("chantier-panneau").assertIsDisplayed()
        save(snapshot(), "48-chantier-fondations.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `the finished house shines, a session without instruction ends on Attend ta consigne`() {
        val generator = generator()
        generator.generate(overview)
        val withNew = Overview.of(sessions + DemoScene.sessions(1).single().copy(id = SessionId("n"), project = sessions[0].project, status = SessionStatus.IDLE), emptyList(), BridgeStatus.Connected)
        generator.reserve(overview, sessions[0].project)
        generator.bind(SessionId("n"))
        val construction = Construction(sessions[0].project).launched(prompted = false).created(SessionId("n")).copy(stage = ConstructionStage.FINISHED)
        val village = generator.generate(withNew, construction)
        val site = assertNotNull(village.site)
        show(village) { VillageTopScreen(VillageSceneState(village, start = site.entry), assets, onEnterHouse = {}, layer = constructionLayer(construction, panel = true)) }

        compose.onNodeWithText("Attend ta consigne").assertIsDisplayed()
        compose.onNodeWithTag("chantier-etape-FINISHED").assert(selected(true))
        assertNotNull(village.houseOf(SessionId("n")))
        save(snapshot(), "48-maison-finie.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `a refused creation stops at the plot, the error is shown as given, the plot is free again`() {
        val generator = generator()
        generator.generate(overview)
        generator.reserve(overview, sessions[0].project)
        generator.release()
        val construction = Construction(sessions[0].project).launched(prompted = true).failedWith("Projet introuvable dans ~/projects : api-meteo.")
        val village = generator.generate(overview, construction)
        assertNull(village.site, "le terrain est libéré")
        val free = village.freePlots.first { village.streets[it.street].project == sessions[0].project }
        show(village) { VillageTopScreen(VillageSceneState(village, start = free.entry), assets, onEnterHouse = {}, layer = constructionLayer(construction, panel = true)) }

        compose.onNodeWithTag("chantier-echec").assertIsDisplayed()
        compose.onNodeWithText("Projet introuvable dans ~/projects : api-meteo.").assertIsDisplayed()
        compose.onNodeWithTag("chantier-etape-PLOT").assert(selected(true))
        save(snapshot(), "48-echec.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `a new project opens a new street, announced by a banner`() {
        val generator = generator()
        generator.generate(overview)
        generator.reserve(overview, "meteo-du-jour")
        val construction = Construction("meteo-du-jour", newProject = true).launched(prompted = true)
        val village = generator.generate(overview, construction)
        val site = assertNotNull(village.site)
        assertEquals("meteo-du-jour", village.streets[site.street].project)
        show(village) { VillageTopScreen(VillageSceneState(village, start = site.entry), assets, onEnterHouse = {}, layer = constructionLayer(construction, panel = true)) }

        compose.onNodeWithTag("chantier-nouvelle-rue").assertIsDisplayed()
        compose.onNode(hasText("Nouvelle rue : meteo-du-jour") and hasAnyAncestor(hasTestTag("chantier-nouvelle-rue"))).assertIsDisplayed()
        save(snapshot(), "48-nouvelle-rue.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `a free plot looks like a plot, fenced and staked`() {
        val village = generator().generate(overview, Construction(sessions[0].project))
        val target = assertNotNull(village.site)
        show(village) { VillageTopScreen(VillageSceneState(village, start = target.entry), assets, onEnterHouse = {}, layer = constructionLayer(Construction(sessions[0].project), panel = true)) }
        compose.onNodeWithText("Terrain choisi").assertIsDisplayed()
        save(snapshot(), "48-terrain-libre.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `the map lists streets and houses with their states, and is tactile`() {
        val generator = generator()
        generator.generate(overview)
        generator.reserve(overview, sessions[1].project)
        val village = generator.generate(overview, Construction(sessions[1].project).launched(prompted = true))
        val goTo = mutableListOf<Int>()
        var closed = 0
        show {
            VillageMapScreen(village.map(), selected = 0, assets, object : MapCallbacks {
                override fun onGoTo(street: Int) { goTo += street }
                override fun onMove(delta: Int) {}
                override fun onClose() { closed++ }
            })
        }
        compose.onNodeWithText("Carte du hameau").assertIsDisplayed()
        village.streets.forEach { compose.onNodeWithText(it.project).assertIsDisplayed() }
        compose.onNodeWithText("${village.streets.size} rues · ${village.houses.size} maisons").assertIsDisplayed()
        save(snapshot(), "48-carte.png")
        compose.onNodeWithTag("carte-chantier", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("carte-rue-0").assert(selected(true))

        compose.onNodeWithTag("carte-rue-1").performClick()
        compose.onNodeWithText("Fermer").performClick()
        assertEquals(listOf(1), goTo)
        assertEquals(1, closed)
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `on a single screen, the form takes the whole screen`() {
        show { NewSessionBottomScreen(form.copy(field = NewSessionField.INSTRUCTION, heard = ""), callbacks, compact = true) }
        compose.onNodeWithTag("instruction").performTextReplacement("Ajoute un cache.")
        compose.onNodeWithText("Bâtir").assertIsDisplayed()
        assertEquals(listOf("consigne:Ajoute un cache."), texts)
        save(snapshot(), "48-un-ecran.png")
    }

    private fun snapshot(): Bitmap {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Les rendus restent dans `build/` ; ceux de `docs/images/jeu/48-*.png` en sont des copies. */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/game-demo").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
