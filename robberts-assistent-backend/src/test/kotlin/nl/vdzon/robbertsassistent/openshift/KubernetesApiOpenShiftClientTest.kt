package nl.vdzon.robbertsassistent.openshift

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseClusterHealth`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class KubernetesApiOpenShiftClientTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `gezonde cluster zonder degradaties en zonder beschikbare update`() {
        val clusterVersion = objectMapper.readTree(
            """{"status": {"desired": {"version": "4.16.3"}, "availableUpdates": null}}""",
        )
        val clusterOperators = objectMapper.readTree(
            """
            {"items": [
              {"metadata": {"name": "kube-apiserver"}, "status": {"conditions": [{"type": "Degraded", "status": "False"}]}},
              {"metadata": {"name": "network"}, "status": {"conditions": [{"type": "Degraded", "status": "False"}]}}
            ]}
            """.trimIndent(),
        )

        val result = KubernetesApiOpenShiftClient.parseClusterHealth(clusterVersion, clusterOperators)

        assertTrue(result.healthy)
        assertEquals("4.16.3", result.clusterVersion)
        assertFalse(result.updateAvailable)
        assertTrue(result.degradedOperators.isEmpty())
    }

    @Test
    fun `herkent gedegradeerde operators`() {
        val clusterVersion = objectMapper.readTree("""{"status": {"desired": {"version": "4.16.3"}}}""")
        val clusterOperators = objectMapper.readTree(
            """
            {"items": [
              {"metadata": {"name": "kube-apiserver"}, "status": {"conditions": [{"type": "Degraded", "status": "False"}]}},
              {"metadata": {"name": "storage"}, "status": {"conditions": [{"type": "Degraded", "status": "True"}]}}
            ]}
            """.trimIndent(),
        )

        val result = KubernetesApiOpenShiftClient.parseClusterHealth(clusterVersion, clusterOperators)

        assertFalse(result.healthy)
        assertEquals(listOf("storage"), result.degradedOperators)
    }

    @Test
    fun `herkent een beschikbare update`() {
        val clusterVersion = objectMapper.readTree(
            """{"status": {"desired": {"version": "4.16.3"}, "availableUpdates": [{"version": "4.16.4"}]}}""",
        )
        val clusterOperators = objectMapper.readTree("""{"items": []}""")

        val result = KubernetesApiOpenShiftClient.parseClusterHealth(clusterVersion, clusterOperators)

        assertTrue(result.updateAvailable)
        assertTrue(result.healthy)
    }
}
