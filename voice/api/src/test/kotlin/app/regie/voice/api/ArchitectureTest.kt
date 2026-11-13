package app.regie.voice.api

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hexagonal rules of the voice build, checked on the sources of every module:
 * - `:api` is pure Kotlin: coroutines and the JDK, no Android, no network library;
 * - an adapter only knows `:api`, never another adapter.
 *
 * `demo` is the composition root of the trial app: it may import every adapter.
 */
class ArchitectureTest {
    private val voiceRoot = File("..").canonicalFile
    private val compositionRoots = setOf("demo")

    private val modules: List<File> = voiceRoot.listFiles { file -> File(file, "src/main/kotlin").isDirectory }!!
        .sortedBy { it.name }

    private fun imports(module: File): List<Pair<String, String>> =
        File(module, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() }
        }.toList()

    @Test
    fun `the modules are found`() {
        assertTrue(modules.map { it.name }.containsAll(listOf("api", "fake", "elevenlabs")), "modules: $modules")
    }

    @Test
    fun `api depends on nothing but Kotlin, coroutines and the JDK`() {
        val allowed = listOf("kotlin.", "kotlinx.coroutines.", "java.", "app.regie.voice.api.")

        val offending = imports(File(voiceRoot, "api")).filterNot { (_, import) -> allowed.any(import::startsWith) }

        assertEquals(emptyList(), offending)
    }

    @Test
    fun `an adapter does not import another adapter`() {
        val adapters = modules.map { it.name } - "api" - compositionRoots

        val offending = adapters.flatMap { adapter ->
            val foreign = (adapters - adapter).map { "app.regie.voice.$it." }
            imports(File(voiceRoot, adapter))
                .filter { (_, import) -> foreign.any(import::startsWith) }
                .map { (file, import) -> "$adapter/$file: $import" }
        }

        assertEquals(emptyList(), offending)
    }

    @Test
    fun `an adapter build only depends on the api project`() {
        val adapters = modules.map { it.name } - "api" - compositionRoots
        val projectDependency = Regex("""project\("(:[^"]+)"\)""")

        val offending = adapters.flatMap { adapter ->
            val build = File(voiceRoot, "$adapter/build.gradle.kts").readText()
            projectDependency.findAll(build).map { it.groupValues[1] }.filter { it != ":api" }.map { "$adapter -> $it" }.toList()
        }

        assertEquals(emptyList(), offending)
    }
}
