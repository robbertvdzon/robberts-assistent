package nl.vdzon.robbertsassistent.strava

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.notifier.Notifier
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
 * Wisselt een langlevende OAuth refresh-token in voor korte access-tokens (Strava token-endpoint)
 * en cachet die in memory tot ~1 min voor verval. Zelfde patroon als
 * [nl.vdzon.robbertsassistent.google.GoogleOAuthService]: de refresh-token staat in de secrets,
 * de access-token wordt nergens persistent opgeslagen.
 *
 * Bij een ongeldige refresh-token (ingetrokken/verlopen) stuurt de service één Telegram-alert
 * zodat het niet stil faalt; herstel is eenmalig opnieuw de OAuth-consent doorlopen.
 */
class StravaOAuthService(
    private val clientId: String,
    private val clientSecret: String,
    private val refreshToken: String,
    private val notifier: Notifier,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    @Volatile
    private var cached: CachedToken? = null

    /** Een geldige access-token voor de Strava-API; ververst automatisch wanneer (bijna) verlopen. */
    fun accessToken(): String {
        val now = Instant.now()
        cached?.let { if (it.expiresAt.isAfter(now.plusSeconds(60))) return it.value }
        return refresh(now)
    }

    @Synchronized
    private fun refresh(now: Instant): String {
        cached?.let { if (it.expiresAt.isAfter(now.plusSeconds(60))) return it.value }

        val form = mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        ).map { (k, v) -> "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }.joinToString("&")

        val request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            if (response.body().contains("invalid_grant") || response.body().contains("invalid_token")) {
                logger.error("Strava refresh-token ongeldig — opnieuw autoriseren nodig")
                runCatching { notifier.send("⚠️ Strava-koppeling verlopen: log opnieuw in om trainingen te blijven zien.") }
            }
            throw IllegalStateException("Strava token-refresh faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }

        val json = objectMapper.readTree(response.body())
        val accessToken = json.path("access_token").asText(null)
            ?: throw IllegalStateException("Strava token-response bevat geen access_token")
        val expiresAtEpoch = json.path("expires_at").asLong(now.epochSecond + 3600)
        val token = CachedToken(accessToken, Instant.ofEpochSecond(expiresAtEpoch))
        cached = token
        logger.debug("Nieuwe Strava access-token opgehaald (geldig tot {})", token.expiresAt)
        return token.value
    }

    private data class CachedToken(val value: String, val expiresAt: Instant)

    private companion object {
        const val TOKEN_URL = "https://www.strava.com/oauth/token"
    }
}
