package nl.vdzon.robbertsassistent.seed

import nl.vdzon.robbertsassistent.alarms.Alarm
import nl.vdzon.robbertsassistent.alarms.AlarmRepository
import nl.vdzon.robbertsassistent.assistant.ConversationMessage
import nl.vdzon.robbertsassistent.assistant.ConversationRepository
import nl.vdzon.robbertsassistent.assistant.MemoryRepository
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notes.NotesRepository
import nl.vdzon.robbertsassistent.reminders.Reminder
import nl.vdzon.robbertsassistent.reminders.ReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Vult de in-memory opslag bij startup met verzonnen testdata, alleen als RA_SEED_DATA=true (staat
 * aan in deploy/overlays/preview). Bewust GEEN echte productiedata: preview-pods hebben geen
 * Google-login (RA_PREVIEW_SKIP_GOOGLE_AUTH) en een publieke URL zonder vaste host.
 */
@Component
class SeedDataRunner(
    private val secrets: AppSecrets,
    @Qualifier("assistantConversationRepository") private val conversations: ConversationRepository,
    @Qualifier("assistantMemoryRepository") private val memory: MemoryRepository,
    private val notes: NotesRepository,
    private val alarms: AlarmRepository,
    private val reminders: ReminderRepository,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!secrets.seedData) return
        logger.info("RA_SEED_DATA=true: synthetische testdata laden")

        seedConversation()
        memory.update("Robbert houdt van tuinieren en fietsen. (Voorbeeld-geheugen uit de seed-data.)")
        val note = notes.createDocument("Todo (seed data)", 0)
        notes.updateText(note.id, "- Boodschappen: melk, brood\n- Bel de tandarts terug\n(Voorbeeldnotitie uit de seed-data.)")
        alarms.save(
            Alarm(
                id = "seed-alarm-1",
                message = "Wakker worden (seed data)",
                time = Instant.now().plus(1, ChronoUnit.DAYS),
            ),
        )
        reminders.save(
            Reminder(
                id = "seed-reminder-1",
                message = "Planten water geven (seed data)",
                dueAt = Instant.now().plus(2, ChronoUnit.HOURS),
            ),
        )
    }

    private fun seedConversation() {
        val now = Instant.now()
        val conversation = conversations.create()
        conversations.save(
            conversation.copy(
                title = "Testgesprek (seed data)",
                messages = listOf(
                    ConversationMessage(
                        id = "seed-msg-1",
                        role = ConversationMessage.ROLE_USER,
                        text = "Wat staat er op mijn agenda vandaag?",
                        createdAt = now.minus(2, ChronoUnit.MINUTES),
                    ),
                    ConversationMessage(
                        id = "seed-msg-2",
                        role = ConversationMessage.ROLE_ASSISTANT,
                        text = "Dit is een voorbeeldantwoord uit de seed-data van de preview-omgeving.",
                        createdAt = now.minus(1, ChronoUnit.MINUTES),
                    ),
                ),
                updatedAt = now,
            ),
        )
    }
}
