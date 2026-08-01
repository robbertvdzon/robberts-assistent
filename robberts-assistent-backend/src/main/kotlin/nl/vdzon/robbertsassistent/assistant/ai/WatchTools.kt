package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.watches.Watch
import nl.vdzon.robbertsassistent.watches.WatchService
import nl.vdzon.robbertsassistent.watches.WatchValidationException
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot de langdurige zoekopdrachten (watches): opsommen, aanmaken
 * en aanpassen. Verwijderen kan bewust niet via de chat — dat gaat via het Zoekopdrachten-scherm.
 */
@Component
class WatchTools(private val watchService: WatchService) {

    @Tool(
        description = "Som Robberts langdurige zoekopdrachten (watches) op: titel, webadres, " +
            "zoekinstructie, status, of 'ie nog actief is en wanneer 'ie voor het laatst " +
            "gecontroleerd is.",
    )
    fun listWatches(): String {
        val watches = watchService.list()
        if (watches.isEmpty()) return "Er lopen op dit moment geen zoekopdrachten."
        return watches.joinToString("\n") { describe(it) }
    }

    @Tool(
        description = "Maak een nieuwe langdurige zoekopdracht (watch) aan: een webpagina die " +
            "elk uur overdag gecontroleerd wordt op iets wat Robbert zoekt. Zodra het gevonden is, " +
            "stopt de zoekopdracht en krijgt hij (optioneel) een push-notificatie.",
    )
    fun createWatch(
        @ToolParam(description = "Korte titel van de zoekopdracht") title: String,
        @ToolParam(description = "Het volledige webadres (http(s)) van de te controleren pagina") url: String,
        @ToolParam(description = "Waar op de pagina naar gezocht moet worden, in gewone taal") instruction: String,
        @ToolParam(description = "Push-notificatie sturen zodra het gevonden is (standaard ja)", required = false)
        notifyOnFound: Boolean = true,
    ): String {
        val watch = try {
            watchService.create(title, url, instruction, notifyOnFound)
        } catch (e: WatchValidationException) {
            return "Zoekopdracht niet aangemaakt: ${e.message}"
        }
        return "Zoekopdracht \"${watch.title}\" aangemaakt voor ${watch.url} " +
            "(elk uur overdag, ${notifyText(watch.notifyOnFound)}) [id ${watch.id.take(8)}]."
    }

    @Tool(
        description = "Pas een bestaande langdurige zoekopdracht (watch) aan op basis van het " +
            "(begin van het) id — gebruik eerst listWatches om het id te vinden. Geef alleen de " +
            "velden mee die moeten wijzigen; de overige velden blijven zoals ze waren.",
    )
    fun updateWatch(
        @ToolParam(description = "Het id (of het begin ervan) van de aan te passen zoekopdracht") id: String,
        @ToolParam(description = "Nieuwe titel (leeg = niet wijzigen)", required = false)
        title: String = "",
        @ToolParam(description = "Nieuw webadres (leeg = niet wijzigen)", required = false)
        url: String = "",
        @ToolParam(description = "Nieuwe zoekinstructie (leeg = niet wijzigen)", required = false)
        instruction: String = "",
        @ToolParam(
            description = "Push-notificatie sturen zodra het gevonden is: 'ja' of 'nee' " +
                "(leeg = niet wijzigen)",
            required = false,
        )
        notifyOnFound: String = "",
    ): String {
        val match = watchService.list().firstOrNull { it.id.startsWith(id) }
            ?: return "Geen zoekopdracht gevonden met id $id."

        val updated = try {
            watchService.update(
                id = match.id,
                title = title.ifBlank { match.title },
                url = url.ifBlank { match.url },
                instruction = instruction.ifBlank { match.instruction },
                notifyOnFound = parseNotify(notifyOnFound) ?: match.notifyOnFound,
            )
        } catch (e: WatchValidationException) {
            return "Zoekopdracht niet aangepast: ${e.message}"
        }
        return "Zoekopdracht \"${updated.title}\" aangepast (${updated.url}, " +
            "elk uur overdag, ${notifyText(updated.notifyOnFound)}). " +
            "De zoekopdracht staat weer op actief en wordt opnieuw gecontroleerd."
    }

    private fun describe(watch: Watch): String {
        val laatst = watch.lastCheckedAt?.let { FORMATTER.format(it) } ?: "nog niet gecontroleerd"
        return "- ${watch.title} (${watch.url}) — zoekt naar: ${watch.instruction} — " +
            "elk uur overdag — status ${watch.status.name}: ${watch.statusDescription} — " +
            "${if (watch.active) "actief" else "niet actief"} — laatst gecontroleerd: $laatst " +
            "[id ${watch.id.take(8)}]"
    }

    /** Driewaardig: null betekent "niet meegegeven", dus niet wijzigen. */
    private fun parseNotify(text: String): Boolean? =
        when (text.trim().lowercase()) {
            "ja", "true", "yes", "1" -> true
            "nee", "false", "no", "0" -> false
            else -> null
        }

    private fun notifyText(notifyOnFound: Boolean): String =
        if (notifyOnFound) "met push-notificatie" else "zonder push-notificatie"

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }
}
