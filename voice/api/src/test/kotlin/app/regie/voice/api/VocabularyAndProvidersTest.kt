package app.regie.voice.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class VocabularyAndProvidersTest {
    @Test
    fun `vocabulary parses one term per line, without comments nor blanks`() {
        val vocabulary = Vocabulary.parse("# termes\nAgentGateway\n\n  Claude Code  \n#ignoré\nnpm\n")

        assertEquals(listOf("AgentGateway", "Claude Code", "npm"), vocabulary.terms)
    }

    @Test
    fun `the corpus term list parses`() {
        val text = java.io.File("../corpus/termes.txt").readText()

        val terms = Vocabulary.parse(text).terms

        assertEquals("AgentGateway", terms.first())
        assertEquals(terms.size, terms.toSet().size, "no duplicate term")
    }

    private class NamedProvider(name: String) : VoiceProvider {
        override val id = VoiceProviderId(name)
        override val displayName = name
        override val speechToText: SpeechToText get() = error("unused")
        override val textToSpeech: TextToSpeech get() = error("unused")
        override fun prepare(): Flow<ProviderStatus> = flowOf(ProviderStatus.Ready)
    }

    @Test
    fun `providers are picked by the id stored in the settings`() {
        val cloud = NamedProvider("elevenlabs")
        val local = NamedProvider("local")
        val providers = VoiceProviders(listOf(cloud, local))

        assertSame(local, providers[VoiceProviderId("local")])
        assertSame(cloud, providers[VoiceProviderId("elevenlabs")])
        assertNull(providers.getOrNull(VoiceProviderId("autre")))
        assertFailsWith<IllegalArgumentException> { providers[VoiceProviderId("autre")] }
        assertFailsWith<IllegalArgumentException> { VoiceProviders(listOf(cloud, NamedProvider("elevenlabs"))) }
    }
}
