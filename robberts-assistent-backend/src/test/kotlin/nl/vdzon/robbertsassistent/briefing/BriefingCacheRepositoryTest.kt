package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BriefingCacheRepositoryTest {

    @Test
    fun `current geeft null terug zonder eerdere store`() {
        val repo = InMemoryBriefingCacheRepository()

        assertNull(repo.current())
    }

    @Test
    fun `store slaat de briefing op en current geeft die terug`() {
        val repo = InMemoryBriefingCacheRepository()
        val response = BriefingResponse(sections = listOf(BriefingSection(key = "a", title = "a", text = "a")), updatedAt = "2026-07-21T17:30:00Z")

        repo.store(response)

        assertEquals(response, repo.current())
    }

    @Test
    fun `store overschrijft de vorige cache volledig`() {
        val repo = InMemoryBriefingCacheRepository()
        repo.store(BriefingResponse(sections = listOf(BriefingSection(key = "oud", title = "oud", text = "oud")), updatedAt = "oud"))

        val nieuw = BriefingResponse(sections = listOf(BriefingSection(key = "nieuw", title = "nieuw", text = "nieuw")), updatedAt = "nieuw")
        repo.store(nieuw)

        assertEquals(nieuw, repo.current())
    }
}
