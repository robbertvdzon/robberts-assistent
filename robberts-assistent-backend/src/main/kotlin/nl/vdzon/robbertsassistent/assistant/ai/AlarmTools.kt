package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.alarms.AlarmsService
import nl.vdzon.robbertsassistent.scheduling.Recurrence
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot alarms. Anders dan een reminder (push-notificatie) is een
 * alarm een echt telefoon-alarm dat afgaat (geluid, over het lockscreen). De app plant ze lokaal.
 */
@Component
class AlarmTools(private val alarmsService: AlarmsService) {

    @Tool(
        description = "Zet een alarm dat op de telefoon afgaat (geluid, wekker). minutesFromNow bepaalt " +
            "wanneer 'ie (de eerste keer) afgaat. Voor een HERHALEND alarm: everyUnit (dag/week/maand/jaar) " +
            "+ everyInterval (bv. elke dag = everyUnit 'dag', everyInterval 1). everyInterval 0 = eenmalig. " +
            "Gebruik dit voor 'alarm'/'wekker'; gebruik een reminder als het alleen een melding hoeft te zijn.",
    )
    fun setAlarm(
        @ToolParam(description = "De tekst/het label van het alarm") message: String,
        @ToolParam(description = "Over hoeveel minuten het alarm (de eerste keer) afgaat") minutesFromNow: Int,
        @ToolParam(description = "Herhaal-eenheid: dag, week, maand of jaar (leeg = eenmalig)", required = false)
        everyUnit: String = "",
        @ToolParam(description = "Herhaal elke N (bv. 1); 0 = eenmalig", required = false)
        everyInterval: Int = 0,
    ): String {
        val time = Instant.now().plus(Duration.ofMinutes(minutesFromNow.toLong()))
        val unit = Recurrence.unitFromText(everyUnit)
        val recurrence = if (everyInterval > 0 && unit != null) Recurrence(unit, everyInterval) else null
        alarmsService.create(message, time, recurrence)
        val herhaling = recurrence?.let { " (herhaalt elke ${it.interval} ${everyUnit})" } ?: ""
        return "Alarm gezet over $minutesFromNow minuten: \"$message\"$herhaling. " +
            "(Gaat af op de telefoon zodra de app gesynct heeft.)"
    }

    @Tool(description = "Som Robberts actieve alarms op.")
    fun listAlarms(): String {
        val active = alarmsService.list().filter { it.active }
        if (active.isEmpty()) return "Er staan geen alarms."
        return active.joinToString("\n") {
            val herhaling = it.recurrence?.let { r -> " — elke ${r.interval} ${r.unit.name.lowercase()}" } ?: ""
            "- ${it.message} (${FORMATTER.format(it.time)})$herhaling [id ${it.id.take(8)}]"
        }
    }

    @Tool(description = "Verwijder een alarm op basis van het (begin van het) id.")
    fun deleteAlarm(
        @ToolParam(description = "Het id (of het begin ervan) van het te verwijderen alarm") id: String,
    ): String {
        val match = alarmsService.list().firstOrNull { it.id.startsWith(id) }
            ?: return "Geen alarm gevonden met id $id."
        alarmsService.delete(match.id)
        return "Alarm verwijderd: \"${match.message}\"."
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }
}
