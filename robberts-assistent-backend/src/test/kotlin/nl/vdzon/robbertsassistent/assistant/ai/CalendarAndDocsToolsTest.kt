package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.google.StubCalendarClient
import nl.vdzon.robbertsassistent.google.StubDocsClient
import kotlin.test.Test
import kotlin.test.assertTrue

class CalendarAndDocsToolsTest {

    @Test
    fun `findEvents vindt de tandarts-afspraak`() {
        val tools = CalendarTools(StubCalendarClient())
        val result = tools.findEvents("tandarts")

        assertTrue(result.contains("Tandarts"), "verwacht de tandarts-afspraak: $result")
    }

    @Test
    fun `upcomingEvents somt afspraken op`() {
        val tools = CalendarTools(StubCalendarClient())

        assertTrue(tools.upcomingEvents().contains("Standup"))
    }

    @Test
    fun `readDoc geeft de stub-inhoud terug`() {
        val tools = DocsTools(StubDocsClient())

        assertTrue(tools.readDoc("doc-123").contains("Wifi"))
    }
}
