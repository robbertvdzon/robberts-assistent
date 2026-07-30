package nl.vdzon.robbertsassistent.watches

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure "is deze watch aan de beurt?"-logica, los van Spring/scheduling — makkelijk unit-testbaar
 * en herbruikt door [WatchScheduler]. Geen losse per-watch cron-triggers: één globale poller
 * (`ra.watches.poll-interval-ms`) roept dit per actieve watch aan.
 */
object WatchScheduling {
    private val DEFAULT_ZONE = ZoneId.of("Europe/Amsterdam")

    /**
     * - [WatchFrequency.KANTOORUREN]: ma–vr, 09:00–17:00, elk uur — waar (op zijn vroegst) zodra we
     *   in een ander uur-blok zitten dan bij [lastCheckedAt].
     * - [WatchFrequency.DAGELIJKS]: waar zodra de kalenderdag (in [zone]) verschilt van die van
     *   [lastCheckedAt].
     *
     * [lastCheckedAt] `null` (nog nooit gecontroleerd) is altijd waar (mits binnen kantooruren voor
     * [WatchFrequency.KANTOORUREN]).
     */
    fun isDue(
        frequency: WatchFrequency,
        lastCheckedAt: Instant?,
        now: Instant,
        zone: ZoneId = DEFAULT_ZONE,
    ): Boolean = when (frequency) {
        WatchFrequency.KANTOORUREN ->
            isWithinOfficeHours(now, zone) && (lastCheckedAt == null || hourSlot(lastCheckedAt, zone) != hourSlot(now, zone))
        WatchFrequency.DAGELIJKS ->
            lastCheckedAt == null || now.atZone(zone).toLocalDate() != lastCheckedAt.atZone(zone).toLocalDate()
    }

    private fun isWithinOfficeHours(instant: Instant, zone: ZoneId): Boolean {
        val local = instant.atZone(zone)
        return local.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY && local.hour in 9..16
    }

    private fun hourSlot(instant: Instant, zone: ZoneId) =
        instant.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
}
