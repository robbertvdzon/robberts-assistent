package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.push.InMemoryFcmTokenStore
import nl.vdzon.robbertsassistent.push.PushService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BriefingSchedulerTest {

    private class FixedProvider(override val order: Int, private val summary: String?) : BriefingSectionProvider {
        override fun section() = BriefingSection(key = "x", title = "x", text = "x")
        override fun shortSummary() = summary
    }

    private class ThrowingSummaryProvider : BriefingSectionProvider {
        override val order = 0
        override fun section() = BriefingSection(key = "x", title = "x", text = "x")
        override fun shortSummary(): String = error("boom")
    }

    /** Zonder Firebase-config blijft `PushService.sendToAll` een no-op (geen crash), zie `PushService`. */
    private fun pushService(): PushService {
        val secrets = AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("robbert@vdzon.com"))
        return PushService(FirebaseProvider(secrets), InMemoryFcmTokenStore())
    }

    @Test
    fun `buildPushBody combineert de shortSummary van elke sectie op volgorde`() {
        val providers = listOf(
            FixedProvider(1, "2 afspraken"),
            FixedProvider(0, "kiten 🟢 avond 24kn"),
            FixedProvider(2, null), // bv. moestuin-placeholder, doet niet mee
        )
        val scheduler = BriefingScheduler(providers, pushService())

        val body = scheduler.buildPushBody()

        assertEquals("Morgen: kiten 🟢 avond 24kn, 2 afspraken", body)
    }

    @Test
    fun `buildPushBody heeft een terugvaltekst als geen enkele sectie een summary geeft`() {
        val scheduler = BriefingScheduler(listOf(FixedProvider(0, null)), pushService())

        assertEquals("Bekijk de briefing in de app.", scheduler.buildPushBody())
    }

    @Test
    fun `buildPushBody vangt een crashende shortSummary op`() {
        val scheduler = BriefingScheduler(listOf(ThrowingSummaryProvider(), FixedProvider(1, "2 afspraken")), pushService())

        assertTrue(scheduler.buildPushBody().contains("2 afspraken"))
    }

    @Test
    fun `sendDailyPush crasht niet als push een no-op is`() {
        val scheduler = BriefingScheduler(listOf(FixedProvider(0, "iets")), pushService())

        scheduler.sendDailyPush() // geen tokens/firebase => no-op, mag niet crashen
    }
}
