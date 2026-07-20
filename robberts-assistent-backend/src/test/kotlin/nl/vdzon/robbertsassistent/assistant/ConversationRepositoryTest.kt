package nl.vdzon.robbertsassistent.assistant

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test [InMemoryConversationRepository] rechtstreeks, met expliciete `updatedAt`-tijdstippen zodat
 * sortering/paginatie deterministisch getest kan worden (i.p.v. via [AssistantService], waar
 * `Instant.now()`-timestamps van snel opeenvolgende calls te dicht bij elkaar kunnen liggen).
 */
class ConversationRepositoryTest {
    private fun conversationAt(id: String, updatedAt: Instant, archived: Boolean = false) = Conversation(
        id = id,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        archived = archived,
    )

    @Test
    fun `listAll sorteert op updatedAt aflopend`() {
        val repo = InMemoryConversationRepository()
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repo.save(conversationAt("a", base))
        repo.save(conversationAt("b", base.plusSeconds(60)))
        repo.save(conversationAt("c", base.plusSeconds(30)))

        assertEquals(listOf("b", "c", "a"), repo.listAll().map { it.id })
    }

    @Test
    fun `listAll sluit gearchiveerde gesprekken standaard uit maar includeArchived toont ze`() {
        val repo = InMemoryConversationRepository()
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repo.save(conversationAt("a", base, archived = false))
        repo.save(conversationAt("b", base.plusSeconds(60), archived = true))

        assertEquals(listOf("a"), repo.listAll().map { it.id })
        assertEquals(listOf("b", "a"), repo.listAll(includeArchived = true).map { it.id })
    }

    @Test
    fun `listAll pagineert met limit en offset over het gefilterde resultaat`() {
        val repo = InMemoryConversationRepository()
        val base = Instant.parse("2026-01-01T00:00:00Z")
        (0 until 12).forEach { i -> repo.save(conversationAt("c$i", base.plusSeconds(i.toLong()))) }

        val firstPage = repo.listAll(limit = 10, offset = 0)
        assertEquals(10, firstPage.size)
        assertEquals("c11", firstPage.first().id)
        assertEquals("c2", firstPage.last().id)

        val secondPage = repo.listAll(limit = 10, offset = 10)
        assertEquals(listOf("c1", "c0"), secondPage.map { it.id })
    }

    @Test
    fun `delete verwijdert het gesprek`() {
        val repo = InMemoryConversationRepository()
        val conversation = repo.create()

        repo.delete(conversation.id)

        assertNull(repo.findById(conversation.id))
        assertFalse(repo.listAll(includeArchived = true).any { it.id == conversation.id })
    }

    @Test
    fun `delete van een onbekend gesprek is een no-op`() {
        val repo = InMemoryConversationRepository()

        repo.delete("onbekend")

        assertTrue(repo.listAll().isEmpty())
    }
}
