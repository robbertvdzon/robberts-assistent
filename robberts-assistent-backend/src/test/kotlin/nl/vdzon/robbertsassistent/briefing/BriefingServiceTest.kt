package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class BriefingServiceTest {

    private class FixedProvider(override val order: Int, private val key: String) : BriefingSectionProvider {
        override fun section() = BriefingSection(key = key, title = key, text = key)
    }

    private class ThrowingProvider(override val order: Int) : BriefingSectionProvider {
        override fun section(): BriefingSection = error("boom")
    }

    private fun service(
        providers: List<BriefingSectionProvider>,
        cache: BriefingCacheRepository = InMemoryBriefingCacheRepository(),
    ) = BriefingService(providers, cache)

    @Test
    fun `current bouwt live op en sorteert secties op order als er nog geen cache is`() {
        val svc = service(listOf(FixedProvider(2, "b"), FixedProvider(0, "a"), FixedProvider(1, "c")))

        val response = svc.current()

        assertEquals(listOf("a", "c", "b"), response.sections.map { it.key })
        assertNotNull(response.updatedAt)
    }

    @Test
    fun `current vangt een crashende sectie op in plaats van te crashen`() {
        val svc = service(listOf(FixedProvider(0, "a"), ThrowingProvider(1)))

        val sections = svc.current().sections

        assertEquals(2, sections.size)
        assertEquals("fout", sections[1].key)
    }

    @Test
    fun `current levert de gecachete briefing als die bestaat, zonder opnieuw op te bouwen`() {
        val cache = InMemoryBriefingCacheRepository()
        val cached = BriefingResponse(sections = listOf(BriefingSection(key = "x", title = "x", text = "x")), updatedAt = "vast")
        cache.store(cached)
        val svc = service(listOf(FixedProvider(0, "a")), cache)

        val response = svc.current()

        assertSame(cached, response)
    }

    @Test
    fun `refresh bouwt altijd live op en overschrijft de cache`() {
        val cache = InMemoryBriefingCacheRepository()
        cache.store(BriefingResponse(sections = listOf(BriefingSection(key = "oud", title = "oud", text = "oud")), updatedAt = "vast"))
        val svc = service(listOf(FixedProvider(0, "nieuw")), cache)

        val response = svc.refresh()

        assertEquals(listOf("nieuw"), response.sections.map { it.key })
        assertEquals(response, cache.current())
    }

    @Test
    fun `zonder cache levert current een live opbouw zonder deze te cachen`() {
        val cache = InMemoryBriefingCacheRepository()
        val svc = service(listOf(FixedProvider(0, "a")), cache)

        svc.current()

        assertNull(cache.current())
    }
}
