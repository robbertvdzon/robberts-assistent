package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.openshift.ClusterHealthResult
import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.StubOpenShiftClient
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenShiftToolsTest {

    @Test
    fun `getOpenShiftHealth geeft de status op basis van de stub`() {
        val tools = OpenShiftTools(StubOpenShiftClient())

        val result = tools.getOpenShiftHealth()

        assertTrue(result.contains("gezond"), result)
        assertTrue(result.contains("0.0.0-stub"), result)
    }

    @Test
    fun `getOpenShiftHealth vermeldt het geheugen-SSD-externe-HDD-gebruik van de stub`() {
        val tools = OpenShiftTools(StubOpenShiftClient())

        val result = tools.getOpenShiftHealth()

        assertTrue(result.contains("geheugen"), result)
        assertTrue(result.contains("SSD"), result)
        assertTrue(result.contains("externe HDD"), result)
    }

    @Test
    fun `getOpenShiftHealth noemt de concrete versie bij een beschikbare update`() {
        val client = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(
                healthy = true,
                clusterVersion = "4.16.3",
                updateAvailable = true,
                degradedOperators = emptyList(),
                availableUpdateVersions = listOf("4.16.4"),
            )
        }
        val tools = OpenShiftTools(client)

        val result = tools.getOpenShiftHealth()

        assertTrue(result.contains("4.16.4"), result)
    }
}
