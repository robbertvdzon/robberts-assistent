package nl.vdzon.robbertsassistent.nightlychecks

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryNightlyCheckRepositoryTest {

    @Test
    fun `latest en history geven null resp leeg terug als er nog niets is opgeslagen`() {
        val repository = InMemoryNightlyCheckRepository()

        assertNull(repository.latest("onbekend"))
        assertEquals(emptyList(), repository.history("onbekend"))
    }

    @Test
    fun `history staat nieuwste eerst en respecteert de limit`() {
        val repository = InMemoryNightlyCheckRepository()
        val base = Instant.parse("2026-07-19T00:00:00Z")
        (1..5).forEach { i ->
            repository.save(CheckRun("check-a", base.plusSeconds(i.toLong()), CheckResult(true, "run $i")))
        }

        val history = repository.history("check-a", limit = 3)

        assertEquals(listOf("run 5", "run 4", "run 3"), history.map { it.result.summary })
        assertEquals("run 5", repository.latest("check-a")?.result?.summary)
    }

    @Test
    fun `houdt runs van verschillende checks apart`() {
        val repository = InMemoryNightlyCheckRepository()
        repository.save(CheckRun("check-a", Instant.now(), CheckResult(true, "a")))
        repository.save(CheckRun("check-b", Instant.now(), CheckResult(true, "b")))

        assertEquals("a", repository.latest("check-a")?.result?.summary)
        assertEquals("b", repository.latest("check-b")?.result?.summary)
    }

    @Test
    fun `bewaart maximaal 100 runs per check`() {
        val repository = InMemoryNightlyCheckRepository()
        val base = Instant.parse("2026-07-19T00:00:00Z")
        (1..120).forEach { i ->
            repository.save(CheckRun("check-a", base.plusSeconds(i.toLong()), CheckResult(true, "run $i")))
        }

        assertEquals(100, repository.history("check-a", limit = 200).size)
        assertEquals("run 120", repository.latest("check-a")?.result?.summary)
    }
}
