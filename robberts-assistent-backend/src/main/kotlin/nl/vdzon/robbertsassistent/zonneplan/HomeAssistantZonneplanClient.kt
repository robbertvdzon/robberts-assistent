package nl.vdzon.robbertsassistent.zonneplan

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
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
