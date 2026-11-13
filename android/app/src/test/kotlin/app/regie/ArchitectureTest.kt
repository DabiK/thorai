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

    private fun File.kotlinFiles() = walkTopDown().filter { it.extension == "kt" }.toList()

    private fun File.imports() = readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

    private fun violations(files: List<File>, forbidden: (String) -> Boolean) =
        files.flatMap { file -> file.imports().filter(forbidden).map { "${file.name} importe $it" } }

    @Test
    fun `the sources are found`() {
        assertTrue(appSources.kotlinFiles().isNotEmpty(), "sources de l'app introuvables depuis ${File("").absolutePath}")
        assertTrue(domainSources.kotlinFiles().isNotEmpty(), "sources du domaine introuvables")
    }

    @Test
    fun `the domain depends neither on Android nor on the app`() {
        val found = violations(domainSources.kotlinFiles()) {
            it.startsWith("android.") || it.startsWith("androidx.") ||
                (it.startsWith("app.regie.") && !it.startsWith("app.regie.domain."))
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `the interface only talks to the domain, never to adapters, ports or the composition root`() {
        val ui = File(appSources, "app/regie/ui").kotlinFiles()
        val found = violations(ui) {
            it.startsWith("app.regie.domain.fake.") || it.startsWith("app.regie.domain.port.") ||
                it.startsWith("app.regie.display.DisplayMonitor") ||
                it == "app.regie.AppContainer" || it == "app.regie.RegieApplication"
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `the composition root is the only place that picks the gateway`() {
        val users = appSources.kotlinFiles()
            .filter { file -> file.imports().any { it.startsWith("app.regie.domain.fake.") } }
            .map { it.name }
        assertEquals(listOf("RegieApplication.kt"), users)
    }
}
