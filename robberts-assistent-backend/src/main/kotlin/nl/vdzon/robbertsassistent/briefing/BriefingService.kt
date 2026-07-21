package nl.vdzon.robbertsassistent.briefing

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Bouwt de dagelijkse 'Morgen'-briefing op uit alle geregistreerde [BriefingSectionProvider]s (zie
 * [BriefingSectionProvider] voor het SPI-patroon). Een crashende sectie mag de rest van de
 * briefing niet meenemen — zelfde beschermende `runCatching` als `NightlyCheckScheduler`.
 *
 * Sinds SF-1200 wordt de briefing gecachet ([BriefingCacheRepository]): [current] levert de
 * gecachete versie (met de `updatedAt` van het moment van cachen), of bouwt live op zonder te
 * cachen als er nog geen cache is. [refresh] bouwt altijd live op en overschrijft de cache — gebruikt
 * door zowel [BriefingCacheScheduler] (17:30) als `BriefingController`'s `/refresh`-endpoint, zodat
 * er geen dubbele opbouw-/opslaglogica is.
 */
@Service
class BriefingService(
    private val providers: List<BriefingSectionProvider>,
    private val cacheRepository: BriefingCacheRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun current(): BriefingResponse = cacheRepository.current() ?: buildFresh()

    fun refresh(): BriefingResponse {
        val fresh = buildFresh()
        cacheRepository.store(fresh)
        return fresh
    }

    private fun buildFresh(): BriefingResponse = BriefingResponse(
        sections = providers.sortedBy { it.order }.map { provider ->
            runCatching { provider.section() }.getOrElse {
                logger.warn("Briefingsectie faalde, sla over", it)
                BriefingSection(key = "fout", title = "Fout", text = "Kon deze sectie niet opbouwen.")
            }
        },
        updatedAt = Instant.now().toString(),
    )
}
