package nl.vdzon.robbertsassistent.briefing

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Ververst elk uur (op het hele uur) zowel de Upcoming- als de Health check-cache
 * ([BriefingService.refreshUpcoming]/[BriefingService.refreshHealth]) — dezelfde opbouw-/
 * opslaglogica als de handmatige `POST /api/v1/briefing/refresh`- en
 * `/api/v1/briefing/health/refresh`-endpoints. Sinds SF-1275 vervangt dit de eerdere dagelijkse
 * 17:30-cron; beide caches blijven onafhankelijk (een falende refresh van de ene raakt de andere
 * niet). [BriefingScheduler] (de dagelijkse 18:00-FCM-push) bouwt zelf nog los van deze cache op en
 * wordt door deze wijziging niet geraakt. Een falende sectie crasht de job niet (zie
 * `BriefingService.buildFresh`'s `runCatching` per sectie).
 */
@Component
class BriefingCacheScheduler(private val briefingService: BriefingService) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 * * * *")
    fun refreshCaches() {
        runCatching { briefingService.refreshUpcoming() }
            .onFailure { logger.warn("Uurlijkse Upcoming-cache-refresh mislukt: {}", it.message) }
        runCatching { briefingService.refreshHealth() }
            .onFailure { logger.warn("Uurlijkse Health check-cache-refresh mislukt: {}", it.message) }
    }
}
