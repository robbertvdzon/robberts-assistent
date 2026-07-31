package nl.vdzon.robbertsassistent.watches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchEvaluatorTest {
    @Test
    fun `parser herkent gevonden en niet gevonden defensief`() {
        val found = WatchAssessmentParser.parse(" GEVONDEN \nEr zijn twee kaarten beschikbaar.")
        val notFound = WatchAssessmentParser.parse("NIET GEVONDEN\nNog geen kaarten beschikbaar.\nextra")

        assertTrue(found.found)
        assertEquals("Er zijn twee kaarten beschikbaar.", found.description)
        assertFalse(notFound.found)
    }

    @Test
    fun `parser weigert ontbrekende omschrijving en onbekende status`() {
        assertFailsWith<IllegalArgumentException> { WatchAssessmentParser.parse("GEVONDEN") }
        assertFailsWith<IllegalStateException> { WatchAssessmentParser.parse("MISSCHIEN\nOnduidelijk.") }
    }
}
