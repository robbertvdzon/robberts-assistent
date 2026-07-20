package nl.vdzon.robbertsassistent.openshift

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de OpenShift-gezondheidscheck. */
@Component
class OpenShiftCouplingProbe(
    private val secrets: AppSecrets,
    private val client: OpenShiftClient,
) : CouplingProbe {

    override val id = "openshift"
    override val name = "OpenShift"
    override val description = "Clustergezondheid en platform-updates (nightly check)."
    override val configured: Boolean get() = secrets.openShiftHealthEnabled
    override val mode: String get() = if (secrets.openShiftHealthEnabled) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!secrets.openShiftHealthEnabled) return false to "niet geconfigureerd (stub, RBAC nog niet gezet)"
        val result = client.clusterHealth()
        return result.error?.let { false to it }
            ?: (true to "cluster ${if (result.healthy) "gezond" else "gedegradeerd"}, versie ${result.clusterVersion}")
    }
}
