package nl.vdzon.robbertsassistent.watches

import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchScheduleTest {
    private fun instant(dateTime: String) =
        ZonedDateTime.of(LocalDateTime.parse(dateTime), WatchSchedule.zone).toInstant()

    private fun watch(
        frequency: WatchFrequency,
        lastCheckedAt: java.time.Instant? = null,
        active: Boolean = true,
    ) = Watch("1", "titel", "https://example.com", "zoek", frequency, false, lastCheckedAt = lastCheckedAt, active = active)

    @Test
    fun `kantooruren zijn werkdagen van 09 inclusief tot 17 exclusief`() {
        val office = watch(WatchFrequency.KANTOORUREN)

        assertFalse(WatchSchedule.isDue(office, instant("2026-07-27T08:59:59")))
        assertTrue(WatchSchedule.isDue(office, instant("2026-07-27T09:00:00")))
        assertTrue(WatchSchedule.isDue(office, instant("2026-07-27T16:59:59")))
        assertFalse(WatchSchedule.isDue(office, instant("2026-07-27T17:00:00")))
        assertFalse(WatchSchedule.isDue(office, instant("2026-08-01T10:00:00")))
        assertFalse(WatchSchedule.isDue(office, instant("2026-08-02T10:00:00")))
    }

    @Test
    fun `kantooruren controleert pas na minimaal een verstreken uur opnieuw`() {
        val checked = watch(WatchFrequency.KANTOORUREN, instant("2026-07-27T09:59:59"))

        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-27T10:00:00")))
        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-27T10:59:58")))
        assertTrue(WatchSchedule.isDue(checked, instant("2026-07-27T10:59:59")))
    }

    @Test
    fun `dagelijks controleert maximaal eenmaal per lokale kalenderdag`() {
        val checked = watch(WatchFrequency.DAGELIJKS, instant("2026-07-27T00:01:00"))

        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-27T23:59:59")))
        assertTrue(WatchSchedule.isDue(checked, instant("2026-07-28T00:00:00")))
    }

    @Test
    fun `nieuwe actieve watch is direct bij eerst passende poll aan de beurt`() {
        assertTrue(WatchSchedule.isDue(watch(WatchFrequency.DAGELIJKS), instant("2026-07-27T03:00:00")))
        assertTrue(WatchSchedule.isDue(watch(WatchFrequency.KANTOORUREN), instant("2026-07-27T09:00:00")))
        assertFalse(
            WatchSchedule.isDue(
                watch(WatchFrequency.DAGELIJKS, active = false),
                instant("2026-07-27T03:00:00"),
            ),
        )
    }
}
