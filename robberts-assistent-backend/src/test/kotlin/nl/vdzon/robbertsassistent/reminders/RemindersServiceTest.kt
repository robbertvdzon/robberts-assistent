package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.scheduling.Recurrence
import nl.vdzon.robbertsassistent.scheduling.RecurrenceUnit
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemindersServiceTest {
    private val service = RemindersService(InMemoryReminderRepository())

    @Test
    fun `list sorteert oplopend op tijd`() {
        val now = Instant.now()
        service.create("later", now.plusSeconds(120))
        service.create("eerder", now.plusSeconds(60))

        assertEquals(listOf("eerder", "later"), service.list().map { it.message })
    }

    @Test
    fun `due geeft alleen actieve, verstreken reminders`() {
        val now = Instant.now()
        val verleden = service.create("verleden", now.minusSeconds(10))
        service.create("toekomst", now.plusSeconds(600))

        assertEquals(listOf("verleden"), service.due(now).map { it.message })

        service.markFired(verleden, now) // eenmalig → inactief
        assertTrue(service.due(now).isEmpty())
    }

    @Test
    fun `markFired schuift een herhalende reminder door naar de toekomst`() {
        val now = Instant.now()
        val reminder = service.create("btw", now.minusSeconds(10), Recurrence(RecurrenceUnit.MONTHS, 3))

        service.markFired(reminder, now)

        val updated = service.list().single()
        assertTrue(updated.active)
        assertTrue(updated.dueAt.isAfter(now))
    }

    @Test
    fun `markFired zet een eenmalige reminder op inactief`() {
        val now = Instant.now()
        val reminder = service.create("eenmalig", now.minusSeconds(10))

        service.markFired(reminder, now)

        assertFalse(service.list().single().active)
    }

    @Test
    fun `delete verwijdert de reminder`() {
        val reminder = service.create("weg", Instant.now())
        service.delete(reminder.id)

        assertTrue(service.list().isEmpty())
    }
}
