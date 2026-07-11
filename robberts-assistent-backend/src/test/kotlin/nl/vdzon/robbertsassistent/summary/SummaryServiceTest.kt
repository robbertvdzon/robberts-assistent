package nl.vdzon.robbertsassistent.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryServiceTest {
    @Test
    fun `returns the five expected daily summary items`() {
        val items = SummaryService().current().items

        assertEquals(
            listOf("wind", "moestuin", "backups", "openshift", "zonnepanelen"),
            items.map { it.key },
        )
        items.forEach { assertTrue(it.title.isNotBlank() && it.text.isNotBlank()) }
    }
}
