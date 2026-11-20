package nl.vdzon.robbertsassistent.zonneplan

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Echte koppeling met de Zonneplan-integratie in Home Assistant (eigen thuis-cluster, REST-API
 * met een long-lived access token — zie developers.home-assistant.io/docs/api/rest). Home
 * Assistant heeft geen losse "gisteren"-sensor voor de zonneopbrengst, dus [CURRENT_POWER_ENTITY]
 * levert het huidige omvormervermogen (ter info) en [DAILY_YIELD_ENTITY] (een dagelijks
 * resettende `total_increasing`-sensor) wordt via de history-API bevraagd: de hoogste waarde die
 * die sensor gisteren had — vlak vóór de reset naar 0 om middernacht — ís de dagopbrengst van
 * gisteren.
 *
 * De route (`*.apps.sno.lab.vdzon.com`) gebruikt het zelfondertekende standaard-routercertificaat
 * van dit thuis-cluster (ingress-operator-CA, niet publiek vertrouwd) — vandaar [buildSslContext],
 * die precies díe CA vertrouwt in plaats van alle TLS-verificatie uit te schakelen (zelfde
 * werkwijze als `openshift.KubernetesApiOpenShiftClient.buildSslContext`, maar dan voor een
 * gebundelde resource in plaats van het in-cluster ServiceAccount-CA-bestand). Roteert deze
 * routercertificaat-CA ooit, ververs dan `home-assistant-router-ca.pem` met:
 *   openssl s_client -connect home-assistant.apps.sno.lab.vdzon.com:443 \
 *     -servername home-assistant.apps.sno.lab.vdzon.com -showcerts </dev/null 2>/dev/null \
 *     | awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/' — bewaar het TWEEDE (self-signed) certificaat.
 */
