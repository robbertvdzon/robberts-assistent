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
import kotlin.test.assertNull
import kotlin.test.assertSame

class BriefingServiceTest {

    private class FixedProvider(override val order: Int, private val key: String) : BriefingSectionProvider {
        override fun section() = BriefingSection(key = key, title = key, text = key)
    }

    private class ThrowingProvider(override val order: Int) : BriefingSectionProvider {
        override fun section(): BriefingSection = error("boom")
    }

    private fun systemStatusProvider() = SystemStatusSectionProvider(
        zonneplanClient = StubZonneplanClient(),
        openShiftClient = StubOpenShiftClient(),
        automowerClient = StubAutomowerClient(),
        softwareFactoryClient = StubSoftwareFactoryClient(),
        chatClient = ChatClient.builder(MockChatModel()).build(),
    )

    private fun service(
        providers: List<BriefingSectionProvider>,
        upcomingCache: BriefingCacheRepository = InMemoryBriefingCacheRepository(),
        healthCache: BriefingCacheRepository = InMemoryBriefingCacheRepository(),
    ) = BriefingService(providers, upcomingCache, healthCache)

    @Test
    fun `currentUpcoming bouwt live op en sorteert secties op order als er nog geen cache is`() {
        val svc = service(listOf(FixedProvider(2, "b"), FixedProvider(0, "a"), FixedProvider(1, "c")))

        val response = svc.currentUpcoming()

        assertEquals(listOf("a", "c", "b"), response.sections.map { it.key })
        assertNotNull(response.updatedAt)
    }

    @Test
    fun `currentUpcoming laat de systeemstatus-sectie buiten beschouwing`() {
        val svc = service(listOf(FixedProvider(0, "a"), systemStatusProvider()))

        val response = svc.currentUpcoming()

        assertEquals(listOf("a"), response.sections.map { it.key })
    }

    @Test
    fun `currentHealth levert uitsluitend de systeemstatus-sectie`() {
        val svc = service(listOf(FixedProvider(0, "a"), systemStatusProvider()))

        val response = svc.currentHealth()

        assertEquals(listOf("system-status"), response.sections.map { it.key })
    }

    @Test
    fun `currentUpcoming vangt een crashende sectie op in plaats van te crashen`() {
        val svc = service(listOf(FixedProvider(0, "a"), ThrowingProvider(1)))

        val sections = svc.currentUpcoming().sections

        assertEquals(2, sections.size)
        assertEquals("fout", sections[1].key)
    }

    @Test
    fun `currentUpcoming levert de gecachete briefing als die bestaat, zonder opnieuw op te bouwen`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        val cached = BriefingResponse(sections = listOf(BriefingSection(key = "x", title = "x", text = "x")), updatedAt = "vast")
        upcomingCache.store(cached)
        val svc = service(listOf(FixedProvider(0, "a")), upcomingCache)

        val response = svc.currentUpcoming()

        assertSame(cached, response)
    }

    @Test
    fun `refreshUpcoming bouwt altijd live op en overschrijft alleen de Upcoming-cache`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        upcomingCache.store(BriefingResponse(sections = listOf(BriefingSection(key = "oud", title = "oud", text = "oud")), updatedAt = "vast"))
        val healthCache = InMemoryBriefingCacheRepository()
        val healthCached = BriefingResponse(sections = listOf(BriefingSection(key = "system-status", title = "s", text = "s")), updatedAt = "health-vast")
        healthCache.store(healthCached)
        val svc = service(listOf(FixedProvider(0, "nieuw")), upcomingCache, healthCache)

        val response = svc.refreshUpcoming()

        assertEquals(listOf("nieuw"), response.sections.map { it.key })
        assertEquals(response, upcomingCache.current())
        assertSame(healthCached, healthCache.current())
    }

    @Test
    fun `refreshHealth bouwt altijd live op en overschrijft alleen de Health check-cache`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        val upcomingCached = BriefingResponse(sections = listOf(BriefingSection(key = "a", title = "a", text = "a")), updatedAt = "upcoming-vast")
        upcomingCache.store(upcomingCached)
        val healthCache = InMemoryBriefingCacheRepository()
        val svc = service(listOf(FixedProvider(0, "a"), systemStatusProvider()), upcomingCache, healthCache)

        val response = svc.refreshHealth()

        assertEquals(listOf("system-status"), response.sections.map { it.key })
        assertEquals(response, healthCache.current())
        assertSame(upcomingCached, upcomingCache.current())
    }

    @Test
    fun `zonder cache levert currentUpcoming een live opbouw zonder deze te cachen`() {
        val upcomingCache = InMemoryBriefingCacheRepository()
        val svc = service(listOf(FixedProvider(0, "a")), upcomingCache)

        svc.currentUpcoming()

        assertNull(upcomingCache.current())
    }

    @Test
    fun `zonder cache levert currentHealth een live opbouw zonder deze te cachen`() {
        val healthCache = InMemoryBriefingCacheRepository()
        val svc = service(listOf(systemStatusProvider()), healthCache = healthCache)

        svc.currentHealth()

        assertNull(healthCache.current())
    }
}
