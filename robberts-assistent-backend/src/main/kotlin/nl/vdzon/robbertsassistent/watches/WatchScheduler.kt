package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Achtergrondagent: één globale poller (`ra.watches.poll-interval-ms`) die per actieve watch kijkt
 * of hij aan de beurt is ([WatchScheduling.isDue]), zo ja de pagina ophaalt ([WatchPageFetcher]) en
 * door de AI laat beoordelen ([WatchAiConfig.watchChatClient]). Bij een transitie naar
 * [WatchStatus.GEVONDEN] (dus niet bij elke poll die al gevonden was) stuurt hij bij
 * `notifyOnFound = true` precies één push en zet de watch op inactief. Een falende pagina-ophaal-
 * of AI-stap voor één watch wordt geïsoleerd afgevangen — andere watches en de volgende poll-ronde
 * blijven ongemoeid.
 */
@Component
class WatchScheduler(
    private val watchesService: WatchesService,
    private val pageFetcher: WatchPageFetcher,
    @Qualifier("watchChatClient") private val chatClient: ChatClient,
    private val pushService: PushService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")
    fun pollDueWatches() {
        val now = Instant.now()
        watchesService.active()
            .filter { WatchScheduling.isDue(it.frequency, it.lastCheckedAt, now) }
            .forEach { watch ->
                runCatching { checkWatch(watch, now) }
                    .onFailure { logger.warn("Watch-check voor '{}' ({}) faalde: {}", watch.title, watch.id, it.message) }
            }
    }

    private fun checkWatch(watch: Watch, now: Instant) {
        val pageText = pageFetcher.fetchPlainText(watch.url)
        val prompt = "Instructie: ${watch.instruction}\n\nPaginatekst:\n$pageText"
        val response = chatClient.prompt().user(prompt).call().content().orEmpty()
        val verdict = WatchVerdict.parse(response)

        val wasFound = watch.status == WatchStatus.GEVONDEN
        val justFound = !wasFound && verdict.status == WatchStatus.GEVONDEN
        if (justFound && watch.notifyOnFound) {
            pushService.sendToAll(
                title = "Zoekopdracht: ${watch.title}",
                body = verdict.statusText.ifBlank { "Gevonden!" },
                data = mapOf("type" to "watch"),
            )
        }
        watchesService.save(
            watch.copy(
                status = verdict.status,
                statusText = verdict.statusText,
                lastCheckedAt = now,
                active = if (justFound) false else watch.active,
            ),
        )
    }
}
