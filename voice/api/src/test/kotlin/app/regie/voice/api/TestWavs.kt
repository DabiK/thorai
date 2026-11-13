package app.regie.voice.api

/** Recordings of corpus sentences (scripts/voice-corpus.sh), versioned in voice/testdata/wav. */
object TestWavs {
    fun bytes(name: String): ByteArray =
        requireNotNull(TestWavs::class.java.getResourceAsStream("/wav/$name")) { "missing test WAV $name" }
            .use { it.readBytes() }
}
