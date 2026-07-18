package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.reminders.RemindersService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot reminders: zetten (die op tijd een push/alarm geven) en
 * opsommen. Via deze tool test je de keten reminder -> scheduler -> Notifier met natuurlijke taal
 * ("stuur me over 10 minuten een push dat ...").
 */
@Component
class ReminderTools(private val remindersService: RemindersService) {

    @Tool(
        description = "Zet een reminder die over een aantal minuten afgaat (push/alarm). Gebruik dit " +
            "als Robbert vraagt om over X minuten/straks een bericht, push of herinnering te krijgen.",
    )
    fun createReminderInMinutes(
        @ToolParam(description = "De tekst van de reminder/het bericht") message: String,
        @ToolParam(description = "Over hoeveel minuten de reminder moet afgaan") minutesFromNow: Int,
    ): String {
        val dueAt = Instant.now().plus(Duration.ofMinutes(minutesFromNow.toLong()))
        remindersService.create(message, dueAt)
        return "Reminder gezet over $minutesFromNow minuten: \"$message\"."
    }

    @Tool(description = "Som Robberts openstaande (nog niet afgegane) reminders op.")
    fun listOpenReminders(): String {
        val open = remindersService.list().filter { !it.delivered }
        if (open.isEmpty()) return "Er staan geen reminders open."
        return open.joinToString("\n") { "- ${it.message} (om ${FORMATTER.format(it.dueAt)})" }
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }
}
