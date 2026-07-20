package nl.vdzon.robbertsassistent.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryRepositoryTest {

    @Test
    fun `create voegt een item toe en listAll geeft het terug`() {
        val repo = InMemoryMemoryRepository()

        val item = repo.create("houdt van vissen")

        assertEquals(listOf(item.id), repo.listAll().map { it.id })
        assertEquals("houdt van vissen", repo.findById(item.id)?.text)
    }

    @Test
    fun `update wijzigt de tekst van een bestaand item`() {
        val repo = InMemoryMemoryRepository()
        val item = repo.create("oud")

        val updated = repo.update(item.id, "nieuw")

        assertEquals("nieuw", updated?.text)
        assertEquals("nieuw", repo.findById(item.id)?.text)
    }

    @Test
    fun `update geeft null terug voor een onbekend item`() {
        val repo = InMemoryMemoryRepository()

        assertNull(repo.update("onbekend", "iets"))
    }

    @Test
    fun `delete verwijdert een item`() {
        val repo = InMemoryMemoryRepository()
        val item = repo.create("weg ermee")

        repo.delete(item.id)

        assertNull(repo.findById(item.id))
        assertFalse(repo.listAll().any { it.id == item.id })
    }

    @Test
    fun `listAll sorteert op updatedAt aflopend`() {
        val repo = InMemoryMemoryRepository()
        val a = repo.create("a")
        val b = repo.create("b")

        repo.update(a.id, "a bijgewerkt")

        assertEquals(listOf(a.id, b.id), repo.listAll().map { it.id })
    }

    @Test
    fun `delete van een onbekend item is een no-op`() {
        val repo = InMemoryMemoryRepository()

        repo.delete("onbekend")

        assertTrue(repo.listAll().isEmpty())
    }
}
