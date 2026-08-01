package nl.vdzon.robbertsassistent.applaunch

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Legt elke app-start vast. Doel is niet de opslag zelf, maar de logregel: zolang nog niet zeker
 * is wát Google Assistent/Gemini meestuurt, worden de ruwe gegevens per start als één regel
 * gelogd zodat ze uit te lezen zijn met
 * `oc logs deploy/robberts-assistent-backend -n robberts-assistent | grep APP_LAUNCH`.
 */
@Service
class AppLaunchService(
    private val repository: AppLaunchRepository,
    private val now: () -> Instant = { Instant.now() },
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Slaat een launch op (server bepaalt id en tijdstip), logt 'm en ruimt oude launches op. */
    fun record(
        source: AppLaunchSource,
        platform: String,
        referrer: String? = null,
        action: String? = null,
        categories: List<String> = emptyList(),
        extras: Map<String, String> = emptyMap(),
        appVersion: String? = null,
    ): AppLaunch {
        val at = now()
        val saved = repository.save(
            AppLaunch(
                id = UUID.randomUUID().toString(),
                at = at,
                source = source,
                platform = platform,
                referrer = referrer,
                action = action,
                categories = categories,
                extras = extras,
                appVersion = appVersion,
            ),
        )
        logger.info(logLine(saved))
        cleanUp(at)
        return saved
    }

    /** De laatste launches, nieuwste eerst. [limit] wordt begrensd op [MAX_LIMIT]. */
    fun recent(limit: Int = DEFAULT_LIMIT): List<AppLaunch> =
        repository.recent(limit.coerceIn(1, MAX_LIMIT))

    /**
     * Best effort: een falende opschoning mag het opslaan van een launch nooit laten mislukken —
     * de logregel is er dan al uit en dat is waar het om gaat.
     */
    private fun cleanUp(at: Instant) {
        runCatching { repository.deleteOlderThan(at.minus(RETENTION)) }
            .onFailure { logger.warn("Opschonen van oude app-launches faalde", it) }
    }

    /**
     * Exact één regel per launch, in een vast greppable formaat. Ontbrekende waarden worden
     * `null`, lege lijsten/maps een lege waarde, en newlines worden een spatie zodat de regel
     * altijd één regel blijft.
     */
    private fun logLine(launch: AppLaunch): String = buildString {
        append("APP_LAUNCH")
        append(" source=").append(launch.source.name)
        append(" platform=").append(oneLine(launch.platform))
        append(" referrer=").append(oneLine(launch.referrer))
        append(" action=").append(oneLine(launch.action))
        append(" categories=").append(launch.categories.joinToString(",") { oneLine(it) })
        append(" extras=")
        append(launch.extras.entries.joinToString(";") { "${oneLine(it.key)}=${oneLine(it.value)}" })
    }

    private fun oneLine(value: String?): String =
        value?.replace('\n', ' ')?.replace('\r', ' ') ?: "null"

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
