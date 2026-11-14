package app.regie

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Règles de l'architecture hexagonale (CLAUDE.md), vérifiées sur les imports des sources.
 * Les tests unitaires d'Android tournent depuis le dossier du module `app`.
 */
class ArchitectureTest {

    private val appSources = File("src/main/kotlin")
    private val domainSources = File("../domain/src/main/kotlin")
    private val gatewaySources = File("../gateway-bridge/src/main/kotlin")

    /** Les adapters : la passerelle vers le pont (module) et les accès à la console (Android). */
    private fun isAdapter(name: String) =
        name.startsWith("app.regie.commandmodel.") || name.startsWith("app.regie.gateway.") || name.startsWith("app.regie.console.") || name.startsWith("app.regie.voice.")

    private fun File.kotlinFiles() = walkTopDown().filter { it.extension == "kt" }.toList()

    private fun File.imports() = readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

    private fun violations(files: List<File>, forbidden: (String) -> Boolean) =
        files.flatMap { file -> file.imports().filter(forbidden).map { "${file.name} importe $it" } }

    @Test
    fun `command model is an isolated adapter and only composed in the root`() {
        val model = File("../command-model/src/main/kotlin").kotlinFiles()
        assertTrue(model.isNotEmpty())
        assertEquals(emptyList(), violations(model) {
            it.startsWith("android.") || it.startsWith("androidx.") ||
                (it.startsWith("app.regie.") && !it.startsWith("app.regie.domain.") && !it.startsWith("app.regie.commandmodel."))
        })
        assertEquals(listOf("RegieApplication.kt"), appSources.kotlinFiles().filter { file ->
            file.imports().any { it.startsWith("app.regie.commandmodel.") }
        }.map { it.name })
    }

    @Test
    fun `the sources are found`() {
        assertTrue(appSources.kotlinFiles().isNotEmpty(), "sources de l'app introuvables depuis ${File("").absolutePath}")
        assertTrue(domainSources.kotlinFiles().isNotEmpty(), "sources du domaine introuvables")
        assertTrue(gatewaySources.kotlinFiles().isNotEmpty(), "sources de la passerelle introuvables")
    }

    @Test
    fun `the domain depends neither on Android nor on the app`() {
        val found = violations(domainSources.kotlinFiles()) {
            it.startsWith("android.") || it.startsWith("androidx.") || it.startsWith("okhttp3.") ||
                it.startsWith("kotlinx.serialization.") || it.startsWith("com.github.difflib.") ||
                (it.startsWith("app.regie.") && !it.startsWith("app.regie.domain."))
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `the gateway adapter depends on the domain only, never on Android nor the app`() {
        val found = violations(gatewaySources.kotlinFiles()) {
            it.startsWith("android.") || it.startsWith("androidx.") ||
                (it.startsWith("app.regie.") && !it.startsWith("app.regie.domain.") && !it.startsWith("app.regie.gateway.bridge."))
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `the interface only talks to the domain, never to adapters, ports or the composition root`() {
        val ui = File(appSources, "app/regie/ui").kotlinFiles()
        val found = violations(ui) {
            isAdapter(it) || it.startsWith("app.regie.domain.port.") ||
                it.startsWith("app.regie.display.DisplayMonitor") ||
                it == "app.regie.AppContainer" || it == "app.regie.RegieApplication"
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `an adapter does not import another adapter`() {
        val console = File(appSources, "app/regie/console").kotlinFiles()
        assertTrue(console.isNotEmpty())
        val found = violations(console) { it.startsWith("app.regie.gateway.") } +
            violations(gatewaySources.kotlinFiles()) { it.startsWith("app.regie.console.") }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `voice adapter only depends on voice ports never on providers or gateway`() {
        val voice = File(appSources, "app/regie/voice").kotlinFiles()
        assertTrue(voice.isNotEmpty())
        val found = violations(voice) {
            it.startsWith("app.regie.gateway.") || it.startsWith("app.regie.voice.local.") ||
                it.startsWith("app.regie.voice.elevenlabs.") || it.startsWith("app.regie.voice.fake.")
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `the composition root is the only place that picks the gateway`() {
        val users = appSources.kotlinFiles()
            .filter { file -> file.imports().any { it.startsWith("app.regie.gateway.") } }
            .map { it.name }
        assertEquals(listOf("RegieApplication.kt"), users)
    }
}
