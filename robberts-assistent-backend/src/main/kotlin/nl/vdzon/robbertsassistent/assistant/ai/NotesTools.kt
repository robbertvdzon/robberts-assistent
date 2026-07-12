package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.notes.NotesService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** Geeft de chat-assistent lees-/schrijftoegang tot Robberts ene notitie (zie [NotesService]). */
@Component
class NotesTools(private val notesService: NotesService) {

    @Tool(description = "Haal de huidige inhoud van Robberts notitie op.")
    fun getNotes(): String = notesService.current().ifBlank { "(de notitie is leeg)" }

    @Tool(
        description = "Overschrijft Robberts notitie met nieuwe tekst. Dit VERVANGT de volledige " +
            "inhoud — als je iets wilt toevoegen i.p.v. vervangen, haal eerst de huidige notitie op " +
            "met getNotes en stuur de samengevoegde tekst mee.",
    )
    fun updateNotes(
        @ToolParam(description = "De volledige nieuwe inhoud van de notitie") text: String,
    ): String {
        notesService.update(text)
        return "Notitie bijgewerkt."
    }
}
