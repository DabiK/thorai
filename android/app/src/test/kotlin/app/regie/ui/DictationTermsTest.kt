package app.regie.ui

import app.regie.ui.screens.recognizedTerms
import kotlin.test.Test
import kotlin.test.assertEquals

class DictationTermsTest {
    @Test fun `only complete repository names are highlighted including punctuation and casing`() {
        val matches = recognizedTerms("Maintenant, MAIN puis src/PassageCheck.ts, pas mainframe.", listOf("main", "PassageCheck.ts"))
        assertEquals(listOf("MAIN", "PassageCheck.ts"), matches.map { it.value })
    }
}
