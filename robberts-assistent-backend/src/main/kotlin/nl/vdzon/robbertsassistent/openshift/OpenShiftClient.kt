package nl.vdzon.robbertsassistent.openshift

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    /** Concrete versienummers uit ClusterVersion's `availableUpdates` (leeg als [updateAvailable] false is). */
    val availableUpdateVersions: List<String> = emptyList(),
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
    val timeMachine: TimeMachineStatus? = null,
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

/**
 * Time Machine-uitlezing van de externe HDD (`node-metrics`'s `read_time_machine()`, per
 * `*.sparsebundle`-map onder `/host-external-hdd/timemachine`). [error] is gezet als het hele
 * `timemachine`-pad niet uit te lezen was (bv. de map bestaat niet); dan is [backups] leeg.
 */
data class TimeMachineStatus(
    val backups: List<TimeMachineBackup> = emptyList(),
    val error: String? = null,
)

/**
 * Eén Time Machine-backup (één sparsebundle = één Mac). [sizeGb] is de opgetelde bandbestand-
 * grootte, [lastModified] het nieuwste mtime binnen de bundel — beide een proxy voor "grootte
 * op schijf" en "laatste schrijfmoment", niet een exacte laatst-voltooide-backup-timestamp (die
 * zou de bundel zelf gemount moeten worden om op te vragen, zie `configmap-server.yaml`).
 * [error] is gezet als het uitlezen van juist déze ene bundel mislukte.
 */
data class TimeMachineBackup(
    val name: String,
    val sizeGb: Double? = null,
    val lastModified: Instant? = null,
    val error: String? = null,
)

/**
 * Bekende Time Machine-sparsebundle-namen op de externe HDD → mensleesbare eigenaar (zie
 * screenshot sessie 2026-07-24: "MacBook Pro" = Karen, "Robbert's MacBook Pro" = Robbert). Een
 * onbekende naam (nieuwe/hernoemde Mac) valt terug op de ruwe sparsebundle-naam.
 */
private val KNOWN_TIME_MACHINE_OWNERS = mapOf(
    "Robbert's MacBook Pro" to "Robbert",
    "MacBook Pro" to "Karen",
)

val TimeMachineBackup.ownerLabel: String get() = KNOWN_TIME_MACHINE_OWNERS[name] ?: name

// Expliciete Locale (i.p.v. de JVM-default, zoals bv. AgendaSectionProvider gebruikt) zodat de
// maandnaam altijd Nederlands ("jul") is, ongeacht op welke locale de omgeving draait.
private val TIME_MACHINE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("nl")).withZone(ZoneId.of("Europe/Amsterdam"))

/**
 * Mensleesbare samenvatting van alle backups, gedeeld door [nl.vdzon.robbertsassistent.briefing.SystemStatusSectionProvider]
 * en [TimeMachineNightlyCheck]. [now] is een parameter (i.p.v. intern `Instant.now()`) zodat dit
 * deterministisch te testen is.
 */
fun TimeMachineStatus.describe(now: Instant = Instant.now()): String {
    error?.let { return "onbekend ($it)" }
    if (backups.isEmpty()) return "geen backups gevonden"
    return backups.joinToString("; ") { it.describe(now) }
}

fun TimeMachineBackup.describe(now: Instant = Instant.now()): String {
    error?.let { return "$ownerLabel: onbekend ($it)" }
    val size = sizeGb?.let { "$it GB" } ?: "onbekende grootte"
    val lastModifiedText = lastModified?.let { "${TIME_MACHINE_DATE_FORMAT.format(it)} (${humanizeAge(Duration.between(it, now))})" } ?: "onbekend"
    return "$ownerLabel: $size, laatst geschreven $lastModifiedText"
}

private fun humanizeAge(age: Duration): String = when {
    age.toHours() < 1 -> "minder dan een uur geleden"
    age.toHours() < 48 -> "${age.toHours()} uur geleden"
    else -> "${age.toDays()} dagen geleden"
}

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
