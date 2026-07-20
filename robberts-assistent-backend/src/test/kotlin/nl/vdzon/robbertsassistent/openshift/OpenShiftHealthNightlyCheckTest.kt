package nl.vdzon.robbertsassistent.openshift

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenShiftHealthNightlyCheckTest {

    @Test
    fun `run geeft ok terug op basis van de stub`() {
        val check = OpenShiftHealthNightlyCheck(StubOpenShiftClient())

        val result = check.run()

        assertTrue(result.ok)
        assertTrue(result.summary.contains("gezond"), result.summary)
    }

    @Test
    fun `run geeft de foutmelding door als de client een error teruggeeft`() {
        val failing = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(false, null, false, emptyList(), "kapot")
        }
        val check = OpenShiftHealthNightlyCheck(failing)

        val result = check.run()

        assertEquals(false, result.ok)
        assertEquals("kapot", result.summary)
    }

    @Test
    fun `run vermeldt gedegradeerde operators in het detail`() {
        val degraded = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(false, "4.16.3", false, listOf("storage", "network"))
        }
        val check = OpenShiftHealthNightlyCheck(degraded)

        val result = check.run()

        assertEquals(false, result.ok)
        assertTrue(result.detail?.contains("storage") == true)
        assertTrue(result.detail?.contains("network") == true)
    }
}
