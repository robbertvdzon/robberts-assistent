package nl.vdzon.robbertsassistent.watches

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class WatchSchedulerIsDueTest {
    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun instantAt(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    private fun watch(
        frequency: WatchFrequency,
        lastChecked: Instant? = null,
    ) = Watch(
        id = "test",
        title = "Test Watch",
        url = "https://example.com",
        instruction = "Zoek iets",
        frequency = frequency,
        lastChecked = lastChecked,
    )

    @Test
    fun `KANTOORUREN - nooit gecheckt op maandag 10u is due`() {
        // Maandag 28 juli 2026, 10:00
        val now = instantAt(2026, 7, 27, 10)
        val w = watch(WatchFrequency.KANTOORUREN)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - gecheckt 2 uur geleden op maandag 10u is due`() {
        val now = instantAt(2026, 7, 27, 10)
        val lastChecked = instantAt(2026, 7, 27, 8) // 2 uur eerder
        val w = watch(WatchFrequency.KANTOORUREN, lastChecked)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - gecheckt 30 min geleden is niet due`() {
        val now = instantAt(2026, 7, 27, 10, 30)
        val lastChecked = instantAt(2026, 7, 27, 10) // 30 min eerder
        val w = watch(WatchFrequency.KANTOORUREN, lastChecked)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - op zaterdag is niet due`() {
        // Zaterdag 1 augustus 2026, 10:00
        val now = instantAt(2026, 8, 1, 10)
        val w = watch(WatchFrequency.KANTOORUREN)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - op zondag is niet due`() {
        // Zondag 2 augustus 2026, 10:00
        val now = instantAt(2026, 8, 2, 10)
        val w = watch(WatchFrequency.KANTOORUREN)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - voor 9u is niet due`() {
        val now = instantAt(2026, 7, 27, 8, 30) // Maandag 08:30
        val w = watch(WatchFrequency.KANTOORUREN)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - na 17u is niet due`() {
        val now = instantAt(2026, 7, 27, 17, 30) // Maandag 17:30
        val w = watch(WatchFrequency.KANTOORUREN)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - om 9u precies is due`() {
        val now = instantAt(2026, 7, 27, 9, 0) // Maandag 09:00
        val w = watch(WatchFrequency.KANTOORUREN)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `KANTOORUREN - om 16u59 is due`() {
        val now = instantAt(2026, 7, 27, 16, 59) // Maandag 16:59
        val w = watch(WatchFrequency.KANTOORUREN)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `DAGELIJKS - nooit gecheckt is due`() {
        val now = instantAt(2026, 7, 27, 10)
        val w = watch(WatchFrequency.DAGELIJKS)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `DAGELIJKS - gecheckt 25 uur geleden is due`() {
        val now = instantAt(2026, 7, 28, 11)
        val lastChecked = instantAt(2026, 7, 27, 10) // 25 uur eerder
        val w = watch(WatchFrequency.DAGELIJKS, lastChecked)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `DAGELIJKS - gecheckt 23 uur geleden is niet due`() {
        val now = instantAt(2026, 7, 28, 9)
        val lastChecked = instantAt(2026, 7, 27, 10) // 23 uur eerder
        val w = watch(WatchFrequency.DAGELIJKS, lastChecked)
        assertFalse(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `DAGELIJKS - gecheckt exact 24 uur geleden is due`() {
        val now = instantAt(2026, 7, 28, 10)
        val lastChecked = instantAt(2026, 7, 27, 10) // exact 24 uur
        val w = watch(WatchFrequency.DAGELIJKS, lastChecked)
        assertTrue(WatchScheduler.isDue(w, now))
    }

    @Test
    fun `DAGELIJKS - werkt ook in het weekend`() {
        // Zaterdag 1 augustus 2026, 10:00
        val now = instantAt(2026, 8, 1, 10)
        val w = watch(WatchFrequency.DAGELIJKS)
        assertTrue(WatchScheduler.isDue(w, now))
    }
}
