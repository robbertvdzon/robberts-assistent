package nl.vdzon.robbertsassistent.openshift

import java.time.Instant

/**
 * Vaste, deterministische clusterstatus — puur voor tests, zodat tools/checks zonder netwerk-call
 * getest kunnen worden (zelfde patroon als `StubCalendarClient`). [OpenShiftClientConfig] kiest
 * deze zolang `RA_OPENSHIFT_HEALTH_ENABLED` niet aan staat (de RBAC bestaat nog niet, zie
 * `docs/nightly-checks.md`).
 */
class StubOpenShiftClient : OpenShiftClient {
    override fun clusterHealth(): ClusterHealthResult = ClusterHealthResult(
        healthy = true,
        clusterVersion = "0.0.0-stub",
        updateAvailable = false,
        degradedOperators = emptyList(),
        nodeMetrics = NodeMetrics(
            memory = MemoryUsage(totalMb = 16000, usedMb = 8000, availableMb = 8000, usedPercent = 50.0),
            ssd = DiskUsage(totalGb = 240.0, usedGb = 120.0, freeGb = 120.0, usedPercent = 50.0),
            externalHdd = DiskUsage(totalGb = 4000.0, usedGb = 2000.0, freeGb = 2000.0, usedPercent = 50.0),
            timeMachine = TimeMachineStatus(
                backups = listOf(
                    TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = Instant.now().minusSeconds(3600)),
                    TimeMachineBackup(name = "MacBook Pro", sizeGb = 410.0, lastModified = Instant.now().minusSeconds(3600)),
                ),
            ),
        ),
    )
}
