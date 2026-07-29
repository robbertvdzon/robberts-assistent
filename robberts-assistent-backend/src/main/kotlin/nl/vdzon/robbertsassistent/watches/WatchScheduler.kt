package nl.vdzon.robbertsassistent.watches

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Achtergrondpoller: kijkt regelmatig welke actieve zoekopdrachten aan de beurt zijn (op basis van
 * hun frequentie + laatste controlemoment) en laat [WatchesService] die controleren. Elke check
 * zit in een eigen `runCatching`, zodat een fout op één zoekopdracht de rest niet blokkeert.
 */
@Component
class WatchScheduler(private val watchesService: WatchesService) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")
    fun pollDue() {
        val now = Instant.now()
        watchesService.list().filter { isDue(it, now) }.forEach { watch ->
            runCatching { watchesService.check(watch, now) }
                .onFailure { logger.warn("Zoekopdracht {} controleren mislukte: {}", watch.id, it.message) }
        }
    }

    internal companion object {
        internal val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        private const val OFFICE_FIRST_HOUR = 9
        // Exclusief: het uurblok dat om 17:00 begint valt buiten kantooruren.
        private const val OFFICE_LAST_HOUR_EXCLUSIVE = 17

        /**
         * Pure "is deze zoekopdracht nu aan de beurt?"-functie (los testbaar, geen klok/IO).
         *
         * - Gepauzeerd (`active = false`) → nooit.
         * - [WatchFrequency.KANTOORUREN] → hoogstens één keer per klokuur, ma t/m vr, in de uren
         *   09:00 t/m 16:59 (Europe/Amsterdam).
         * - [WatchFrequency.DAGELIJKS] → hoogstens één keer per kalenderdag.
         */
        internal fun isDue(watch: Watch, now: Instant, zone: ZoneId = ZONE): Boolean {
            if (!watch.active) return false
            val local = now.atZone(zone)
            val last = watch.lastCheckedAt?.atZone(zone)
            return when (watch.frequency) {
                WatchFrequency.KANTOORUREN -> {
                    if (local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY) return false
                    if (local.hour < OFFICE_FIRST_HOUR || local.hour >= OFFICE_LAST_HOUR_EXCLUSIVE) return false
                    last == null || last.truncatedTo(ChronoUnit.HOURS) < local.truncatedTo(ChronoUnit.HOURS)
                }

                WatchFrequency.DAGELIJKS -> last == null || last.toLocalDate() < local.toLocalDate()
            }
        }
    }
}
