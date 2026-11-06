package nl.vdzon.robbertsassistent.seed

import nl.vdzon.robbertsassistent.alarms.InMemoryAlarmRepository
import nl.vdzon.robbertsassistent.assistant.InMemoryConversationRepository
import nl.vdzon.robbertsassistent.assistant.InMemoryMemoryRepository
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notes.InMemoryNotesRepository
import nl.vdzon.robbertsassistent.reminders.InMemoryReminderRepository
import org.springframework.boot.DefaultApplicationArguments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataRunnerTest {

    private fun secrets(seedData: Boolean) = AppSecrets(
        rememberSecret = "x",
        googleClientId = "x",
        allowedEmails = setOf("robbert@vdzon.com"),
        seedData = seedData,
    )

    @Test
    fun `zonder RA_SEED_DATA blijft alles leeg`() {
        val conversations = InMemoryConversationRepository()
        val alarms = InMemoryAlarmRepository()
        val runner = SeedDataRunner(
            secrets(seedData = false),
            conversations,
            InMemoryMemoryRepository(),
            InMemoryNotesRepository(),
            alarms,
            InMemoryReminderRepository(),
        )

        runner.run(DefaultApplicationArguments())

        assertTrue(conversations.listAll().isEmpty())
        assertTrue(alarms.all().isEmpty())
    }

    @Test
    fun `met RA_SEED_DATA=true wordt de opslag gevuld`() {
        val conversations = InMemoryConversationRepository()
        val memory = InMemoryMemoryRepository()
        val notes = InMemoryNotesRepository()
        val alarms = InMemoryAlarmRepository()
        val reminders = InMemoryReminderRepository()
        val runner = SeedDataRunner(secrets(seedData = true), conversations, memory, notes, alarms, reminders)

        runner.run(DefaultApplicationArguments())

        val conversation = conversations.listAll().singleOrNull()
        assertEquals(2, conversation?.messages?.size)
        assertTrue(memory.current().isNotBlank())
        assertTrue(notes.documents().any { it.text.isNotBlank() })
        assertEquals(1, alarms.all().size)
        assertEquals(1, reminders.all().size)
    }
}
