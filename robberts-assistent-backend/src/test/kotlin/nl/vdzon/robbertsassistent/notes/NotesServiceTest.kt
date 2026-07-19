package nl.vdzon.robbertsassistent.notes

import kotlin.test.Test
import kotlin.test.assertEquals

class NotesServiceTest {
    private val service = NotesService(InMemoryNotesRepository())

    @Test
    fun `starts empty and round-trips an update`() {
        assertEquals("", service.current())

        service.update("Boodschappen: melk, eieren")
        assertEquals("Boodschappen: melk, eieren", service.current())

        service.update("Overschreven")
        assertEquals("Overschreven", service.current())
    }
}
