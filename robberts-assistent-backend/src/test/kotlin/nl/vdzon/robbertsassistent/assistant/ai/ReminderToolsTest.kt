package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.reminders.InMemoryReminderRepository
import nl.vdzon.robbertsassistent.reminders.RemindersService
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderToolsTest {
    private val service = RemindersService(InMemoryReminderRepository())
    private val tools = ReminderTools(service)

    @Test
    fun `createReminder zet een eenmalige reminder in de toekomst`() {
        val before = Instant.now()
        tools.createReminder("bel de tandarts", 10)

        val reminder = service.list().single()
        assertEquals("bel de tandarts", reminder.message)
        assertTrue(reminder.dueAt.isAfter(before.plus(Duration.ofMinutes(9))))
        assertEquals(null, reminder.recurrence)
    }

    @Test
    fun `createReminder met herhaling zet een recurrence`() {
        tools.createReminder("btw aangifte", 0, everyUnit = "maand", everyInterval = 3)

        val reminder = service.list().single()
        assertEquals(3, reminder.recurrence?.interval)
    }

    @Test
    fun `listReminders meldt leeg en toont daarna de reminder`() {
        assertEquals("Er staan geen reminders open.", tools.listReminders())

        tools.createReminder("boodschappen doen", 5)
        assertTrue(tools.listReminders().contains("boodschappen doen"))
    }

    @Test
    fun `deleteReminder verwijdert op id-prefix`() {
        tools.createReminder("weg", 5)
        val id = service.list().single().id
        val result = tools.deleteReminder(id.take(8))

        assertTrue(result.contains("verwijderd"))
        assertTrue(service.list().isEmpty())
    }
}
