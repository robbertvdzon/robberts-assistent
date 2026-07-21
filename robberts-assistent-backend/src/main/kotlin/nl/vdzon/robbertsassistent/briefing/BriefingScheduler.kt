package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Stuurt elke dag om 18:00 (Europe/Amsterdam) één FCM-push met een korte samenvatting van de
 * 'Morgen'-briefing (zie [BriefingSectionProvider.shortSummary]), via de bestaande [PushService]
 * (no-op zonder Firebase/geregistreerde tokens — geen crash, zie `PushService.sendToAll`).
 */
@Component
class BriefingScheduler(
    private val providers: List<BriefingSectionProvider>,
    private val pushService: PushService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Amsterdam")
    fun sendDailyPush() {
        val body = buildPushBody()
        runCatching { pushService.sendToAll(TITLE, body) }
            .onFailure { logger.warn("Morgen-briefing-push mislukt: {}", it.message) }
    }

    internal fun buildPushBody(): String {
        val parts = providers.sortedBy { it.order }.mapNotNull { provider ->
            runCatching { provider.shortSummary() }
                .onFailure { logger.warn("shortSummary() van briefingsectie {} faalde", provider.javaClass.simpleName, it) }
                .getOrNull()
        }
        return if (parts.isEmpty()) "Bekijk de briefing in de app." else "Morgen: " + parts.joinToString(", ")
    }

    private companion object {
        const val TITLE = "Morgen-briefing"
    }
}
