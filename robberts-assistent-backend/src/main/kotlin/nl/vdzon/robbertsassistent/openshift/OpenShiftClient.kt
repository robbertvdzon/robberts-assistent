package nl.vdzon.robbertsassistent.openshift

/**
 * Gezondheid van de OpenShift-cluster. [degradedOperators] leeg + [updateAvailable] false + geen
 * [error] betekent: alles gezond en up-to-date. [nodeMetrics] is best-effort en apart van [error]:
 * ontbreekt/mislukt de node-metrics-bevraging (zie [NodeMetrics]), dan blijft de rest van de
 * clusterstatus gewoon bruikbaar.
 */
data class ClusterHealthResult(
    val healthy: Boolean,
    val clusterVersion: String?,
    val updateAvailable: Boolean,
    val degradedOperators: List<String>,
    val error: String? = null,
    val nodeMetrics: NodeMetrics? = null,
)

/**
 * Geheugen-/SSD-/externe-HDD-gebruik van de node, opgehaald bij het `node-metrics`-endpoint
 * (`robberts-infrastructure/manifests/node-metrics`) — er is geen Prometheus/node-exporter/
 * metrics-server op dit cluster, dus dit kleine, aparte endpoint is de bron. Elk onderdeel is los
 * optioneel: ontbreekt een mount aan die kant (bv. de externe HDD is even niet aangesloten), dan
 * is dat ene veld `null`/heeft een `error`, zonder de rest te breken.
 */
data class NodeMetrics(
    val memory: MemoryUsage? = null,
    val ssd: DiskUsage? = null,
    val externalHdd: DiskUsage? = null,
)

data class MemoryUsage(
    val totalMb: Long? = null,
    val usedMb: Long? = null,
    val availableMb: Long? = null,
    val usedPercent: Double? = null,
    val error: String? = null,
)

data class DiskUsage(
    val totalGb: Double? = null,
    val usedGb: Double? = null,
    val freeGb: Double? = null,
    val usedPercent: Double? = null,
    val error: String? = null,
)

/** Mensleesbare samenvatting, gedeeld door [nl.vdzon.robbertsassistent.assistant.ai.OpenShiftTools] en [OpenShiftHealthNightlyCheck]. */
fun NodeMetrics.describe(): String = listOfNotNull(
    memory?.let { "geheugen: ${it.describe("MB", it.usedMb, it.totalMb)}" },
    ssd?.let { "SSD: ${it.describe("GB", it.usedGb, it.totalGb)}" },
    externalHdd?.let { "externe HDD: ${it.describe("GB", it.usedGb, it.totalGb)}" },
).joinToString(", ")

private fun MemoryUsage.describe(unit: String, used: Long?, total: Long?) = usageText(unit, used, total, usedPercent, error)
private fun DiskUsage.describe(unit: String, used: Double?, total: Double?) = usageText(unit, used, total, usedPercent, error)

private fun usageText(unit: String, used: Number?, total: Number?, usedPercent: Double?, error: String?): String {
    if (error != null) return "onbekend ($error)"
    if (used == null || total == null) return "onbekend"
    val percent = usedPercent?.let { " ($it%)" }.orEmpty()
    return "$used/$total $unit$percent"
}

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
