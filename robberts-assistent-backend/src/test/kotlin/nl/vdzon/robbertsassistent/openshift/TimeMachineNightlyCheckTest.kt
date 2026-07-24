package nl.vdzon.robbertsassistent.openshift

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeMachineNightlyCheckTest {

    private fun clientWith(vararg backups: TimeMachineBackup) = object : OpenShiftClient {
        override fun clusterHealth() = ClusterHealthResult(
            healthy = true,
            clusterVersion = "4.16.3",
            updateAvailable = false,
            degradedOperators = emptyList(),
            nodeMetrics = NodeMetrics(timeMachine = TimeMachineStatus(backups = backups.toList())),
        )
    }

    @Test
    fun `run is ok als beide backups recent zijn`() {
        val now = Instant.now()
        val check = TimeMachineNightlyCheck(
            clientWith(
                TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = now.minusSeconds(3600)),
                TimeMachineBackup(name = "MacBook Pro", sizeGb = 410.0, lastModified = now.minusSeconds(7200)),
            ),
        )

        val result = check.run()

        assertTrue(result.ok)
        assertEquals("Beide backups zijn actueel", result.summary)
        assertTrue(result.detail?.contains("Robbert") == true, result.detail)
        assertTrue(result.detail?.contains("Karen") == true, result.detail)
    }

    @Test
    fun `run is niet ok als een backup ouder is dan 48 uur`() {
        val now = Instant.now()
        val check = TimeMachineNightlyCheck(
            clientWith(
                TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = now.minusSeconds(3600)),
                TimeMachineBackup(name = "MacBook Pro", sizeGb = 410.0, lastModified = now.minus(java.time.Duration.ofHours(72))),
            ),
        )

        val result = check.run()

        assertFalse(result.ok)
        assertTrue(result.summary.contains("Karen"), result.summary)
    }

    @Test
    fun `run is niet ok als een backup een error heeft`() {
        val check = TimeMachineNightlyCheck(
            clientWith(
                TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = Instant.now()),
                TimeMachineBackup(name = "MacBook Pro", error = "Permission denied"),
            ),
        )

        val result = check.run()

        assertFalse(result.ok)
        assertTrue(result.detail?.contains("Permission denied") == true, result.detail)
    }

    @Test
    fun `run meldt geen sparsebundles gevonden als de lijst leeg is`() {
        val check = TimeMachineNightlyCheck(clientWith())

        val result = check.run()

        assertFalse(result.ok)
        assertEquals("Geen sparsebundles gevonden op de externe HDD", result.summary)
    }

    @Test
    fun `run geeft de foutmelding door als de client een error teruggeeft`() {
        val failing = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(false, null, false, emptyList(), "kapot")
        }
        val check = TimeMachineNightlyCheck(failing)

        val result = check.run()

        assertFalse(result.ok)
        assertTrue(result.summary.contains("kapot"), result.summary)
    }

    @Test
    fun `run meldt een mislukte timeMachine-uitlezing als geheel`() {
        val client = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(
                healthy = true,
                clusterVersion = "4.16.3",
                updateAvailable = false,
                degradedOperators = emptyList(),
                nodeMetrics = NodeMetrics(timeMachine = TimeMachineStatus(error = "geen mount gevonden")),
            )
        }
        val check = TimeMachineNightlyCheck(client)

        val result = check.run()

        assertFalse(result.ok)
        assertTrue(result.summary.contains("geen mount gevonden"), result.summary)
    }

    @Test
    fun `run gebruikt de stub-data en is ok`() {
        val check = TimeMachineNightlyCheck(StubOpenShiftClient())

        val result = check.run()

        assertTrue(result.ok)
    }
}
