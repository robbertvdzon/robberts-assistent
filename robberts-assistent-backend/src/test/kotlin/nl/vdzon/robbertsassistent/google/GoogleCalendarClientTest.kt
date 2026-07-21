package nl.vdzon.robbertsassistent.google

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dekt de pure `toCalendarEvent`-conversie zonder HTTP, zelfde patroon als `RwsTideClientTest`. */
class GoogleCalendarClientTest {

    @Test
    fun `toCalendarEvent bewaart het hele-dag-karakter van een 'date'-event`() {
        val json = jacksonObjectMapper().readTree(
            """{"summary": "Vakantie", "start": {"date": "2026-08-01"}, "end": {"date": "2026-08-15"}}""",
        )

        val event = GoogleCalendarClient.toCalendarEvent(json)

        assertTrue(event != null && event.allDay)
    }

    @Test
    fun `toCalendarEvent zet allDay op false voor een 'dateTime'-event`() {
        val json = jacksonObjectMapper().readTree(
            """
            {"summary": "Standup", "start": {"dateTime": "2026-07-22T09:00:00+02:00"},
             "end": {"dateTime": "2026-07-22T09:15:00+02:00"}, "location": "Online"}
            """.trimIndent(),
        )

        val event = GoogleCalendarClient.toCalendarEvent(json)

        assertFalse(event!!.allDay)
        assertEquals("Standup", event.summary)
        assertEquals("Online", event.location)
    }

    @Test
    fun `toCalendarEvent geeft null zonder start-tijd`() {
        val json = jacksonObjectMapper().readTree("""{"summary": "Kapot event", "start": {}, "end": {}}""")

        assertNull(GoogleCalendarClient.toCalendarEvent(json))
    }
}
