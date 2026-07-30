package nl.vdzon.robbertsassistent.watches

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchSchedulingTest {
    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    @Test
    fun `kantooruren is due zonder eerdere check, binnen kantooruren`() {
        // Donderdag 2026-07-30, 09:15.
        val now = at(2026, 7, 30, 9, 15)
        assertTrue(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, null, now, zone))
    }

    @Test
    fun `kantooruren is niet due buiten kantooruren of in het weekend`() {
        val avond = at(2026, 7, 30, 18, 0)
        assertFalse(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, null, avond, zone))

        val vroeg = at(2026, 7, 30, 8, 30)
        assertFalse(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, null, vroeg, zone))

        // 2026-08-01 is een zaterdag.
        val weekend = at(2026, 8, 1, 10, 0)
        assertFalse(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, null, weekend, zone))
    }

    @Test
    fun `kantooruren is niet opnieuw due binnen hetzelfde uur-blok`() {
        val lastChecked = at(2026, 7, 30, 10, 5)
        val laterZelfdeUur = at(2026, 7, 30, 10, 55)
        assertFalse(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, lastChecked, laterZelfdeUur, zone))
    }

    @Test
    fun `kantooruren is weer due in het volgende uur-blok`() {
        val lastChecked = at(2026, 7, 30, 10, 5)
        val volgendUur = at(2026, 7, 30, 11, 1)
        assertTrue(WatchScheduling.isDue(WatchFrequency.KANTOORUREN, lastChecked, volgendUur, zone))
    }

    @Test
    fun `dagelijks is due zonder eerdere check`() {
        assertTrue(WatchScheduling.isDue(WatchFrequency.DAGELIJKS, null, at(2026, 7, 30, 3, 0), zone))
    }

    @Test
    fun `dagelijks is niet opnieuw due op dezelfde kalenderdag`() {
        val lastChecked = at(2026, 7, 30, 6, 0)
        val laterZelfdeDag = at(2026, 7, 30, 23, 0)
        assertFalse(WatchScheduling.isDue(WatchFrequency.DAGELIJKS, lastChecked, laterZelfdeDag, zone))
    }

    @Test
    fun `dagelijks is weer due op de volgende kalenderdag`() {
        val lastChecked = at(2026, 7, 30, 23, 0)
        val volgendeDag = at(2026, 7, 31, 0, 5)
        assertTrue(WatchScheduling.isDue(WatchFrequency.DAGELIJKS, lastChecked, volgendeDag, zone))
    }
}
