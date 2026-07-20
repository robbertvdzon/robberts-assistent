package nl.vdzon.robbertsassistent.assistant

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryRepositoryTest {

    @Test
    fun `current geeft lege tekst terug zonder eerdere update`() {
        val repo = InMemoryMemoryRepository()

        assertEquals("", repo.current())
    }

    @Test
    fun `update slaat de volledige tekst op en current geeft die terug`() {
        val repo = InMemoryMemoryRepository()

        val result = repo.update("houdt van vissen")

        assertEquals("houdt van vissen", result)
        assertEquals("houdt van vissen", repo.current())
    }

    @Test
    fun `update overschrijft de vorige tekst volledig`() {
        val repo = InMemoryMemoryRepository()
        repo.update("oud")

        repo.update("nieuw")

        assertEquals("nieuw", repo.current())
    }
}
