package nl.vdzon.robbertsassistent.briefing

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Bouwt elke dag om 17:30 (Europe/Amsterdam, een half uur vóór [BriefingScheduler]'s 18:00-push) de
 * 'Morgen'-briefing op en cachet 'm via [BriefingService.refresh] — dezelfde opbouw-/opslaglogica als
 * het handmatige `POST /api/v1/briefing/refresh`-endpoint. Een falende sectie crasht de job niet
 * (zie `BriefingService.buildFresh`'s `runCatching` per sectie).
 */
@Component
class BriefingCacheScheduler(private val briefingService: BriefingService) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 17 * * *", zone = "Europe/Amsterdam")
    fun refreshCache() {
        runCatching { briefingService.refresh() }
            .onFailure { logger.warn("Dagelijkse briefing-cache-refresh mislukt: {}", it.message) }
    }
}
