package nl.vdzon.robbertsassistent.banking

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

@Component
class BankCouplingProbe(private val settings: BankSettings, private val gateway: BankingGateway) : CouplingProbe {
    override val id = "enable-banking"
    override val name = "Enable Banking"
    override val description = "Alleen-lezen bankkoppeling; toestemming en transacties via Bankzaken."
    override val configured get() = settings.configured
    override val mode get() = if (configured) "echt" else "fallback"
    override fun test(): Pair<Boolean, String> = if (!configured) false to "Bankkoppeling nog niet ingesteld."
        else runCatching { gateway.check() to "API bereikbaar; dit controleert geen rekeningtoestemming." }
            .getOrDefault(false to "API niet bereikbaar; controleer configuratie en certificaat.")
}
