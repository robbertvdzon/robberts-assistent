package nl.vdzon.robbertsassistent.openshift

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
    )
}
