package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import nl.vdzon.robbertsassistent.reminders.Reminder
import nl.vdzon.robbertsassistent.reminders.RemindersService
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Agenda-briefingsectie: afspraken over de komende 7 dagen, over alle agenda's van de gebruiker
 * (zie [CalendarClient.eventsInRange]), oplopend op tijd. Per afspraak wordt bepaald of er al een
 * reminder ~1 uur van tevoren staat.
 *
 * De story omschrijft dit als een "AI-bepaling (op basis van tijd + tekst)". Voor determinisme
 * (vereist onder `RA_MOCK_AI`/preview, zie [nl.vdzon.robbertsassistent.config.AppSecrets]) en
 * testbaarheid is dit geïmplementeerd als een expliciete, tijd-gebaseerde heuristiek
 * ([hasReminderFor]) in plaats van een echte AI-aanroep per afspraak — een reminder "hoort erbij"
 * als 'ie 30-90 minuten vóór de afspraak afgaat, ongeacht exacte tekstmatch (tekst is voor een mens
 * op het scherm toch leidend). Zie de story-worklog voor deze afweging.
 */
@Component
class AgendaSectionProvider(
    private val calendarClient: CalendarClient,
    private val remindersService: RemindersService,
) : BriefingSectionProvider {

    override val order = 10

    override fun section(): BriefingSection {
        val now = Instant.now()
        val events = calendarClient.eventsInRange(now, now.plus(Duration.ofDays(7))).sortedBy { it.start }
        if (events.isEmpty()) {
            return BriefingSection(key = "agenda", title = "Agenda (7 dagen)", text = "Geen afspraken de komende 7 dagen.")
        }
        val reminders = remindersService.list()
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm").withZone(ZONE)
        val text = events.joinToString("\n") { event ->
            val status = if (hasReminderFor(event, reminders)) "✅ reminder staat" else "⚠️ nog geen reminder"
            "${formatter.format(event.start)} — ${event.summary} ($status)"
        }
        return BriefingSection(key = "agenda", title = "Agenda (7 dagen)", text = text)
    }

    override fun shortSummary(): String {
        val now = Instant.now()
        val count = calendarClient.eventsInRange(now, now.plus(Duration.ofDays(7))).size
        return "$count afspra${if (count == 1) "ak" else "ken"}"
    }

    internal companion object {
        private val ZONE = ZoneId.of("Europe/Amsterdam")

        // ~1 uur van tevoren, met marge (30-90 min) voor een reminder die niet exact op 60 minuten
        // gezet is.
        private val REMINDER_WINDOW_MIN = Duration.ofMinutes(30)
        private val REMINDER_WINDOW_MAX = Duration.ofMinutes(90)

        internal fun hasReminderFor(event: CalendarEvent, reminders: List<Reminder>): Boolean =
            reminders.any { reminder ->
                if (!reminder.active) return@any false
                val leadTime = Duration.between(reminder.dueAt, event.start)
                !leadTime.isNegative && leadTime >= REMINDER_WINDOW_MIN && leadTime <= REMINDER_WINDOW_MAX
            }
    }
}
