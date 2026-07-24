package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.describe
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent toegang tot de OpenShift-clustergezondheid via [OpenShiftClient]
 * (in-cluster ServiceAccount, geen los secret — actief zodra `RA_OPENSHIFT_HEALTH_ENABLED` aan
 * staat, zie CLAUDE.md §5).
 */
@Component
class OpenShiftTools(private val client: OpenShiftClient) {

    @Tool(
        description = "Haal de gezondheid van de OpenShift-cluster op: gedegradeerde operators, of er " +
            "een platform-/security-update beschikbaar is, en het geheugen-/SSD-/externe-HDD-gebruik " +
            "van de node. Gebruik dit voor vragen als 'is de cluster nog gezond', 'moet ik updaten' of " +
            "'hoeveel geheugen/schijfruimte is er nog vrij'.",
    )
    fun getOpenShiftHealth(): String {
        val health = client.clusterHealth()
        health.error?.let { return it }
        val status = if (health.healthy) "gezond" else "gedegradeerd"
        val updates = if (health.updateAvailable) "er is een update beschikbaar" else "geen update beschikbaar"
        val degraded = health.degradedOperators.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "; gedegradeerde operators: ")
            ?: ""
        val metrics = health.nodeMetrics?.let { ". ${it.describe()}" }.orEmpty()
        return "Cluster is $status (versie ${health.clusterVersion}), $updates$degraded$metrics"
    }
}