class HomeAssistantZonneplanClient(
    private val baseUrl: String,
    private val token: String,
    private val currentPowerEntityId: String = CURRENT_POWER_ENTITY,
    private val dailyYieldEntityId: String = DAILY_YIELD_ENTITY,
    private val httpClient: HttpClient = HttpClient.newBuilder().sslContext(buildSslContext()).build(),
) : ZonneplanClient {

    private val objectMapper = jacksonObjectMapper()
    private val trimmedBaseUrl = baseUrl.trimEnd('/')

    override fun status(): SolarStatusResult =
        runCatching {
            val currentPower = parseCurrentPower(objectMapper.readTree(getState(currentPowerEntityId).body()))
            val yesterdayYield = parseYesterdayMaxKwh(objectMapper.readTree(getYesterdayHistory(dailyYieldEntityId).body()))
            SolarStatusResult(currentPowerWatt = currentPower, yesterdayYieldKwh = yesterdayYield)
        }.getOrElse { SolarStatusResult(null, null, "Kon zonnepanelen-status niet ophalen: ${it.message}") }

    /**
     * Home Assistant bewaart de gewone geschiedenis maar zo'n tien dagen; de langetermijn-
     * statistieken (per dag, onbeperkt) zijn er alleen via de WebSocket-API.
     */
    override fun dailyYields(since: LocalDate): Map<LocalDate, Double> {
        val start = since.atStartOfDay(ZONE).toInstant()
        val request = objectMapper.writeValueAsString(
            mapOf(
                "id" to STATISTICS_REQUEST_ID,
                "type" to "recorder/statistics_during_period",
                "start_time" to start.toString(),
                "statistic_ids" to listOf(dailyYieldEntityId),
                "period" to "day",
                "types" to listOf("change"),
            ),
        )
        return parseDailyYields(webSocketResult(request), dailyYieldEntityId)
    }

    /** Eén vraag over de WebSocket: aanmelden met het token, de vraag sturen, het antwoord lezen. */
    private fun webSocketResult(request: String): JsonNode {
        val messages = LinkedBlockingQueue<String>()
        val listener = object : WebSocket.Listener {
            private val buffer = StringBuilder()

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                buffer.append(data)
                if (last) {
                    messages.put(buffer.toString())
                    buffer.setLength(0)
                }
                webSocket.request(1)
                return null
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                messages.put(objectMapper.writeValueAsString(mapOf("type" to "fout", "message" to (error.message ?: "verbinding verbroken"))))
            }
        }
        val uri = URI.create(trimmedBaseUrl.replaceFirst("http", "ws") + "/api/websocket")
        val socket = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
            .buildAsync(uri, listener).get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        try {
            fun next(): JsonNode = objectMapper.readTree(
                messages.poll(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("Home Assistant antwoordde niet op tijd."),
            )
            fun expect(type: String) {
                val message = next()
                if (message.path("type").asText() != type) {
                    error("Home Assistant gaf '${message.path("type").asText()}' in plaats van '$type'.")
                }
            }
            expect("auth_required")
            socket.sendText(objectMapper.writeValueAsString(mapOf("type" to "auth", "access_token" to token)), true)
                .get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            expect("auth_ok")
            socket.sendText(request, true).get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            while (true) {
                val message = next()
                if (message.path("type").asText() == "fout") error("Home Assistant-verbinding: ${message.path("message").asText()}")
                if (message.path("id").asInt() != STATISTICS_REQUEST_ID || message.path("type").asText() != "result") continue
                if (!message.path("success").asBoolean()) {
                    error("Home Assistant weigerde de statistieken: ${message.path("error").path("message").asText()}")
                }
                return message.path("result")
            }
        } finally {
            socket.abort()
        }
    }

    private fun getState(entityId: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("$trimmedBaseUrl/api/states/$entityId"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("Home Assistant gaf HTTP ${response.statusCode()} terug voor $entityId.")
        return response
    }

    private fun getYesterdayHistory(entityId: String): HttpResponse<String> {
        val zone = ZoneId.of("Europe/Amsterdam")
        val yesterday = LocalDate.now(zone).minusDays(1)
        val start = yesterday.atStartOfDay(zone).toInstant()
        val end = yesterday.plusDays(1).atStartOfDay(zone).toInstant()
        val encodedEntity = URLEncoder.encode(entityId, StandardCharsets.UTF_8)
        val url = "$trimmedBaseUrl/api/history/period/$start?filter_entity_id=$encodedEntity&end_time=$end&minimal_response=true"
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("Home Assistant history-API gaf HTTP ${response.statusCode()} terug voor $entityId.")
        return response
    }

    internal companion object {
        const val CURRENT_POWER_ENTITY = "sensor.zonneplan_one_optimized_omvormer_laatst_gemeten_waarde"
        const val DAILY_YIELD_ENTITY = "sensor.zonneplan_opbrengst_vandaag"
        private const val ROUTER_CA_RESOURCE = "/home-assistant-router-ca.pem"

        private const val STATISTICS_REQUEST_ID = 1
        private const val WS_TIMEOUT_SECONDS = 20L
        private val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /**
         * Zet het antwoord van `recorder/statistics_during_period` (per entiteit een lijst met
         * `start` in milliseconden en de `change` over die dag) om naar kWh per dag.
         */
        internal fun parseDailyYields(result: JsonNode, entityId: String): Map<LocalDate, Double> =
            result.path(entityId).mapNotNull { row ->
                val change = row.path("change").takeIf { it.isNumber }?.asDouble()?.takeIf { it >= 0 } ?: return@mapNotNull null
                val start = row.path("start").takeIf { it.isNumber }?.asLong() ?: return@mapNotNull null
                Instant.ofEpochMilli(start).atZone(ZONE).toLocalDate() to Math.round(change * 100) / 100.0
            }.toMap()

        private val UNAVAILABLE_STATES = setOf("unknown", "unavailable", "")

        /** Bouwt een `SSLContext` die alleen de gebundelde router-CA vertrouwt, zie klasse-KDoc. */
        internal fun buildSslContext(): SSLContext {
            val cert = HomeAssistantZonneplanClient::class.java.getResourceAsStream(ROUTER_CA_RESOURCE).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("home-assistant-router-ca", cert)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
            return SSLContext.getInstance("TLS").apply { init(null, trustManagerFactory.trustManagers, null) }
        }

        /** Zet de `/api/states/{entity}`-respons om naar een afgerond vermogen in Watt. */
        internal fun parseCurrentPower(root: JsonNode): Int? =
            root.path("state").asText().takeIf { it !in UNAVAILABLE_STATES }?.toDoubleOrNull()?.let { Math.round(it).toInt() }

        /**
         * Zet de `/api/history/period`-respons (een array van arrays met state-histories, één per
         * opgevraagde entity) om naar de hoogste geldige waarde van de dag — bij een dagelijks
         * resettende `total_increasing`-sensor is dat de dagopbrengst vlak vóór de reset.
         */
        internal fun parseYesterdayMaxKwh(root: JsonNode): Double? =
            root.firstOrNull()
                ?.mapNotNull { it.path("state").asText().takeIf { state -> state !in UNAVAILABLE_STATES }?.toDoubleOrNull() }
                ?.maxOrNull()
    }
}
