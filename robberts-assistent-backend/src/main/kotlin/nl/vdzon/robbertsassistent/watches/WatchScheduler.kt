package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Achtergrondagent: pollt periodiek alle actieve watches en checkt of ze "aan de beurt" zijn.
 * Als de AI een GEVONDEN-status teruggeeft, stuurt deze een pushmelding en zet de watch op inactief.
 */
@Component
class WatchScheduler(
    private val service: WatchesService,
    private val pageFetcher: WatchPageFetcher,
    @Qualifier("watchChatClient") private val chatClient: ChatClient,
    private val pushService: PushService,
    private val appSecrets: AppSecrets,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")
    fun checkWatches() {
        val now = Instant.now()
        val watches = service.activeWatches()

        for (watch in watches) {
            if (!isDue(watch, now)) continue

            logger.info("Watch '{}' wordt gecheckt", watch.title)

            val fetchResult = pageFetcher.fetch(watch.url)
            when (fetchResult) {
                is WatchPageFetcher.FetchResult.Error -> {
                    logger.warn("Watch '{}': pagina ophalen mislukt: {}", watch.title, fetchResult.message)
                    service.markChecked(watch)
                    continue
                }
                is WatchPageFetcher.FetchResult.Success -> {
                    val status = evaluateWithAi(watch, fetchResult.text)
                    handleStatusChange(watch, status)
                }
            }
        }
    }

    private fun evaluateWithAi(watch: Watch, pageText: String): WatchStatus {
        if (appSecrets.effectiveMockAi) {
            logger.info("Watch '{}': mock-AI, status blijft ONBEKEND", watch.title)
            return WatchStatus.ONBEKEND
        }

        return runCatching {
            val prompt = """
                |Pagina-tekst:
                |$pageText
                |
                |Zoekinstructie:
                |${watch.instruction}
            """.trimMargin()

            val response = chatClient.prompt().user(prompt).call().content() ?: ""
            logger.debug("Watch '{}': AI-antwoord: {}", watch.title, response)

            when {
                response.startsWith("GEVONDEN", ignoreCase = true) -> WatchStatus.GEVONDEN
                response.startsWith("NIET GEVONDEN", ignoreCase = true) -> WatchStatus.NIET_GEVONDEN
                else -> WatchStatus.ONBEKEND
            }
        }.getOrElse {
            logger.warn("Watch '{}': AI-call faalde: {}", watch.title, it.message)
            WatchStatus.ONBEKEND
        }
    }

    private fun handleStatusChange(watch: Watch, newStatus: WatchStatus) {
        val wasNotFound = watch.status == WatchStatus.NIET_GEVONDEN || watch.status == WatchStatus.ONBEKEND
        val isNowFound = newStatus == WatchStatus.GEVONDEN

        if (wasNotFound && isNowFound) {
            logger.info("Watch '{}': GEVONDEN! Pushmelding versturen en deactiveren.", watch.title)
            runCatching {
                pushService.sendToAll(
                    title = "Watch: ${watch.title}",
                    body = "Het gezochte item is gevonden!",
                    data = mapOf("type" to "watch"),
                )
            }.onFailure { logger.warn("Push voor watch '{}' faalde: {}", watch.title, it.message) }
            service.updateStatus(watch, WatchStatus.GEVONDEN, deactivate = true)
        } else {
            service.updateStatus(watch, newStatus)
        }
    }

    companion object {
        private val ZONE = ZoneId.of("Europe/Amsterdam")

        /**
         * Bepaalt of een watch nu gecheckt moet worden op basis van frequentie en laatste check.
         * - KANTOORUREN: ma-vr 09:00-17:00, minimaal 1 uur sinds laatste check
         * - DAGELIJKS: minimaal 24 uur sinds laatste check
         */
        internal fun isDue(watch: Watch, now: Instant): Boolean {
            val zoned = ZonedDateTime.ofInstant(now, ZONE)
            val lastChecked = watch.lastChecked

            return when (watch.frequency) {
                WatchFrequency.KANTOORUREN -> {
                    val dayOfWeek = zoned.dayOfWeek
                    val hour = zoned.hour

                    val isWeekday = dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                    val isOfficeHour = hour in 9..16 // 09:00 tot 16:59 (dus t/m 17:00)

                    if (!isWeekday || !isOfficeHour) return false

                    if (lastChecked == null) return true
                    val hoursSinceLastCheck = java.time.Duration.between(lastChecked, now).toHours()
                    hoursSinceLastCheck >= 1
                }
                WatchFrequency.DAGELIJKS -> {
                    if (lastChecked == null) return true
                    val hoursSinceLastCheck = java.time.Duration.between(lastChecked, now).toHours()
                    hoursSinceLastCheck >= 24
                }
            }
        }
    }
}
