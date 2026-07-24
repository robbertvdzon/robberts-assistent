package nl.vdzon.robbertsassistent.zonneplan

import nl.vdzon.robbertsassistent.config.AppSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZonneplanCouplingProbeTest {

    private fun secrets(configured: Boolean) = AppSecrets(
        rememberSecret = "s",
        googleClientId = "c",
        allowedEmails = setOf("robbert@vdzon.com"),
        homeAssistantUrl = if (configured) "https://home-assistant.example" else null,
        homeAssistantToken = if (configured) "token" else null,
    )

    @Test
    fun `test meldt niet geconfigureerd als de secrets ontbreken`() {
        val probe = ZonneplanCouplingProbe(secrets(configured = false), StubZonneplanClient())

        val (ok, detail) = probe.test()

        assertFalse(ok)
        assertTrue(detail.contains("niet geconfigureerd"))
        assertFalse(probe.configured)
        assertEquals("fallback", probe.mode)
    }

    @Test
    fun `test is ok bij een normale dagopbrengst`() {
        val client = object : ZonneplanClient {
            override fun status() = SolarStatusResult(currentPowerWatt = 900, yesterdayYieldKwh = 12.4)
        }
        val probe = ZonneplanCouplingProbe(secrets(configured = true), client)

        val (ok, detail) = probe.test()

        assertTrue(ok)
        assertTrue(detail.contains("900 W"))
        assertTrue(detail.contains("12.4 kWh"))
    }

    @Test
    fun `test meldt een storing bij nagenoeg geen opbrengst gisteren`() {
        val client = object : ZonneplanClient {
            override fun status() = SolarStatusResult(currentPowerWatt = 0, yesterdayYieldKwh = 0.0)
        }
        val probe = ZonneplanCouplingProbe(secrets(configured = true), client)

        val (ok, detail) = probe.test()

        assertFalse(ok)
        assertTrue(detail.contains("storing"))
    }

    @Test
    fun `test geeft de foutmelding door bij een netwerkfout`() {
        val client = object : ZonneplanClient {
            override fun status() = SolarStatusResult(null, null, "netwerkfout")
        }
        val probe = ZonneplanCouplingProbe(secrets(configured = true), client)

        val (ok, detail) = probe.test()

        assertFalse(ok)
        assertEquals("netwerkfout", detail)
    }
}
