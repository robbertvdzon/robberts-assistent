package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertEquals

class BriefingServiceTest {

    private class FixedProvider(override val order: Int, private val key: String) : BriefingSectionProvider {
        override fun section() = BriefingSection(key = key, title = key, text = key)
    }

    private class ThrowingProvider(override val order: Int) : BriefingSectionProvider {
        override fun section(): BriefingSection = error("boom")
    }

    @Test
    fun `current sorteert secties op order`() {
        val service = BriefingService(listOf(FixedProvider(2, "b"), FixedProvider(0, "a"), FixedProvider(1, "c")))

        val keys = service.current().sections.map { it.key }

        assertEquals(listOf("a", "c", "b"), keys)
    }

    @Test
    fun `current vangt een crashende sectie op in plaats van te crashen`() {
        val service = BriefingService(listOf(FixedProvider(0, "a"), ThrowingProvider(1)))

        val sections = service.current().sections

        assertEquals(2, sections.size)
        assertEquals("fout", sections[1].key)
    }
}
