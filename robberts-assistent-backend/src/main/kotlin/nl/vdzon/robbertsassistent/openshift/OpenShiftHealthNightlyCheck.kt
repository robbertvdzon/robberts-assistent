package nl.vdzon.robbertsassistent.openshift

import nl.vdzon.robbertsassistent.nightlychecks.CheckResult
import nl.vdzon.robbertsassistent.nightlychecks.NightlyCheck
import org.springframework.stereotype.Component

/** Eerste nightly check: is de OpenShift-cluster gezond, en staan er platform-updates klaar. */
@Component
class OpenShiftHealthNightlyCheck(private val client: OpenShiftClient) : NightlyCheck {

    override val id = "openshift-health"
    override val name = "OpenShift-gezondheid"
    override val description = "Clustergezondheid (gedegradeerde operators) en beschikbare platform-updates."

    // Elke ochtend om 07:00 — vaker heeft weinig zin voor een cluster die je zelf thuis beheert.
    override val cronSchedule = "0 0 7 * * *"

    override fun run(): CheckResult {
        val health = client.clusterHealth()
        health.error?.let { return CheckResult(ok = false, summary = it) }

        val summary = buildString {
            append(if (health.healthy) "Cluster gezond" else "Cluster gedegradeerd")
            health.clusterVersion?.let { append(" (versie $it)") }
            if (health.updateAvailable) append(" — update beschikbaar")
        }
        val detail = health.degradedOperators.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "Gedegradeerde operators: ")

        return CheckResult(ok = health.healthy, summary = summary, detail = detail)
    }
}
