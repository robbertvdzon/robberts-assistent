package nl.vdzon.robbertsassistent.summary

import nl.vdzon.robbertsassistent.nightlychecks.CheckResult
import nl.vdzon.robbertsassistent.nightlychecks.InMemoryNightlyCheckRepository
import nl.vdzon.robbertsassistent.nightlychecks.NightlyCheck
import nl.vdzon.robbertsassistent.nightlychecks.NightlyCheckScheduler
import nl.vdzon.robbertsassistent.nightlychecks.NightlyChecksService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryServiceTest {

    private class StubCheck : NightlyCheck {
        override val id = "stub-check"
        override val name = "Stub-check"
        override val description = "Testcheck"
        override val cronSchedule = "0 0 7 * * *"
        override fun run() = CheckResult(ok = true, summary = "alles gezond (stub)")
    }

    private fun nightlyChecksService(checks: List<NightlyCheck>): Pair<NightlyChecksService, NightlyCheckScheduler> {
        val repository = InMemoryNightlyCheckRepository()
        val scheduler = NightlyCheckScheduler(checks, repository)
        return NightlyChecksService(checks, repository, scheduler) to scheduler
    }

    @Test
    fun `bevat de basisitems plus het nightly-check-resultaat, na zonnepanelen`() {
        val checks = listOf(StubCheck())
        val (service, scheduler) = nightlyChecksService(checks)
        scheduler.runAndStore(checks.first())

        val items = SummaryService(service).current().items

        assertEquals(listOf("wind", "moestuin", "backups", "stub-check", "zonnepanelen"), items.map { it.key })
        items.forEach { assertTrue(it.title.isNotBlank() && it.text.isNotBlank()) }
        assertEquals("alles gezond (stub)", items.first { it.key == "stub-check" }.text)
    }

    @Test
    fun `toont 'nog niet gedraaid' als een check nog geen resultaat heeft`() {
        val (service, _) = nightlyChecksService(listOf(StubCheck()))

        val items = SummaryService(service).current().items

        assertEquals("Nog niet gedraaid.", items.first { it.key == "stub-check" }.text)
    }
}
