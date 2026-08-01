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
        lastCheckedAt: java.time.Instant? = null,
        active: Boolean = true,
    ) = Watch("1", "titel", "https://example.com", "zoek", false, lastCheckedAt = lastCheckedAt, active = active)

    @Test
    fun `controle loopt van 08 tot en met 22 uur en daarbuiten niet`() {
        val watch = watch()

        assertFalse(WatchSchedule.isDue(watch, instant("2026-07-27T03:00:00")))
        assertFalse(WatchSchedule.isDue(watch, instant("2026-07-27T07:59:59")))
        assertTrue(WatchSchedule.isDue(watch, instant("2026-07-27T08:00:00")))
        assertTrue(WatchSchedule.isDue(watch, instant("2026-07-27T22:00:00")))
        assertTrue(WatchSchedule.isDue(watch, instant("2026-07-27T22:59:59")))
        assertFalse(WatchSchedule.isDue(watch, instant("2026-07-27T23:00:00")))
    }

    @Test
    fun `ook in het weekend wordt er overdag gecontroleerd`() {
        val watch = watch()

        // 2026-08-01 is een zaterdag, 2026-08-02 een zondag.
        assertTrue(WatchSchedule.isDue(watch, instant("2026-08-01T10:00:00")))
        assertTrue(WatchSchedule.isDue(watch, instant("2026-08-02T10:00:00")))
        assertFalse(WatchSchedule.isDue(watch, instant("2026-08-01T23:30:00")))
    }

    @Test
    fun `controleert pas na minimaal een verstreken uur opnieuw`() {
        val checked = watch(instant("2026-07-27T09:59:59"))

        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-27T10:00:00")))
        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-27T10:59:58")))
        assertTrue(WatchSchedule.isDue(checked, instant("2026-07-27T10:59:59")))
        assertTrue(WatchSchedule.isDue(checked, instant("2026-07-27T12:00:00")))
    }

    @Test
    fun `een verstreken uur buiten het dagvenster maakt nog niets aan de beurt`() {
        val checked = watch(instant("2026-07-27T22:30:00"))

        assertFalse(WatchSchedule.isDue(checked, instant("2026-07-28T00:00:00")))
        assertTrue(WatchSchedule.isDue(checked, instant("2026-07-28T08:00:00")))
    }

    @Test
    fun `een inactieve watch is nooit aan de beurt`() {
        val inactief = watch(active = false)

        assertFalse(WatchSchedule.isDue(inactief, instant("2026-07-27T03:00:00")))
        assertFalse(WatchSchedule.isDue(inactief, instant("2026-07-27T08:00:00")))
        assertFalse(WatchSchedule.isDue(inactief, instant("2026-07-27T22:00:00")))
    }
}
