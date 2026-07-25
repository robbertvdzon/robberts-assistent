package nl.vdzon.robbertsassistent.briefing

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Bouwt de dagelijkse 'Morgen'-briefing op uit alle geregistreerde [BriefingSectionProvider]s (zie
 * [BriefingSectionProvider] voor het SPI-patroon). Een crashende sectie mag de rest van de
 * briefing niet meenemen — zelfde beschermende `runCatching` als `NightlyCheckScheduler`.
 *
 * Sinds SF-1275 zijn de 'Upcoming'- en 'Health check'-tabs onafhankelijk cachebaar: de
 * systeemstatus-sectie ([SystemStatusSectionProvider]) heeft een eigen cache ([healthCache]) met
 * eigen `updatedAt`, los van alle overige secties ([upcomingCache]). Zo raakt een refresh vanuit de
 * ene tab de andere niet. [currentUpcoming]/[currentHealth] leveren de gecachete versie, of bouwen
 * live op zonder te cachen als er nog geen cache is; [refreshUpcoming]/[refreshHealth] bouwen altijd
 * live op en overschrijven alleen hun eigen cache — gebruikt door zowel [BriefingCacheScheduler]
 * (uurlijks) als `BriefingController`'s `/refresh`-endpoints, zodat er geen dubbele
 * opbouw-/opslaglogica is.
 */
@Service
class BriefingService(
    providers: List<BriefingSectionProvider>,
    @Qualifier("upcomingBriefingCache") private val upcomingCache: BriefingCacheRepository,
    @Qualifier("healthBriefingCache") private val healthCache: BriefingCacheRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val healthProviders = providers.filterIsInstance<SystemStatusSectionProvider>()
    private val upcomingProviders = providers.filterNot { it is SystemStatusSectionProvider }

    fun currentUpcoming(): BriefingResponse = upcomingCache.current() ?: buildFresh(upcomingProviders)

    fun refreshUpcoming(): BriefingResponse {
        val fresh = buildFresh(upcomingProviders)
        upcomingCache.store(fresh)
        return fresh
    }

    fun currentHealth(): BriefingResponse = healthCache.current() ?: buildFresh(healthProviders)

    fun refreshHealth(): BriefingResponse {
        val fresh = buildFresh(healthProviders)
        healthCache.store(fresh)
        return fresh
    }

    private fun buildFresh(providers: List<BriefingSectionProvider>): BriefingResponse = BriefingResponse(
        sections = providers.sortedBy { it.order }.map { provider ->
            runCatching { provider.section() }.getOrElse {
                logger.warn("Briefingsectie faalde, sla over", it)
                BriefingSection(key = "fout", title = "Fout", text = "Kon deze sectie niet opbouwen.")
            }
        },
        updatedAt = Instant.now().toString(),
    )
}
