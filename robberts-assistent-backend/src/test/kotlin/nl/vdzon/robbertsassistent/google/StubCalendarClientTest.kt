package nl.vdzon.robbertsassistent.google

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class StubCalendarClientTest {

    @Test
    fun `eventsInRange filtert op starttijd binnen het venster`() {
        val client = StubCalendarClient()
        val now = Instant.now()

        val within7Days = client.eventsInRange(now, now.plus(Duration.ofDays(7)))

        assertTrue(within7Days.any { it.summary == "Standup" })
        assertTrue(within7Days.none { it.summary == "Vakantie Frankrijk" }, "vakantie start pas over 30 dagen")
    }

    @Test
    fun `de vakantie-afspraak is een hele-dag-item`() {
        val client = StubCalendarClient()

        val vacation = client.upcoming(50).single { it.summary == "Vakantie Frankrijk" }

        assertTrue(vacation.allDay)
    }
}
