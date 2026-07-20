package nl.vdzon.robbertsassistent.nightlychecks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightlyChecksServiceTest {

    private class FixedCheck(override val id: String, private val result: CheckResult) : NightlyCheck {
        override val name = "Naam van $id"
        override val description = "Omschrijving van $id"
        override val cronSchedule = "0 0 7 * * *"
        override fun run(): CheckResult = result
    }

    @Test
    fun `list geeft alle checks terug, met null lastRun als er nog niets gedraaid heeft`() {
        val check = FixedCheck("check-a", CheckResult(true, "ok"))
        val repository = InMemoryNightlyCheckRepository()
        val service = NightlyChecksService(listOf(check), repository, NightlyCheckScheduler(listOf(check), repository))

        val statuses = service.list()

        assertEquals(1, statuses.size)
        assertEquals("check-a", statuses[0].id)
        assertNull(statuses[0].lastRun)
    }

    @Test
    fun `runNow draait de check en slaat 'm op, history geeft 'm terug`() {
        val check = FixedCheck("check-a", CheckResult(true, "ok"))
        val repository = InMemoryNightlyCheckRepository()
        val service = NightlyChecksService(listOf(check), repository, NightlyCheckScheduler(listOf(check), repository))

        val run = service.runNow("check-a")

        assertEquals("ok", run?.result?.summary)
        assertEquals("ok", service.list().first().lastRun?.result?.summary)
        assertEquals(1, service.history("check-a", limit = 10)?.size)
    }

    @Test
    fun `runNow en history geven null terug voor een onbekende check-id`() {
        val repository = InMemoryNightlyCheckRepository()
        val service = NightlyChecksService(emptyList(), repository, NightlyCheckScheduler(emptyList(), repository))

        assertNull(service.runNow("onbekend"))
        assertNull(service.history("onbekend", limit = 10))
    }
}
