package nl.vdzon.robbertsassistent.openshift

/**
 * Gezondheid van de OpenShift-cluster. [degradedOperators] leeg + [updateAvailable] false + geen
 * [error] betekent: alles gezond en up-to-date.
 */
data class ClusterHealthResult(
    val healthy: Boolean,
    val clusterVersion: String?,
    val updateAvailable: Boolean,
    val degradedOperators: List<String>,
    val error: String? = null,
)

/**
 * Read-only bevraging van de OpenShift-cluster waar deze backend zelf op draait (ClusterVersion +
 * ClusterOperators — "is de cluster gezond, staan er platform-/security-updates klaar"). Gebruikt
 * de in-cluster ServiceAccount van de pod zelf (geen los secret), zie
 * [nl.vdzon.robbertsassistent.config.AppSecrets.openShiftHealthEnabled] en `docs/nightly-checks.md`
 * voor de nog te zetten RBAC. Actief zodra die vlag aan staat; anders [StubOpenShiftClient].
 */
interface OpenShiftClient {
    fun clusterHealth(): ClusterHealthResult
}
