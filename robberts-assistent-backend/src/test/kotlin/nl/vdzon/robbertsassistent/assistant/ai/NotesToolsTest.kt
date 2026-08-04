package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.notes.DEFAULT_DOCUMENT_ID
import nl.vdzon.robbertsassistent.notes.InMemoryNotesRepository
import nl.vdzon.robbertsassistent.notes.NotesService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesToolsTest {
    private val service = NotesService(InMemoryNotesRepository())
    private val tools = NotesTools(service)

    @Test
    fun `getNotes meldt expliciet dat de notitie leeg is`() {
        assertEquals("(de notitie is leeg)", tools.getNotes())
    }

    @Test
    fun `updateNotes overschrijft en getNotes leest terug`() {
        tools.updateNotes("Boodschappen: melk, eieren")

        assertEquals("Boodschappen: melk, eieren", tools.getNotes())
    }

    @Test
    fun `listNoteDocuments somt de titels in volgorde op`() {
        service.createDocument("recepten")

        assertEquals("Notitiedocumenten (in volgorde): todo, recepten.", tools.listNoteDocuments())
    }

    @Test
    fun `zonder titel werken de tools op het standaarddocument`() {
        tools.updateNoteDocument(text = "todo-tekst", title = null)

        assertEquals("Notitiedocument 'todo':\ntodo-tekst", tools.getNoteDocument(null))
        assertEquals("todo-tekst", service.document(DEFAULT_DOCUMENT_ID).text)
    }

    @Test
    fun `een document is op naam te lezen en te overschrijven, hoofdletter-ongevoelig`() {
        val recepten = service.createDocument("Recepten")

        assertEquals(
            "Notitiedocument 'Recepten' bijgewerkt.",
            tools.updateNoteDocument(text = "pannenkoeken", title = "recepten"),
        )
        assertEquals("Notitiedocument 'Recepten':\npannenkoeken", tools.getNoteDocument("RECEPTEN"))
        assertEquals("pannenkoeken", service.document(recepten.id).text)
        // Het standaarddocument is niet geraakt.
        assertEquals("", service.document(DEFAULT_DOCUMENT_ID).text)
    }

    @Test
    fun `een beginstuk van de titel volstaat als het er maar één is`() {
        service.createDocument("recepten")

        assertEquals("Notitiedocument 'recepten':\n(dit document is leeg)", tools.getNoteDocument("rec"))
    }

    @Test
    fun `een onbekende naam geeft een nette foutzin met de beschikbare titels`() {
        service.createDocument("recepten")

        val antwoord = tools.getNoteDocument("boodschappen")

        assertTrue(antwoord.startsWith("Geen notitiedocument gevonden"), antwoord)
        assertTrue(antwoord.contains("todo, recepten"), antwoord)
    }

    @Test
    fun `een ambigue naam geeft een nette foutzin met de beschikbare titels`() {
        service.createDocument("recepten")
        service.createDocument("recepten-oud")

        val antwoord = tools.getNoteDocument("recepten-")
        assertTrue(antwoord.startsWith("Notitiedocument 'recepten-oud'"), antwoord)

        val ambigu = tools.getNoteDocument("recept")
        assertTrue(ambigu.startsWith("Meerdere notitiedocumenten passen bij"), ambigu)
        assertTrue(ambigu.contains("recepten, recepten-oud"), ambigu)
    }

    @Test
    fun `een exacte titel wint van een beginstuk-match`() {
        service.createDocument("recepten")
        service.createDocument("recepten-oud")

        assertEquals("Notitiedocument 'recepten':\n(dit document is leeg)", tools.getNoteDocument("recepten"))
    }

    @Test
    fun `createNoteDocument maakt een leeg document aan`() {
        assertEquals(
            "Notitiedocument 'klussen' aangemaakt; het is nog leeg.",
            tools.createNoteDocument("klussen"),
        )
        assertEquals(listOf("todo", "klussen"), service.documents().map { it.title })
    }

    @Test
    fun `een ongeldige of dubbele titel levert een foutzin op, geen exception`() {
        assertTrue(tools.createNoteDocument("  ").startsWith("Dat lukt niet:"), tools.createNoteDocument("  "))

        tools.createNoteDocument("klussen")
        val dubbel = tools.createNoteDocument("KLUSSEN")
        assertTrue(dubbel.startsWith("Dat lukt niet:"), dubbel)
        assertEquals(listOf("todo", "klussen"), service.documents().map { it.title })
    }
}
