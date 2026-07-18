package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.notifier.Notifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulerTest {

    private class RecordingNotifier : Notifier {
        val sent = mutableListOf<String>()
        override fun send(message: String) {
            sent += message
        }
    }

    @Test
    fun `levert due reminders af en markeert ze, geen dubbele afgifte`() {
        val service = RemindersService(InMemoryReminderRepository())
        val notifier = RecordingNotifier()
        val scheduler = ReminderScheduler(service, notifier)
        service.create("nu", Instant.now().minusSeconds(1))
        service.create("straks", Instant.now().plusSeconds(600))

        scheduler.deliverDue()
        assertEquals(listOf("nu"), notifier.sent)

        // Tweede tick: al afgeleverd, dus niets nieuws.
        scheduler.deliverDue()
        assertEquals(listOf("nu"), notifier.sent)
    }

    @Test
    fun `een mislukte push wordt de volgende tick opnieuw geprobeerd`() {
        val service = RemindersService(InMemoryReminderRepository())
        var shouldFail = true
        val notifier = object : Notifier {
            val sent = mutableListOf<String>()
            override fun send(message: String) {
                if (shouldFail) throw RuntimeException("kanaal down")
                sent += message
            }
        }
        val scheduler = ReminderScheduler(service, notifier)
        service.create("nu", Instant.now().minusSeconds(1))

        scheduler.deliverDue()
        assertTrue(notifier.sent.isEmpty())

        shouldFail = false
        scheduler.deliverDue()
        assertEquals(listOf("nu"), notifier.sent)
    }
}
