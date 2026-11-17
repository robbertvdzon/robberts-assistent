package nl.vdzon.robbertsassistent.activity

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityServiceTest {
    private val now = Instant.parse("2026-11-17T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = InMemoryActivityDayRepository()
    private val service = ActivityService(repository, clock)
    private val day = LocalDate.parse("2026-11-17")

    @Test
    fun `sync vervangt de dag en zet bron en tijdstip`() {
        service.sync(listOf(ActivityDay(date = day, steps = 4000, totalKcal = 1500)), "health-connect")
        val workout = Workout(Instant.parse("2026-11-17T18:00:00Z"), Instant.parse("2026-11-17T19:05:00Z"), "BIKING", 654)
        service.sync(listOf(ActivityDay(date = day, steps = 4609, totalKcal = 2485, workouts = listOf(workout))), "health-connect")

        val stored = service.day(day)!!
        assertEquals(4609, stored.steps)
        assertEquals(2485, stored.totalKcal)
        assertEquals(listOf(workout), stored.workouts)
        assertEquals("health-connect", stored.source)
        assertEquals(now, stored.syncedAt)
    }

    @Test
    fun `handmatig gewicht overleeft een sync en kan los worden gezet`() {
        service.setWeight(day, 81.4)
        service.sync(listOf(ActivityDay(date = day, steps = 5000)), "health-connect")
        assertEquals(81.4, service.day(day)!!.weightKg)
        assertEquals(5000, service.day(day)!!.steps)

        // Gewicht op een dag zonder sync maakt een dag met alleen gewicht, zonder sync-tijdstip.
        val other = service.setWeight(day.minusDays(3), 82.0)
        assertNull(other.syncedAt)
        assertFailsWith<IllegalArgumentException> { service.setWeight(day, 5.0) }
    }

    @Test
    fun `bereik geeft de dagen oplopend en inclusief de grenzen`() {
        service.sync((0L..4L).map { ActivityDay(date = day.minusDays(it), steps = 1000 + it.toInt()) }, "health-connect")
        val range = service.between(day.minusDays(3), day.minusDays(1))
        assertEquals(listOf(day.minusDays(3), day.minusDays(2), day.minusDays(1)), range.map { it.date })
    }

    @Test
    fun `koppelingsstatus volgt de leeftijd van de laatste sync`() {
        val probe = HealthConnectCouplingProbe(service, clock)
        assertFalse(probe.configured)
        assertFalse(probe.test().first)

        service.sync(listOf(ActivityDay(date = day, steps = 1)), "health-connect")
        assertTrue(probe.configured)
        assertEquals(true to "laatste sync 0 min geleden", probe.test())

        val later = HealthConnectCouplingProbe(service, Clock.fixed(now.plusSeconds(3 * 24 * 3600), ZoneOffset.UTC))
        assertEquals(false to "laatste sync 3 dagen geleden", later.test())
    }
}
