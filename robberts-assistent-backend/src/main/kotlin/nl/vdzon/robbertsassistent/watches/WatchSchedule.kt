package nl.vdzon.robbertsassistent.watches

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object WatchSchedule {
    val zone: ZoneId = ZoneId.of("Europe/Amsterdam")

    fun isDue(watch: Watch, now: Instant): Boolean {
        if (!watch.active) return false
        val localNow = now.atZone(zone)
        val last = watch.lastCheckedAt?.atZone(zone)
        return when (watch.frequency) {
            WatchFrequency.KANTOORUREN -> {
                val weekday = localNow.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                val inOfficeHours = localNow.hour in 9 until 17
                val atLeastOneHourSinceLastCheck = watch.lastCheckedAt == null ||
                    Duration.between(watch.lastCheckedAt, now) >= Duration.ofHours(1)
                weekday && inOfficeHours && atLeastOneHourSinceLastCheck
            }
            WatchFrequency.DAGELIJKS ->
                last == null || last.toLocalDate() != localNow.toLocalDate()
        }
    }
}
