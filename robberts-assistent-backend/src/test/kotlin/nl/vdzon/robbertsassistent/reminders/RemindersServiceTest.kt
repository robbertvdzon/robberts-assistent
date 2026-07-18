package nl.vdzon.robbertsassistent.reminders

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `due geeft alleen verstreken, niet-afgeleverde reminders`() {
        val now = Instant.now()
        val verleden = service.create("verleden", now.minusSeconds(10))
        service.create("toekomst", now.plusSeconds(600))

        assertEquals(listOf("verleden"), service.due(now).map { it.message })

        service.markDelivered(verleden.id)
        assertTrue(service.due(now).isEmpty())
    }

    @Test
    fun `delete verwijdert de reminder`() {
        val reminder = service.create("weg", Instant.now())
        service.delete(reminder.id)

        assertTrue(service.list().isEmpty())
    }
}
