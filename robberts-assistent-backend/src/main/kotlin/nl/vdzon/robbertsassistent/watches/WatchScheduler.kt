package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pollt actieve watches en checkt per watch of 'ie aan de beurt is. Frequentie-regels:
 * - KANTOORUREN: ma-vr 09:00-17:00, maximaal één check per uur
 * - DAGELIJKS: maximaal één check per 24 uur
 *
 * Bij een transitie naar GEVONDEN: push-notificatie en watch deactiveren.
 */
@Component
class WatchScheduler(
    private val watchesService: WatchesService,
    private val pushService: PushService,
    private val appSecrets: AppSecrets,
    @Qualifier("watchChatClient") private val watchChatClient: ChatClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    @Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")
    fun pollWatches() {
        val now = Instant.now()
        val zonedNow = ZonedDateTime.ofInstant(now, ZONE)

        watchesService.list()
            .filter { it.active }
            .filter { isDue(it, zonedNow) }
            .forEach { watch -> checkWatch(watch, now) }
    }

    private fun isDue(watch: Watch, now: ZonedDateTime): Boolean {
        return when (watch.frequency) {
            WatchFrequency.KANTOORUREN -> {
                val dow = now.dayOfWeek
                val hour = now.hour
                val isWorkday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
                val isOfficeHour = hour in 9..16
                if (!isWorkday || !isOfficeHour) return false
                val lastCheck = watch.lastChecked ?: return true
                Duration.between(lastCheck, now.toInstant()) >= Duration.ofHours(1)
            }
            WatchFrequency.DAGELIJKS -> {
                val lastCheck = watch.lastChecked ?: return true
                Duration.between(lastCheck, now.toInstant()) >= Duration.ofHours(24)
            }
        }
    }

    private fun checkWatch(watch: Watch, now: Instant) {
        logger.info("Controleer watch '{}' ({})", watch.title, watch.id)
        val pageText = fetchPageText(watch.url)
        if (pageText == null) {
            logger.warn("Kon pagina niet ophalen voor watch '{}'", watch.title)
            watchesService.save(watch.copy(lastChecked = now))
            return
        }

        val (status, statusText) = evaluateWithAi(pageText, watch.instruction)
        val previousStatus = watch.status
        val updated = watch.copy(
            status = status,
            statusText = statusText,
            lastChecked = now,
            active = if (status == WatchStatus.GEVONDEN) false else watch.active,
        )
        watchesService.save(updated)

        if (status == WatchStatus.GEVONDEN && previousStatus != WatchStatus.GEVONDEN) {
            logger.info("Watch '{}' is GEVONDEN, stuur push", watch.title)
            runCatching {
                pushService.sendToAll(
                    title = "Watch: ${watch.title}",
                    body = statusText ?: "Gevonden!",
                    data = mapOf("type" to "watch"),
                )
            }.onFailure { logger.warn("Push voor watch '{}' faalde: {}", watch.title, it.message) }
        }
    }

    private fun fetchPageText(url: String): String? {
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                htmlToPlainText(response.body())
            } else {
                logger.warn("HTTP {} bij ophalen van {}", response.statusCode(), url)
                null
            }
        }.onFailure { logger.warn("Fout bij ophalen van {}: {}", url, it.message) }
            .getOrNull()
    }

    private fun evaluateWithAi(pageText: String, instruction: String): Pair<WatchStatus, String?> {
        if (appSecrets.effectiveMockAi) {
            return WatchStatus.ONBEKEND to "AI niet beschikbaar (mock-modus)"
        }
        return runCatching {
            val prompt = """
                INSTRUCTIE: $instruction

                PAGINA-INHOUD:
                ${pageText.take(MAX_PAGE_LENGTH)}
            """.trimIndent()
            val response = watchChatClient.prompt().user(prompt).call().content() ?: ""
            parseAiResponse(response)
        }.onFailure { logger.warn("AI-beoordeling faalde: {}", it.message) }
            .getOrElse { WatchStatus.ONBEKEND to "Fout bij AI-beoordeling" }
    }

    private fun parseAiResponse(response: String): Pair<WatchStatus, String?> {
        val lines = response.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return WatchStatus.ONBEKEND to null
        val firstLine = lines[0].trim().uppercase()
        val status = when {
            firstLine.startsWith("GEVONDEN") -> WatchStatus.GEVONDEN
            firstLine.startsWith("NIET GEVONDEN") || firstLine.startsWith("NIET_GEVONDEN") -> WatchStatus.NIET_GEVONDEN
            else -> WatchStatus.ONBEKEND
        }
        val statusText = lines.getOrNull(1)?.trim()
        return status to statusText
    }

    companion object {
        private val ZONE = ZoneId.of("Europe/Amsterdam")
        private const val MAX_PAGE_LENGTH = 50_000
    }
}

/** Strip script/style/tags, decodeer een handjevol entities, comprimeer whitespace. */
internal fun htmlToPlainText(html: String): String {
    val withoutScripts = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    val withoutTags = withoutScripts.replace(Regex("(?s)<[^>]+>"), " ")
    val decoded = withoutTags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
    return decoded.replace(Regex("\\s+"), " ").trim()
}
