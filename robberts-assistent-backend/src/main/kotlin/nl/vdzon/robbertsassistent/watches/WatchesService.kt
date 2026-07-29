package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Beheert de zoekopdrachten (CRUD) en voert één check uit: pagina ophalen → platte tekst →
 * AI-beoordeling ("GEVONDEN"/"NIET GEVONDEN" + korte statuszin).
 *
 * De klasse en [fetchPageText] zijn `open` zodat tests de netwerkcall kunnen vervangen zonder
 * `java.net.http.HttpClient` te hoeven namaken (zelfde aanpak als
 * `briefing.OsmCoastMapImageBuilder.fetchMap`); de [httpClient] blijft daarnaast een
 * constructor-parameter met default, net als bij `assistant.ai.WindTools`.
 */
@Service
open class WatchesService(
    private val repository: WatchRepository,
    @Qualifier("watchChatClient") private val chatClient: ChatClient,
    private val pushService: PushService,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Alle zoekopdrachten, nieuwste bovenaan. */
    fun list(): List<Watch> = repository.all().sortedByDescending { it.createdAt }

    fun find(id: String): Watch? = repository.find(id)

    fun create(
        title: String,
        url: String,
        instruction: String,
        frequency: WatchFrequency,
        pushOnFound: Boolean,
        now: Instant = Instant.now(),
    ): Watch = repository.save(
        Watch(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            pushOnFound = pushOnFound,
            createdAt = now,
        ),
    )

    /**
     * Werkt de door de gebruiker instelbare velden bij (incl. [Watch.active], dus pauzeren en
     * hervatten lopen hier ook doorheen). Geeft `null` als de zoekopdracht niet bestaat.
     */
    fun update(
        id: String,
        title: String,
        url: String,
        instruction: String,
        frequency: WatchFrequency,
        pushOnFound: Boolean,
        active: Boolean,
    ): Watch? {
        val existing = repository.find(id) ?: return null
        return repository.save(
            existing.copy(
                title = title,
                url = url,
                instruction = instruction,
                frequency = frequency,
                pushOnFound = pushOnFound,
                active = active,
            ),
        )
    }

    fun delete(id: String) = repository.delete(id)

    /**
     * Voert één check uit en slaat het resultaat op. Een netwerk-, HTTP- of AI-fout zet alleen
     * [Watch.lastError] op déze watch en laat de vorige status staan (geen exception, geen push).
     *
     * Push + afronden gebeurt uitsluitend bij de omslag "niet gevonden" → "gevonden": is
     * [Watch.pushOnFound] aan, dan gaat er precies één push uit; daarna wordt de watch op
     * `active = false` gezet zodat er geen herhaalde meldingen komen.
     */
    fun check(watch: Watch, now: Instant = Instant.now()): Watch {
        val checked = runCatching {
            val assessment = assess(watch.instruction, fetchPageText(watch.url))
            watch.copy(
                lastCheckedAt = now,
                lastStatus = assessment.status,
                found = assessment.found,
                lastError = null,
            )
        }.getOrElse { ex ->
            logger.warn("Check van zoekopdracht {} faalde: {}", watch.id, ex.message)
            watch.copy(lastCheckedAt = now, lastError = ex.message ?: ex.javaClass.simpleName)
        }

        if (!watch.found && checked.found) {
            if (checked.pushOnFound) {
                runCatching { sendFoundPush(checked) }
                    .onFailure { logger.warn("Push van zoekopdracht {} faalde: {}", watch.id, it.message) }
            }
            // Gevonden = afgerond: niet meer actief, dus de poller laat 'm voortaan met rust.
            return repository.save(checked.copy(active = false))
        }
        return repository.save(checked)
    }

    /** Eén FCM-push met de titel + status; `type = watch` laat de app de juiste tab openen. */
    internal open fun sendFoundPush(watch: Watch) {
        pushService.sendToAll(watch.title, watch.lastStatus.orEmpty(), mapOf("type" to PUSH_TYPE))
    }

    /** Haalt de pagina op en levert 'm als (afgetopte) platte tekst. `open` voor tests. */
    internal open fun fetchPageText(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            error("Pagina gaf HTTP ${response.statusCode()}")
        }
        return htmlToPlainText(response.body())
    }

    private fun assess(instruction: String, pageText: String): Assessment {
        val prompt = "Instructie van Robbert:\n$instruction\n\nPaginatekst:\n$pageText"
        val reply = chatClient.prompt().user(prompt).call().content().orEmpty()
        return parseAssessment(reply)
    }

    /** Uitkomst van één AI-beoordeling. */
    internal data class Assessment(val found: Boolean, val status: String)

    internal companion object {
        // Moet gelijk zijn aan de tak in `fcm_service.dart` (robberts_assistent).
        const val PUSH_TYPE = "watch"
        private const val USER_AGENT =
            "Mozilla/5.0 (compatible; RobbertsAssistent/1.0; +https://robberts-assistent.vdzonsoftware.nl)"

        // Genoeg om de relevante pagina-inhoud mee te sturen zonder de AI-call op te blazen.
        private const val MAX_LENGTH = 6000

        /**
         * Defensieve parser van het vaste tweeregelige antwoordformaat. Een niet-herkende regel 1
         * (bv. het antwoord van `MockChatModel` onder `RA_MOCK_AI`) levert `found = false` met de
         * ruwe tekst als status — nooit een exception en dus ook nooit een push.
         */
        internal fun parseAssessment(reply: String): Assessment {
            val lines = reply.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val marker = lines.firstOrNull().orEmpty().trim('*', '-', '"', '.', ' ').uppercase()
            val status = lines.getOrNull(1)?.trim('*', '-', '"', ' ')?.takeIf { it.isNotBlank() }
            return when {
                marker.startsWith("NIET GEVONDEN") -> Assessment(false, status ?: "Nog niet gevonden.")
                marker.startsWith("GEVONDEN") -> Assessment(true, status ?: "Gevonden.")
                else -> Assessment(
                    false,
                    reply.replace(Regex("\\s+"), " ").trim().take(200).ifBlank { "Kon niet bepalen." },
                )
            }
        }

        /**
         * HTML → platte tekst: script/style eruit, tags strippen, veelvoorkomende entities
         * decoderen, whitespace comprimeren en aftoppen. Bewuste duplicatie van de variant in
         * `assistant.ai.WindTools` — die is `internal` binnen een andere Modulith-module.
         */
        internal fun htmlToPlainText(html: String): String {
            val withoutScripts = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            val withoutTags = withoutScripts.replace(Regex("(?s)<[^>]+>"), " ")
            val decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
            return decoded.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)
        }
    }
}
