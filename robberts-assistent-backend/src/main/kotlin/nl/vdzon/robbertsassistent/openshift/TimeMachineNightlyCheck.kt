package nl.vdzon.robbertsassistent.openshift

import nl.vdzon.robbertsassistent.nightlychecks.CheckResult
import nl.vdzon.robbertsassistent.nightlychecks.NightlyCheck
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** Hoe lang een backup zonder nieuwe schrijfactie mag zijn voordat de check niet-ok meldt. */
private val STALE_AFTER = Duration.ofHours(48)

/**
 * Tweede nightly check op de node-metrics-route (zie [OpenShiftHealthNightlyCheck] voor de
 * eerste): staan de Time Machine-backups van beide MacBooks (naar de externe HDD via
 * `robberts-infrastructure/manifests/smb-timemachine`) er nog actueel bij. Eigen check i.p.v.
 * meeliften op de OpenShift-gezondheidscheck, zodat een verouderde backup niet de
 * clustergezondheid-status vervuilt en dit een eigen (lossere) cron kan hebben. Gebruikt dezelfde
 * [OpenShiftClient]/`node-metrics`-endpoint, dus dezelfde `RA_OPENSHIFT_HEALTH_ENABLED`-vlag en
 * [StubOpenShiftClient]-fallback. In tegenstelling tot [describe] (gedeeld met de vrije-tekst
 * briefingsectie, bewust zonder hardcoded drempel) hoort een expliciete staleness-drempel wél
 * hier thuis — dit is precies de plek voor een harde ja/nee-check, zie ook de
 * `zonneplan.ZonneplanCouplingProbe`-KDoc voor hetzelfde onderscheid.
 */
@Component
class TimeMachineNightlyCheck(private val client: OpenShiftClient) : NightlyCheck {

    override val id = "time-machine-backups"
    override val name = "Time Machine-backups"
    override val description = "Laatste schrijfmoment en grootte van de Time Machine-backups van beide MacBooks op de externe HDD."

    // Eén keer per ochtend, samen met de OpenShift-gezondheidscheck (07:00) maar een half uur later.
    override val cronSchedule = "0 30 7 * * *"

    override fun run(): CheckResult {
        val health = client.clusterHealth()
        health.error?.let { return CheckResult(ok = false, summary = "Kon node-metrics niet ophalen: $it") }

        val status = health.nodeMetrics?.timeMachine
            ?: return CheckResult(ok = false, summary = "Geen Time Machine-gegevens ontvangen van node-metrics")
        status.error?.let { return CheckResult(ok = false, summary = "Time Machine-uitlezing mislukt: $it") }
        if (status.backups.isEmpty()) {
            return CheckResult(ok = false, summary = "Geen sparsebundles gevonden op de externe HDD")
        }

        val now = Instant.now()
        val stale = status.backups.filter { isStale(it, now) }
        val summary = if (stale.isEmpty()) {
            "Beide backups zijn actueel"
        } else {
            "Verouderd of onbekend: " + stale.joinToString(", ") { it.ownerLabel }
        }
        val detail = status.backups.joinToString("\n") { it.describe(now) }

        return CheckResult(ok = stale.isEmpty(), summary = summary, detail = detail)
    }

    private fun isStale(backup: TimeMachineBackup, now: Instant): Boolean =
        backup.error != null || backup.lastModified == null || Duration.between(backup.lastModified, now) > STALE_AFTER
}
