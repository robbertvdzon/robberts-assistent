package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.notes.NotesService
import nl.vdzon.robbertsassistent.reminders.Reminder
import nl.vdzon.robbertsassistent.reminders.RemindersService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * AI-samenvattingssectie: "wat moet ik komende week écht doen?", op basis van de openstaande
 * reminders + Robberts notitie. Faalt stil naar een neutrale placeholder-tekst bij een AI-fout
 * (zelfde beschermende patroon als `AssistantService.updateMemoryFromExchange`).
 */
@Component
class WeekTasksSectionProvider(
    private val remindersService: RemindersService,
    private val notesService: NotesService,
    @Qualifier("weekTasksChatClient") private val chatClient: ChatClient,
) : BriefingSectionProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val order = 20

    override fun section(): BriefingSection {
        val reminders = remindersService.list().filter { it.active }
        val note = notesService.current()
        val text = runCatching { summarize(reminders, note) }
            .getOrElse {
                logger.warn("Weektaken-AI-samenvatting mislukt", it)
                "Kon de weektaken-samenvatting niet ophalen."
            }
        return BriefingSection(key = "week-tasks", title = "Deze week", text = text)
    }

    override fun shortSummary(): String {
        val count = remindersService.list().count { it.active }
        return "$count ${if (count == 1) "taak" else "taken"}"
    }

    private fun summarize(reminders: List<Reminder>, note: String): String {
        val remindersText = if (reminders.isEmpty()) {
            "(geen openstaande reminders)"
        } else {
            reminders.joinToString("\n") { "- ${it.message} (${it.dueAt})" }
        }
        val noteText = note.ifBlank { "(geen notitie)" }
        val prompt = "Reminders:\n$remindersText\n\nNotitie:\n$noteText"
        return chatClient.prompt().user(prompt).call().content()?.trim()?.takeIf { it.isNotBlank() }
            ?: "Er staat niets dringends op de planning."
    }
}
