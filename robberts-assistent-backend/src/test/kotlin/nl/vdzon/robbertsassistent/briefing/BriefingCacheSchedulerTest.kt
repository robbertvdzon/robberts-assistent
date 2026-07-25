package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.automower.StubAutomowerClient
import nl.vdzon.robbertsassistent.openshift.StubOpenShiftClient
import nl.vdzon.robbertsassistent.softwarefactory.StubSoftwareFactoryClient
import nl.vdzon.robbertsassistent.zonneplan.StubZonneplanClient
import org.springframework.ai.chat.client.ChatClient
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

    private fun systemStatusProvider() = SystemStatusSectionProvider(
        zonneplanClient = StubZonneplanClient(),
        openShiftClient = StubOpenShiftClient(),
        automowerClient = StubAutomowerClient(),
        softwareFactoryClient = StubSoftwareFactoryClient(),
        chatClient = ChatClient.builder(MockChatModel()).build(),
    )

    @Test
    fun `refreshCaches bouwt zowel de Upcoming- als de Health check-briefing op en schrijft ze naar hun eigen cache`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        val healthCache = InMemoryBriefingCacheRepository()
        val service = BriefingService(listOf(FixedProvider(0, "a"), systemStatusProvider()), upcomingCache, healthCache)
        val scheduler = BriefingCacheScheduler(service)

        scheduler.refreshCaches()

        val cachedUpcoming = upcomingCache.current()
        assertNotNull(cachedUpcoming)
        assertEquals(listOf("a"), cachedUpcoming.sections.map { it.key })

        val cachedHealth = healthCache.current()
        assertNotNull(cachedHealth)
        assertEquals(listOf("system-status"), cachedHealth.sections.map { it.key })
    }

    @Test
    fun `refreshCaches crasht niet als een sectie faalt en ververst de andere cache toch`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        val healthCache = InMemoryBriefingCacheRepository()
        val service = BriefingService(listOf(ThrowingProvider()), upcomingCache, healthCache)
        val scheduler = BriefingCacheScheduler(service)

        scheduler.refreshCaches() // mag niet crashen

        val cachedUpcoming = upcomingCache.current()
        assertNotNull(cachedUpcoming)
        assertEquals("fout", cachedUpcoming.sections.first().key)
    }
}
