package nl.vdzon.robbertsassistent.watches

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object WatchSchedule {
    val zone: ZoneId = ZoneId.of("Europe/Amsterdam")

    /**
     * Elke actieve zoekopdracht wordt overdag elk uur gecontroleerd: de eerste controle vanaf
     * 08:00 en de laatste vanaf 22:00 (Europe/Amsterdam), elke dag van de week. Tussen 23:00 en
     * 08:00 gebeurt er niets.
     */
    fun isDue(watch: Watch, now: Instant): Boolean {
        if (!watch.active) return false
        val inDayWindow = now.atZone(zone).hour in 8..22
        val atLeastOneHourSinceLastCheck = watch.lastCheckedAt == null ||
            Duration.between(watch.lastCheckedAt, now) >= Duration.ofHours(1)
        return inDayWindow && atLeastOneHourSinceLastCheck
    }
}
