package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.notes.NoteDocument
import nl.vdzon.robbertsassistent.notes.NoteDocumentConflictException
import nl.vdzon.robbertsassistent.notes.NoteDocumentNotFoundException
import nl.vdzon.robbertsassistent.notes.NoteTitleInvalidException
import nl.vdzon.robbertsassistent.notes.NotesService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent lees-/schrijftoegang tot Robberts notitiedocumenten (zie [NotesService]).
 * Zonder titel werken de tools op het standaarddocument ('todo').
 */
@Component
class NotesTools(private val notesService: NotesService) {

    @Tool(description = "Haal de huidige inhoud van Robberts notitie (het standaarddocument) op.")
    fun getNotes(): String = notesService.current().ifBlank { "(de notitie is leeg)" }

    @Tool(
        description = "Overschrijft Robberts notitie (het standaarddocument) met nieuwe tekst. Dit " +
            "VERVANGT de volledige inhoud — als je iets wilt toevoegen i.p.v. vervangen, haal eerst " +
            "de huidige notitie op met getNotes en stuur de samengevoegde tekst mee.",
    )
    fun updateNotes(
        @ToolParam(description = "De volledige nieuwe inhoud van de notitie") text: String,
    ): String {
        notesService.update(text)
        return "Notitie bijgewerkt."
    }

    @Tool(description = "Somt de titels van alle notitiedocumenten van Robbert op.")
    fun listNoteDocuments(): String = antwoord {
        val documenten = notesService.documents()
        if (documenten.isEmpty()) {
            "Er zijn nog geen notitiedocumenten."
        } else {
            "Notitiedocumenten (in volgorde): " + titels(documenten) + "."
        }
    }

    @Tool(
        description = "Haalt de inhoud van één notitiedocument op, gezocht op titel. Laat de titel " +
            "weg voor het standaarddocument.",
    )
    fun getNoteDocument(
        @ToolParam(description = "Titel van het document (leeg = standaarddocument)", required = false)
        title: String?,
    ): String = antwoord {
        val document = zoekDocument(title)
        val inhoud = document.text.ifBlank { "(dit document is leeg)" }
        "Notitiedocument '${document.title}':\n$inhoud"
    }

    @Tool(
        description = "Overschrijft de inhoud van één notitiedocument, gezocht op titel (leeg = " +
            "standaarddocument). Dit VERVANGT de volledige inhoud — wil je iets toevoegen, haal dan " +
            "eerst de inhoud op met getNoteDocument en stuur de samengevoegde tekst mee.",
    )
    fun updateNoteDocument(
        @ToolParam(description = "De volledige nieuwe inhoud van het document") text: String,
        @ToolParam(description = "Titel van het document (leeg = standaarddocument)", required = false)
        title: String?,
    ): String = antwoord {
        val document = zoekDocument(title)
        notesService.updateText(document.id, text)
        "Notitiedocument '${document.title}' bijgewerkt."
    }

    @Tool(description = "Maakt een nieuw, leeg notitiedocument met de opgegeven titel aan.")
    fun createNoteDocument(
        @ToolParam(description = "De titel van het nieuwe document") title: String,
    ): String = antwoord {
        val document = notesService.createDocument(title)
        "Notitiedocument '${document.title}' aangemaakt; het is nog leeg."
    }

    /**
     * Zoekt op titel: hoofdletter-ongevoelig, eerst een exacte match, anders titels die met de
     * opgegeven tekst beginnen. Precies één match is nodig; zonder titel het standaarddocument.
     */
    private fun zoekDocument(title: String?): NoteDocument {
        val documenten = notesService.documents()
        val gevraagd = title?.trim().orEmpty()
        if (gevraagd.isEmpty()) return notesService.defaultDocument()
        val exact = documenten.filter { it.title.equals(gevraagd, ignoreCase = true) }
        val matches = exact.ifEmpty {
            documenten.filter { it.title.startsWith(gevraagd, ignoreCase = true) }
        }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw ToolFout(
                "Geen notitiedocument gevonden met de naam '$gevraagd'. Beschikbaar: ${titels(documenten)}.",
            )
            else -> throw ToolFout(
                "Meerdere notitiedocumenten passen bij '$gevraagd'. Beschikbaar: ${titels(documenten)}.",
            )
        }
    }

    private fun titels(documenten: List<NoteDocument>) = documenten.joinToString(", ") { it.title }

    /** Zet elke verwachte fout om in een leesbare Nederlandse zin; nooit een exception naar buiten. */
    private fun antwoord(block: () -> String): String = try {
        block()
    } catch (error: ToolFout) {
        error.message.orEmpty()
    } catch (error: NoteDocumentNotFoundException) {
        "Dat lukt niet: ${error.message}."
    } catch (error: NoteTitleInvalidException) {
        "Dat lukt niet: ${error.message}."
    } catch (error: NoteDocumentConflictException) {
        "Dat lukt niet: ${error.message}."
    }

    private class ToolFout(message: String) : RuntimeException(message)
}
