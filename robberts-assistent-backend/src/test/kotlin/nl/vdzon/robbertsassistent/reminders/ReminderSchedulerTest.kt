package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.notifier.Notifier
import nl.vdzon.robbertsassistent.push.InMemoryFcmTokenStore
import nl.vdzon.robbertsassistent.push.PushService
import nl.vdzon.robbertsassistent.scheduling.Recurrence
import nl.vdzon.robbertsassistent.scheduling.RecurrenceUnit
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSchedulerTest {

    private class RecordingNotifier : Notifier {
        val sent = mutableListOf<String>()
        override fun send(message: String) { sent += message }
    }

    // PushService zonder Firebase-config → sendToAll is een no-op (return 0), prima voor de test.
    private fun pushService() = PushService(
        FirebaseProvider(AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("robbert@vdzon.com"))),
        InMemoryFcmTokenStore(),
    )

    @Test
    fun `eenmalige reminder gaat af, wordt afgeleverd en daarna inactief`() {
        val service = RemindersService(InMemoryReminderRepository())
        val notifier = RecordingNotifier()
        val scheduler = ReminderScheduler(service, pushService(), notifier)
        service.create("nu", Instant.now().minusSeconds(1))
        service.create("straks", Instant.now().plusSeconds(600))

        scheduler.deliverDue()

        assertEquals(1, notifier.sent.size)
        assertTrue(notifier.sent.first().contains("nu"))
        // De eenmalige is nu inactief; de toekomstige nog actief.
        val byMessage = service.list().associateBy { it.message }
        assertFalse(byMessage.getValue("nu").active)
        assertTrue(byMessage.getValue("straks").active)
        // Tweede tick: niets meer due.
        scheduler.deliverDue()
        assertEquals(1, notifier.sent.size)
    }

    @Test
    fun `herhalende reminder schuift door en blijft actief`() {
        val service = RemindersService(InMemoryReminderRepository())
        val scheduler = ReminderScheduler(service, pushService(), RecordingNotifier())
        val past = Instant.now().minusSeconds(1)
        service.create("btw", past, Recurrence(RecurrenceUnit.MONTHS, 3))

        scheduler.deliverDue()

        val reminder = service.list().single()
        assertTrue(reminder.active)
        assertTrue(reminder.dueAt.isAfter(Instant.now())) // doorgeschoven naar de toekomst
    }
}
