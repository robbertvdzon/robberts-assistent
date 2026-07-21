package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import nl.vdzon.robbertsassistent.reminders.InMemoryReminderRepository
import nl.vdzon.robbertsassistent.reminders.RemindersService
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaSectionProviderTest {

    private class FixedCalendarClient(private val events: List<CalendarEvent>) : CalendarClient {
        override fun upcoming(maxResults: Int) = events.take(maxResults)
        override fun search(query: String) = events
        override fun eventsInRange(from: Instant, to: Instant) = events.filter { !it.start.isBefore(from) && it.start.isBefore(to) }
    }

    @Test
    fun `hasReminderFor is waar bij een reminder 30-90 min voor de afspraak`() {
        val start = Instant.now().plus(Duration.ofDays(1))
        val event = CalendarEvent("Tandarts", start, start.plusSeconds(1800))
        val reminder = nl.vdzon.robbertsassistent.reminders.Reminder(
            id = "1", message = "Tandarts", dueAt = start.minus(Duration.ofMinutes(60)),
        )

        assertTrue(AgendaSectionProvider.hasReminderFor(event, listOf(reminder)))
    }

    @Test
    fun `hasReminderFor is onwaar zonder passende reminder`() {
        val start = Instant.now().plus(Duration.ofDays(1))
        val event = CalendarEvent("Tandarts", start, start.plusSeconds(1800))
        val teVroeg = nl.vdzon.robbertsassistent.reminders.Reminder(
            id = "1", message = "Tandarts", dueAt = start.minus(Duration.ofHours(5)),
        )
        val inactief = nl.vdzon.robbertsassistent.reminders.Reminder(
            id = "2", message = "Tandarts", dueAt = start.minus(Duration.ofMinutes(60)), active = false,
        )

        assertFalse(AgendaSectionProvider.hasReminderFor(event, listOf(teVroeg, inactief)))
        assertFalse(AgendaSectionProvider.hasReminderFor(event, emptyList()))
    }

    @Test
    fun `section toont afspraken oplopend met reminder-status`() {
        val now = Instant.now()
        val eerder = CalendarEvent("Standup", now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(2)).plusSeconds(900))
        val later = CalendarEvent("Tandarts", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)).plusSeconds(1800))
        val calendarClient = FixedCalendarClient(listOf(eerder, later))
        val reminders = RemindersService(InMemoryReminderRepository())
        reminders.create("Tandarts", later.start.minus(Duration.ofMinutes(60)))

        val provider = AgendaSectionProvider(calendarClient, reminders)
        val section = provider.section()

        assertEquals("agenda", section.key)
        val lines = section.text.lines()
        assertTrue(lines[0].contains("Tandarts") && lines[0].contains("✅"))
        assertTrue(lines[1].contains("Standup") && lines[1].contains("⚠️"))

        assertEquals(2, section.items.size)
        assertEquals(null, section.items[0].action) // Tandarts heeft al een reminder
        val standupAction = section.items[1].action
        assertTrue(standupAction != null)
        assertEquals("/api/v1/briefing/agenda-reminder", standupAction!!.endpoint)
        assertEquals("Standup", standupAction.payload["summary"])
        assertEquals(eerder.start.toString(), standupAction.payload["startAt"])
    }

    @Test
    fun `section meldt expliciet geen afspraken`() {
        val provider = AgendaSectionProvider(FixedCalendarClient(emptyList()), RemindersService(InMemoryReminderRepository()))

        val section = provider.section()

        assertEquals("Geen afspraken de komende 7 dagen.", section.text)
    }

    @Test
    fun `shortSummary telt het aantal afspraken`() {
        val now = Instant.now()
        val events = listOf(
            CalendarEvent("A", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))),
            CalendarEvent("B", now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(2))),
        )
        val provider = AgendaSectionProvider(FixedCalendarClient(events), RemindersService(InMemoryReminderRepository()))

        assertEquals("2 afspraken", provider.shortSummary())
    }
}
