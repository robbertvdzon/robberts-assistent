package nl.vdzon.robbertsassistent.automower

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * Echte koppeling met de Husqvarna Automower Connect API. Gebruikt `client_credentials` (een
 * geregistreerde app-key/secret via developer.husqvarnagroup.cloud) — geen los OAuth-consent
 * nodig, in tegenstelling tot Google Agenda/Docs. Het access-token wordt in-memory gecachet tot
 * vlak vóór verval (net als [nl.vdzon.robbertsassistent.google.GoogleOAuthService]), en telkens
 * opnieuw opgevraagd met de vaste, niet-verlopende app-key/secret.
 */
class HusqvarnaAutomowerClient(
    private val appKey: String,
    private val appSecret: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : AutomowerClient {

    private val objectMapper = jacksonObjectMapper()

    @Volatile
    private var cachedToken: CachedToken? = null

    override fun status(): MowerStatusResult =
        runCatching {
            val token = accessToken() ?: return MowerStatusResult(emptyList(), "Kon geen Husqvarna-token ophalen.")
            val response = getMowers(token)
            if (response.statusCode() !in 200..299) {
                return MowerStatusResult(emptyList(), "Kon maaierstatus niet ophalen (HTTP ${response.statusCode()}).")
            }
            MowerStatusResult(parseMowers(objectMapper.readTree(response.body())))
        }.getOrElse { MowerStatusResult(emptyList(), "Kon maaierstatus niet ophalen: ${it.message}") }

    override fun startMowing(durationMinutes: Int): MowerActionResult =
        performAction("""{"data":{"type":"Start","attributes":{"duration":$durationMinutes}}}""")

    override fun park(): MowerActionResult = performAction("""{"data":{"type":"ParkUntilNextSchedule"}}""")

    private fun performAction(body: String): MowerActionResult =
        runCatching {
            val token = accessToken() ?: return MowerActionResult(false, "Kon geen Husqvarna-token ophalen.")
            val mowersResponse = getMowers(token)
            if (mowersResponse.statusCode() !in 200..299) {
                return MowerActionResult(false, "Kon geen maaier vinden (HTTP ${mowersResponse.statusCode()}).")
            }
            val mowerId = objectMapper.readTree(mowersResponse.body()).path("data").firstOrNull()?.path("id")?.asText()
                ?: return MowerActionResult(false, "Geen maaier gevonden op dit account.")

            val request = HttpRequest.newBuilder(URI.create(ACTIONS_URL.format(mowerId)))
                .header("Authorization", "Bearer $token")
                .header("Authorization-Provider", "husqvarna")
                .header("X-Api-Key", appKey)
                .header("Content-Type", "application/vnd.api+json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                MowerActionResult(true)
            } else {
                MowerActionResult(false, "Husqvarna wees de actie af (HTTP ${response.statusCode()}).")
            }
        }.getOrElse { MowerActionResult(false, "Kon de actie niet uitvoeren: ${it.message}") }

    private fun getMowers(token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(MOWERS_URL))
            .header("Authorization", "Bearer $token")
            .header("Authorization-Provider", "husqvarna")
            .header("X-Api-Key", appKey)
            .header("Accept", "application/vnd.api+json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /** Vraagt een nieuw access-token op via `client_credentials`; cachet 'm tot vlak vóór verval. */
    private fun accessToken(): String? {
        cachedToken?.let { cached -> if (cached.expiresAt.isAfter(Instant.now().plusSeconds(TOKEN_EXPIRY_MARGIN_SECONDS))) return cached.value }

        val encode = { value: String -> URLEncoder.encode(value, StandardCharsets.UTF_8) }
        val body = "grant_type=client_credentials&client_id=${encode(appKey)}&client_secret=${encode(appSecret)}"
        val request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null

        val json = objectMapper.readTree(response.body())
        val token = json.path("access_token").asText().takeIf { it.isNotBlank() } ?: return null
        val expiresIn = json.path("expires_in").asLong(DEFAULT_TOKEN_TTL_SECONDS)
        cachedToken = CachedToken(token, Instant.now().plusSeconds(expiresIn))
        return token
    }

    private data class CachedToken(val value: String, val expiresAt: Instant)

    internal companion object {
        private const val TOKEN_URL = "https://api.authentication.husqvarnagroup.dev/v1/oauth2/token"
        private const val MOWERS_URL = "https://api.amc.husqvarna.dev/v1/mowers"
        private const val ACTIONS_URL = "https://api.amc.husqvarna.dev/v1/mowers/%s/actions"
        private const val TOKEN_EXPIRY_MARGIN_SECONDS = 30L
        private const val DEFAULT_TOKEN_TTL_SECONDS = 3600L

        /** Zet de JSON:API-respons van `/v1/mowers` om naar een lijst [MowerStatus]. */
        internal fun parseMowers(root: JsonNode): List<MowerStatus> =
            root.path("data").map { mower ->
                val attributes = mower.path("attributes")
                val system = attributes.path("system")
                val mowerAttrs = attributes.path("mower")
                val battery = attributes.path("battery")
                val metadata = attributes.path("metadata")
                MowerStatus(
                    name = system.path("name").asText("onbekend"),
                    model = system.path("model").asText("onbekend"),
                    mode = mowerAttrs.path("mode").asText("ONBEKEND"),
                    activity = mowerAttrs.path("activity").asText("ONBEKEND"),
                    state = mowerAttrs.path("state").asText("ONBEKEND"),
                    batteryPercent = battery.path("batteryPercent").takeIf { !it.isMissingNode }?.asInt(),
                    errorCode = mowerAttrs.path("errorCode").asInt(0),
                    connected = metadata.path("connected").asBoolean(false),
                )
            }
    }
}
