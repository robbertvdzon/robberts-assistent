package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.notes.InMemoryNotesRepository
import nl.vdzon.robbertsassistent.notes.NotesService
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesToolsTest {
    private val tools = NotesTools(NotesService(InMemoryNotesRepository()))

    @Test
    fun `getNotes meldt expliciet dat de notitie leeg is`() {
        assertEquals("(de notitie is leeg)", tools.getNotes())
    }

    @Test
    fun `updateNotes overschrijft en getNotes leest terug`() {
        tools.updateNotes("Boodschappen: melk, eieren")

        assertEquals("Boodschappen: melk, eieren", tools.getNotes())
    }
}
