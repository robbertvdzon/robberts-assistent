package nl.vdzon.robbertsassistent.nightlychecks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightlyCheckSchedulerTest {

    private class FixedCheck(override val id: String, private val result: CheckResult) : NightlyCheck {
        override val name = id
        override val description = id
        override val cronSchedule = "0 0 7 * * *"
        override fun run(): CheckResult = result
    }

    private class ThrowingCheck(override val id: String) : NightlyCheck {
        override val name = id
        override val description = id
        override val cronSchedule = "0 0 7 * * *"
        override fun run(): CheckResult = error("boom")
    }

    @Test
    fun `runAndStore slaat het resultaat van de check op`() {
        val repository = InMemoryNightlyCheckRepository()
        val check = FixedCheck("check-a", CheckResult(true, "alles gezond"))
        val scheduler = NightlyCheckScheduler(listOf(check), repository)

        scheduler.runAndStore(check)

        assertEquals("alles gezond", repository.latest("check-a")?.result?.summary)
    }

    @Test
    fun `runAndStore vangt een crashende check op in plaats van te crashen`() {
        val repository = InMemoryNightlyCheckRepository()
        val check = ThrowingCheck("check-b")
        val scheduler = NightlyCheckScheduler(listOf(check), repository)

        scheduler.runAndStore(check)

        val run = repository.latest("check-b")
        assertFalse(run?.result?.ok ?: true)
        assertTrue(run?.result?.summary?.contains("boom") == true)
    }
}
