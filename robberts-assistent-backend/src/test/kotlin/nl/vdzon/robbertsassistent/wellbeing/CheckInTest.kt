package nl.vdzon.robbertsassistent.wellbeing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import nl.vdzon.robbertsassistent.push.PushService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CheckInTest {
    private val service = CheckInService(InMemoryCheckInRepository())
    private val push = Mockito.mock(PushService::class.java)
    private val now = Instant.parse("2026-11-17T14:30:00Z")
    private val scheduler = CheckInPromptScheduler(service, push, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `check-in bewaart beide schalen los van elkaar`() {
        val both = service.record(hunger = 1, energy = 4, note = " na het fietsen ", source = CheckInSource.MELDING)
        assertEquals(1, both.hunger)
        assertEquals(4, both.energy)
        assertEquals("na het fietsen", both.note)

        val onlyHunger = service.record(hunger = 4, energy = null)
        assertEquals(null, onlyHunger.energy)
    }

    @Test
    fun `waarden buiten de schaal en een lege check-in worden geweigerd`() {
        assertFailsWith<IllegalArgumentException> { service.record(null, null) }
        assertFailsWith<IllegalArgumentException> { service.record(5, null) }
        assertFailsWith<IllegalArgumentException> { service.record(null, 0) }
        assertFailsWith<IllegalArgumentException> { service.record(null, 6) }
    }

    @Test
    fun `dagoverzicht volgt de Nederlandse dag en staat op tijd`() {
        service.record(1, 3, at = Instant.parse("2026-11-17T09:30:00Z"))
        service.record(0, 4, at = Instant.parse("2026-11-17T07:00:00Z"))
        // 23:30 UTC op de 16e is 00:30 Nederlandse tijd op de 17e.
        service.record(2, 2, at = Instant.parse("2026-11-16T23:30:00Z"))
        service.record(0, 5, at = Instant.parse("2026-11-16T12:00:00Z"))

        val day = service.day(LocalDate.parse("2026-11-17"))
        assertEquals(listOf(2, 0, 1), day.map { it.hunger })
    }

    @Test
    fun `vraag gaat uit als er het afgelopen uur geen check-in was`() {
        service.record(1, 3, at = now.minusSeconds(2 * 3600))
        scheduler.prompt()
        Mockito.verify(push).sendToAll("Hoe voel je je?", "Twee tikken: honger en energie.", mapOf("type" to "checkin"))
    }

    @Test
    fun `vraag wordt overgeslagen na een recente check-in`() {
        service.record(1, 3, at = now.minusSeconds(20 * 60))
        scheduler.prompt()
        Mockito.verifyNoInteractions(push)
    }

    @Test
    fun `check-ins van de oude schaal schuiven bij het lezen op, nieuwe blijven staan`() {
        // Oud: 0 geen, 1 trek, 2 honger, 3 slap. Nieuw: 2 is beetje honger, 3 honger, 4 slap.
        assertEquals(listOf(0, 1, 3, 4), listOf(0, 1, 2, 3).map { FirestoreCheckInRepository.upgradeHunger(it, null) })
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0, 1, 2, 3, 4).map { FirestoreCheckInRepository.upgradeHunger(it, 2) })
    }
}
