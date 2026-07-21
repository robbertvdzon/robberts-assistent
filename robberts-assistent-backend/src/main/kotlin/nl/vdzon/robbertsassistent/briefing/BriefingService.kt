package nl.vdzon.robbertsassistent.briefing

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Bouwt de dagelijkse 'Morgen'-briefing op uit alle geregistreerde [BriefingSectionProvider]s (zie
 * [BriefingSectionProvider] voor het SPI-patroon). Een crashende sectie mag de rest van de
 * briefing niet meenemen — zelfde beschermende `runCatching` als `NightlyCheckScheduler`.
 */
@Service
class BriefingService(private val providers: List<BriefingSectionProvider>) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun current(): BriefingResponse = BriefingResponse(
        providers.sortedBy { it.order }.map { provider ->
            runCatching { provider.section() }.getOrElse {
                logger.warn("Briefingsectie faalde, sla over", it)
                BriefingSection(key = "fout", title = "Fout", text = "Kon deze sectie niet opbouwen.")
            }
        },
    )
}
