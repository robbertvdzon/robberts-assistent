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
    fun `parser kijkt door opmaak en het voorvoegsel van de regel heen`() {
        val a = WatchAssessmentParser.parse("regel 1: NIET GEVONDEN\nregel 2: Het product is nog uitverkocht.")
        assertEquals(WatchAssessment(false, "Het product is nog uitverkocht."), a)
        assertEquals(WatchAssessment(false, "Nog uitverkocht."), WatchAssessmentParser.parse("**Niet gevonden.**\n\nNog uitverkocht."))
        assertEquals(WatchAssessment(true, "Weer op voorraad."), WatchAssessmentParser.parse("GEVONDEN: Weer op voorraad."))
        assertEquals(WatchAssessment(false, "Nog uitverkocht."), WatchAssessmentParser.parse("NIET GEVONDEN - Nog uitverkocht."))
    }

    @Test
    fun `derde regel is context, en geen betekent dat er niets opviel`() {
        val withNote = WatchAssessmentParser.parse("NIET GEVONDEN\nNog uitverkocht.\nregel 3: Het product lijkt uit het assortiment.")
        assertEquals("Het product lijkt uit het assortiment.", withNote.note)
        assertEquals("", WatchAssessmentParser.parse("NIET GEVONDEN\nNog uitverkocht.\nGeen.").note)
        assertEquals("", WatchAssessmentParser.parse("NIET GEVONDEN\nNog uitverkocht.").note)
    }

    @Test
    fun `parser weigert ontbrekende omschrijving en onbekende status`() {
        assertFailsWith<IllegalArgumentException> { WatchAssessmentParser.parse("GEVONDEN") }
        assertFailsWith<IllegalStateException> { WatchAssessmentParser.parse("MISSCHIEN\nOnduidelijk.") }
    }
}
