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
    fun `createReminderInMinutes zet een reminder in de toekomst`() {
        val before = Instant.now()
        tools.createReminderInMinutes("bel de tandarts", 10)

        val reminder = service.list().single()
        assertEquals("bel de tandarts", reminder.message)
        assertTrue(reminder.dueAt.isAfter(before.plus(Duration.ofMinutes(9))))
    }

    @Test
    fun `listOpenReminders meldt leeg en toont daarna de reminder`() {
        assertEquals("Er staan geen reminders open.", tools.listOpenReminders())

        tools.createReminderInMinutes("boodschappen doen", 5)
        assertTrue(tools.listOpenReminders().contains("boodschappen doen"))
    }
}
