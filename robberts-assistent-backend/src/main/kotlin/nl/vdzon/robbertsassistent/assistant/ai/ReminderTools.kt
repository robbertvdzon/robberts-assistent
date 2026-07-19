package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.reminders.RemindersService
import nl.vdzon.robbertsassistent.scheduling.Recurrence
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot reminders (push-notificatie op tijd; eenmalig of herhalend).
 * Test de keten reminder -> scheduler -> FCM-push met natuurlijke taal.
 */
@Component
class ReminderTools(private val remindersService: RemindersService) {

    @Tool(
        description = "Zet een reminder die op een tijdstip een push-notificatie naar Robberts telefoon " +
            "geeft. minutesFromNow bepaalt wanneer 'ie (de eerste keer) afgaat. Voor een HERHALENDE " +
            "reminder: geef everyUnit (dag/week/maand/jaar) én everyInterval (bv. elke 3 maanden = " +
            "everyUnit 'maand', everyInterval 3). Laat everyInterval 0 voor een eenmalige reminder.",
    )
    fun createReminder(
        @ToolParam(description = "De tekst van de reminder") message: String,
        @ToolParam(description = "Over hoeveel minuten de reminder (de eerste keer) afgaat") minutesFromNow: Int,
        @ToolParam(description = "Herhaal-eenheid: dag, week, maand of jaar (leeg = eenmalig)", required = false)
        everyUnit: String = "",
        @ToolParam(description = "Herhaal elke N (bv. 3); 0 = eenmalig", required = false)
        everyInterval: Int = 0,
    ): String {
        val dueAt = Instant.now().plus(Duration.ofMinutes(minutesFromNow.toLong()))
        val unit = Recurrence.unitFromText(everyUnit)
        val recurrence = if (everyInterval > 0 && unit != null) Recurrence(unit, everyInterval) else null
        remindersService.create(message, dueAt, recurrence)
        val herhaling = recurrence?.let { " (herhaalt elke ${it.interval} ${everyUnit})" } ?: ""
        return "Reminder gezet over $minutesFromNow minuten: \"$message\"$herhaling."
    }

    @Tool(description = "Som Robberts actieve reminders op.")
    fun listReminders(): String {
        val active = remindersService.list().filter { it.active }
        if (active.isEmpty()) return "Er staan geen reminders open."
        return active.joinToString("\n") {
            val herhaling = it.recurrence?.let { r -> " — elke ${r.interval} ${r.unit.name.lowercase()}" } ?: ""
            "- ${it.message} (${FORMATTER.format(it.dueAt)})$herhaling [id ${it.id.take(8)}]"
        }
    }

    @Tool(description = "Verwijder een reminder op basis van het (begin van het) id.")
    fun deleteReminder(
        @ToolParam(description = "Het id (of het begin ervan) van de te verwijderen reminder") id: String,
    ): String {
        val match = remindersService.list().firstOrNull { it.id.startsWith(id) }
            ?: return "Geen reminder gevonden met id $id."
        remindersService.delete(match.id)
        return "Reminder verwijderd: \"${match.message}\"."
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }
}
