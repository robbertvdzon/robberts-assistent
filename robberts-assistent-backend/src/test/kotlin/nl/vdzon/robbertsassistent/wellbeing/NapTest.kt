package nl.vdzon.robbertsassistent.wellbeing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NapTest {
    private val now = Instant.parse("2026-11-18T14:00:00Z")
    private val repository = InMemoryNapRepository()
    private val service = NapService(repository, Clock.fixed(now, ZoneOffset.UTC))

    private fun minutesAgo(minutes: Long): Instant = now.minusSeconds(minutes * 60)

    @Test
    fun `een dutje starten geeft een lopend dutje, en er loopt er hooguit een`() {
        val nap = service.create(alarmAt = now.plusSeconds(1800))
        assertEquals(now, nap.start)
        assertTrue(nap.open)
        assertEquals(nap.id, service.open()?.id)
        assertFailsWith<NapConflictException> { service.create() }
    }

    @Test
    fun `afmelden zet het eind en haalt de schatting weg`() {
        val nap = service.create(start = minutesAgo(60))
        service.applyEstimate(nap.id, minutesAgo(20))
        assertTrue(service.get(nap.id).endEstimated)

        val done = service.update(nap.id, end = minutesAgo(15), clearAlarm = true)
        assertEquals(minutesAgo(15), done.end)
        assertEquals(false, done.endEstimated)
        assertNull(service.open())
        // Een eind dat Robbert zelf opgaf wordt niet meer overschreven.
        assertEquals(minutesAgo(15), service.applyEstimate(nap.id, minutesAgo(40)).end)
    }

    @Test
    fun `achteraf invullen en ongeldige tijden`() {
        val nap = service.create(start = minutesAgo(120), end = minutesAgo(90), alarmAt = minutesAgo(80))
        assertEquals(false, nap.open)
        assertNull(nap.alarmAt)
        assertNull(service.open())

        assertFailsWith<IllegalArgumentException> { service.create(start = minutesAgo(10), end = minutesAgo(20)) }
        assertFailsWith<IllegalArgumentException> { service.create(start = now.plusSeconds(3600)) }
        assertFailsWith<IllegalArgumentException> { service.create(start = minutesAgo(10), end = now.plusSeconds(3600)) }
        assertFailsWith<IllegalArgumentException> { service.create(start = minutesAgo(10), alarmAt = minutesAgo(20)) }
        assertFailsWith<NapNotFoundException> { service.update("bestaat-niet", end = now) }
    }

    @Test
    fun `schatting neemt het vroegste teken en schuift alleen naar voren`() {
        val nap = service.create(start = minutesAgo(120))
        assertTrue(service.applyEstimate(nap.id, null).open, "zonder teken blijft het dutje lopen")

        assertEquals(minutesAgo(30), service.applyEstimate(nap.id, minutesAgo(30)).end)
        assertEquals(minutesAgo(70), service.applyEstimate(nap.id, minutesAgo(70)).end, "vroeger teken wint")
        assertEquals(minutesAgo(70), service.applyEstimate(nap.id, minutesAgo(10)).end, "later teken verandert niets")
        assertEquals(listOf(nap.id), service.pending().map { it.id }, "geschat blijft in de lijst om te verbeteren")
    }

    @Test
    fun `een teken dat te laat komt of ontbreekt valt terug op de wekker of een half uur`() {
        val withAlarm = service.create(start = minutesAgo(600), alarmAt = minutesAgo(560))
        assertEquals(minutesAgo(560), service.applyEstimate(withAlarm.id, minutesAgo(60)).end)
        repository.delete(withAlarm.id)

        val old = repository.save(Nap("oud", minutesAgo(13 * 60)))
        assertEquals(minutesAgo(13 * 60 - 30), service.applyEstimate(old.id, null).end)
        assertTrue(service.get(old.id).endEstimated)
    }

    @Test
    fun `dagoverzicht volgt de Nederlandse dag`() {
        repository.save(Nap("a", Instant.parse("2026-11-17T23:30:00Z"), Instant.parse("2026-11-17T23:50:00Z")))
        repository.save(Nap("b", Instant.parse("2026-11-18T12:00:00Z"), Instant.parse("2026-11-18T12:30:00Z")))
        repository.save(Nap("c", Instant.parse("2026-11-17T12:00:00Z"), Instant.parse("2026-11-17T12:30:00Z")))
        assertEquals(listOf("a", "b"), service.day(LocalDate.parse("2026-11-18")).map { it.id })
    }
}
