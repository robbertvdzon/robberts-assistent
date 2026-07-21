package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BriefingCacheSchedulerTest {

    private class FixedProvider(override val order: Int, private val key: String) : BriefingSectionProvider {
        override fun section() = BriefingSection(key = key, title = key, text = key)
    }

    private class ThrowingProvider : BriefingSectionProvider {
        override val order = 0
        override fun section(): BriefingSection = error("boom")
    }

    @Test
    fun `refreshCache bouwt de briefing op en schrijft 'm naar de cache`() {
        val cache = InMemoryBriefingCacheRepository()
        val service = BriefingService(listOf(FixedProvider(0, "a")), cache)
        val scheduler = BriefingCacheScheduler(service)

        scheduler.refreshCache()

        val cached = cache.current()
        assertNotNull(cached)
        assertEquals(listOf("a"), cached.sections.map { it.key })
    }

    @Test
    fun `refreshCache crasht niet als een sectie faalt`() {
        val cache = InMemoryBriefingCacheRepository()
        val service = BriefingService(listOf(ThrowingProvider()), cache)
        val scheduler = BriefingCacheScheduler(service)

        scheduler.refreshCache() // mag niet crashen

        assertNotNull(cache.current())
    }
}
