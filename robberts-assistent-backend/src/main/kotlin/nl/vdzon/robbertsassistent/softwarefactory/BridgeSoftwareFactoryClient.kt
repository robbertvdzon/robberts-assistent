package nl.vdzon.robbertsassistent.softwarefactory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * Echte bridge naar de software-factory-dashboard-backend — cluster-intern bereikbaar (beide
 * draaien op dezelfde OpenShift-cluster), dus geen publieke hostname nodig. Logt zelf in: ververst
 * het Google-refresh-token naar een ID-token (zelfde OAuth-client als de app-login,
 * `googleClientId`/`AppSecrets.googleClientId`), wisselt dat in voor een factory-sessie-token
 * (geldig ~30 dagen bij de factory zelf) en cachet dat in memory. Bij HTTP 401 (sessie verlopen of
 * ingetrokken) wordt bij de volgende aanroep automatisch opnieuw ingelogd.
 *
 * Zelfde caching-patroon als [nl.vdzon.robbertsassistent.google.GoogleOAuthService].
 */
class BridgeSoftwareFactoryClient(
    private val googleClientId: String,
    private val googleClientSecret: String,
    private val googleRefreshToken: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : SoftwareFactoryClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    @Volatile
    private var cachedSession: CachedSession? = null

    override fun stories(): FactoryStoriesResult =
        runCatching { parseStories(fetch("/api/v1/stories")) }
            .getOrElse { FactoryStoriesResult(emptyList(), it.message ?: "Onbekende fout") }

    override fun myActions(): FactoryMyActionsResult =
        runCatching { parseMyActions(fetch("/api/v1/my-actions")) }
            .getOrElse { FactoryMyActionsResult(emptyList(), it.message ?: "Onbekende fout") }

    private fun fetch(path: String): JsonNode {
        val token = sessionToken()
        val request = HttpRequest.newBuilder(URI.create("$BASE_URL$path"))
            .header("Authorization", "Bearer $token")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 401) {
            cachedSession = null
            error("Software-factory-sessie verlopen of geweigerd (HTTP 401).")
        }
        if (response.statusCode() !in 200..299) {
            error("Software factory gaf HTTP ${response.statusCode()} terug.")
        }
        return objectMapper.readTree(response.body())
    }

    private fun sessionToken(): String {
        val now = Instant.now()
        cachedSession?.let { if (it.expiresAt.isAfter(now.plusSeconds(60))) return it.token }
        return login(now)
    }

    @Synchronized
    private fun login(now: Instant): String {
        // Dubbelcheck: een andere thread kan zojuist al ingelogd zijn.
        cachedSession?.let { if (it.expiresAt.isAfter(now.plusSeconds(60))) return it.token }

        val idToken = refreshGoogleIdToken()
        val sessionToken = exchangeForSessionToken(idToken)
        // Iets korter dan de 30 dagen die de factory zelf hanteert, zodat we ruim op tijd verversen.
        val session = CachedSession(sessionToken, now.plus(Duration.ofDays(29)))
        cachedSession = session
        logger.info("Software-factory-sessie ververst (geldig tot {})", session.expiresAt)
        return session.token
    }

    private fun refreshGoogleIdToken(): String {
        val form = mapOf(
            "client_id" to googleClientId,
            "client_secret" to googleClientSecret,
            "refresh_token" to googleRefreshToken,
            "grant_type" to "refresh_token",
        ).map { (k, v) -> "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }.joinToString("&")

        val request = HttpRequest.newBuilder(URI.create(GOOGLE_TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Google token-refresh voor de software-factory-bridge faalde (HTTP ${response.statusCode()}).")
        }
        return objectMapper.readTree(response.body()).path("id_token").asText(null)
            ?: error("Google token-response bevat geen id_token.")
    }

    private fun exchangeForSessionToken(idToken: String): String {
        val body = objectMapper.writeValueAsString(mapOf("idToken" to idToken))
        val request = HttpRequest.newBuilder(URI.create("$BASE_URL/api/v1/auth/google"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Inloggen bij de software factory faalde (HTTP ${response.statusCode()}).")
        }
        return objectMapper.readTree(response.body()).path("token").asText(null)
            ?: error("Software-factory-loginresponse bevat geen token.")
    }

    private data class CachedSession(val token: String, val expiresAt: Instant)

    internal companion object {
        // Cluster-interne service (namespace software-factory) — geen publieke hostname nodig.
        private const val BASE_URL = "http://softwarefactory-dashboard-backend.software-factory"
        private const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"

        /**
         * Zet de ruwe `/api/v1/stories`-JSON om naar een lijst [FactoryStory]s.
         *
         * De payload bevat zowel stories als subtaken (onderscheiden via `issueType`). Subtaken
         * hebben altijd `storyPhase=null` (ze gebruiken `subtaskPhase`), dus zonder filter lijken ze
         * op "fase=onbekend" — filter daarom net als de dashboard-frontend (`stories_screen.dart`)
         * op `issueType == "STORY"`.
         */
        internal fun parseStories(root: JsonNode): FactoryStoriesResult {
            val mergedKeys = root.path("mergedStoryKeys").map { it.asText() }.toSet()
            val stories = root.path("issues")
                .filter { it.path("issueType").asText() == "STORY" }
                .map { issue ->
                    val fields = issue.path("fields")
                    FactoryStory(
                        key = issue.path("key").asText(),
                        summary = issue.path("summary").asText(),
                        phase = fields.path("storyPhase").asText(null),
                        paused = fields.path("paused").asBoolean(false),
                        error = fields.path("error").asText(null),
                        merged = issue.path("key").asText() in mergedKeys,
                    )
                }
            return FactoryStoriesResult(stories)
        }

        /** Zet de ruwe `/api/v1/my-actions`-JSON om naar een platte lijst [FactoryActionItem]s. */
        internal fun parseMyActions(root: JsonNode): FactoryMyActionsResult {
            val items = root.path("groups").flatMap { group ->
                val storyKey = group.path("storyKey").asText()
                val storySummary = group.path("storySummary").asText()
                group.path("items").map { item ->
                    FactoryActionItem(
                        storyKey = storyKey,
                        storySummary = storySummary,
                        question = item.path("question").asText(null),
                    )
                }
            }
            return FactoryMyActionsResult(items)
        }
    }
}
