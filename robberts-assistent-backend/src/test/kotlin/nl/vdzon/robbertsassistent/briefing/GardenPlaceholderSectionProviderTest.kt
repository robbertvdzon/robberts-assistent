package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GardenPlaceholderSectionProviderTest {

    @Test
    fun `section geeft een vaste placeholder-tekst terug`() {
        val section = GardenPlaceholderSectionProvider().section()

        assertEquals("moestuin", section.key)
        assertTrue(section.text.isNotBlank())
    }

    @Test
    fun `shortSummary is null, sectie doet niet mee in de push`() {
        assertNull(GardenPlaceholderSectionProvider().shortSummary())
    }
}
