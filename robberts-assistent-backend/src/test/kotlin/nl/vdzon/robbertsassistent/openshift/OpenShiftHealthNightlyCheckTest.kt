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

    @Test
    fun `run vermeldt het node-metrics-gebruik van de stub in het detail`() {
        val check = OpenShiftHealthNightlyCheck(StubOpenShiftClient())

        val result = check.run()

        assertTrue(result.detail?.contains("geheugen") == true, result.detail)
        assertTrue(result.detail?.contains("SSD") == true, result.detail)
        assertTrue(result.detail?.contains("externe HDD") == true, result.detail)
    }

    @Test
    fun `run combineert gedegradeerde operators en node-metrics in het detail`() {
        val degradedWithMetrics = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(
                healthy = false,
                clusterVersion = "4.16.3",
                updateAvailable = false,
                degradedOperators = listOf("storage"),
                nodeMetrics = NodeMetrics(memory = MemoryUsage(totalMb = 16000, usedMb = 8000, availableMb = 8000, usedPercent = 50.0)),
            )
        }
        val check = OpenShiftHealthNightlyCheck(degradedWithMetrics)

        val result = check.run()

        assertTrue(result.detail?.contains("storage") == true, result.detail)
        assertTrue(result.detail?.contains("geheugen") == true, result.detail)
    }

    @Test
    fun `run noemt de concrete versie bij een beschikbare update`() {
        val withUpdate = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(
                healthy = true,
                clusterVersion = "4.16.3",
                updateAvailable = true,
                degradedOperators = emptyList(),
                availableUpdateVersions = listOf("4.16.4"),
            )
        }
        val check = OpenShiftHealthNightlyCheck(withUpdate)

        val result = check.run()

        assertTrue(result.summary.contains("4.16.4"), result.summary)
    }
}
