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
        assertTrue(result.availableUpdateVersions.isEmpty())
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
        assertEquals(listOf("4.16.4"), result.availableUpdateVersions)
    }

    @Test
    fun `verzamelt alle versienummers bij meerdere beschikbare updates`() {
        val clusterVersion = objectMapper.readTree(
            """{"status": {"desired": {"version": "4.16.3"}, "availableUpdates": [{"version": "4.16.4"}, {"version": "4.17.0"}]}}""",
        )
        val clusterOperators = objectMapper.readTree("""{"items": []}""")

        val result = KubernetesApiOpenShiftClient.parseClusterHealth(clusterVersion, clusterOperators)

        assertEquals(listOf("4.16.4", "4.17.0"), result.availableUpdateVersions)
    }

    @Test
    fun `parseNodeMetrics zet alle drie de secties correct om`() {
        val root = objectMapper.readTree(
            """
            {
              "memory": {"totalMb": 16000, "usedMb": 8000, "availableMb": 8000, "usedPercent": 50.0},
              "ssd": {"totalGb": 240.0, "usedGb": 120.0, "freeGb": 120.0, "usedPercent": 50.0},
              "externalHdd": {"totalGb": 4000.0, "usedGb": 1000.0, "freeGb": 3000.0, "usedPercent": 25.0}
            }
            """.trimIndent(),
        )

        val result = KubernetesApiOpenShiftClient.parseNodeMetrics(root)

        assertEquals(16000L, result.memory?.totalMb)
        assertEquals(50.0, result.memory?.usedPercent)
        assertEquals(240.0, result.ssd?.totalGb)
        assertEquals(1000.0, result.externalHdd?.usedGb)
    }

    @Test
    fun `parseNodeMetrics geeft een error per sectie door zonder de andere secties te breken`() {
        val root = objectMapper.readTree(
            """
            {
              "memory": {"totalMb": 16000, "usedMb": 8000, "availableMb": 8000, "usedPercent": 50.0},
              "ssd": {"totalGb": 240.0, "usedGb": 120.0, "freeGb": 120.0, "usedPercent": 50.0},
              "externalHdd": {"error": "statvfs failed: [Errno 2] No such file or directory: '/host-external-hdd'"}
            }
            """.trimIndent(),
        )

        val result = KubernetesApiOpenShiftClient.parseNodeMetrics(root)

        assertEquals(50.0, result.memory?.usedPercent)
        assertEquals(240.0, result.ssd?.totalGb)
        assertTrue(result.externalHdd?.error?.contains("No such file") == true)
        assertEquals(null, result.externalHdd?.totalGb)
    }

    @Test
    fun `parseNodeMetrics tolereert ontbrekende secties`() {
        val root = objectMapper.readTree("""{"memory": {"totalMb": 16000, "usedMb": 8000, "availableMb": 8000}}""")

        val result = KubernetesApiOpenShiftClient.parseNodeMetrics(root)

        assertEquals(16000L, result.memory?.totalMb)
        assertEquals(null, result.ssd)
        assertEquals(null, result.externalHdd)
    }
}
