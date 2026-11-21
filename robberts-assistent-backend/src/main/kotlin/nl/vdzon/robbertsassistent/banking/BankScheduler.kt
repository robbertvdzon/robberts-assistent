package nl.vdzon.robbertsassistent.banking

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Geen bankinformatie in globale pushberichten of Agent Runtime-jobs. */
@Component
class BankScheduler(
    private val settings: BankSettings,
    private val service: BankService,
    @Value("\${RA_BANKING_AUTO_SYNC:false}") private val enabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 15 4 * * *", zone = "Europe/Amsterdam")
    fun run() {
        if (!enabled || !settings.configured) return
        runCatching { service.sync(settings.owner) }
            .onSuccess { logger.info("Bankadministratie bijgewerkt") }
            .onFailure { logger.warn("Bankadministratie niet bijgewerkt; controleer de koppeling in Bankzaken") }
    }
}
