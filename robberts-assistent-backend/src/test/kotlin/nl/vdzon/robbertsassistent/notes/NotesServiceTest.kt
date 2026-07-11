package nl.vdzon.robbertsassistent.notes

import kotlin.test.Test
import kotlin.test.assertEquals

class NotesServiceTest {
    @Test
    fun `starts empty and round-trips an update`() {
        val service = NotesService()
        assertEquals("", service.current())

        service.update("Boodschappen: melk, eieren")
        assertEquals("Boodschappen: melk, eieren", service.current())

        service.update("Overschreven")
        assertEquals("Overschreven", service.current())
    }
}
