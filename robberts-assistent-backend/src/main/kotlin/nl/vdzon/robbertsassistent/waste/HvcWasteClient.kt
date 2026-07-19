package nl.vdzon.robbertsassistent.waste

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

/**
 * Echte afvalkalender via de (niet-officieel gedocumenteerde, maar publiek bevraagbare) HVC
 * Groep REST-API (inzamelkalender.hvcgroep.nl) — gratis, geen API-key. HVC is de
 * afvalinzamelaar voor Heemskerk. Endpoint/response-vorm bepaald aan de hand van de
 * open-source Home Assistant-integratie `cyberjunky/home-assistant-hvcgroep`.
 *
 * Werkwijze: 1) postcode+huisnummer → BAG-ID (de API geeft alle huisnummer-varianten terug,
 * huisletter moet je er zelf uitfilteren), 2) BAG-ID → lijst afvalstromen. Alleen items met een
 * ingevulde `ophaaldatum` zijn echte ophaalmomenten (de rest is informatieve content, bv.
 * "wat mag er wel/niet bij").
 */
@Component
class HvcWasteClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : WasteClient {

    private val objectMapper = jacksonObjectMapper()

    override fun upcomingPickups(): WasteSchedule =
        runCatching {
            val bagId = fetchBagId()
                ?: return WasteSchedule(emptyList(), "Kon geen BAG-ID vinden voor $POSTAL_CODE $HOUSE_NUMBER$HOUSE_LETTER.")
            val request = HttpRequest.newBuilder(URI.create(WASTE_URL.format(bagId)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                WasteSchedule(emptyList(), "Kon de afvalkalender niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseSchedule(objectMapper.readTree(response.body()))
            }
        }.getOrElse { WasteSchedule(emptyList(), "Kon de afvalkalender niet ophalen: ${it.message}") }

    private fun fetchBagId(): String? {
        val request = HttpRequest.newBuilder(URI.create(BAGID_URL.format(POSTAL_CODE, HOUSE_NUMBER)))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null
        val candidates = objectMapper.readTree(response.body())
        if (candidates.size() == 0) return null
        val match = candidates.firstOrNull { it.path("huisletter").asText().equals(HOUSE_LETTER, ignoreCase = true) }
            ?: candidates.first()
        return match.path("bagId").asText().takeIf { it.isNotBlank() }
    }

    internal companion object {
        // Robberts huisadres (Heemskerk) — geen secret, zie CLAUDE.md §5 ("Config is niet geheim").
        private const val POSTAL_CODE = "1961GA"
        private const val HOUSE_NUMBER = "13"
        private const val HOUSE_LETTER = "A"
        private const val BAGID_URL = "https://inzamelkalender.hvcgroep.nl/rest/adressen/%s-%s"
        private const val WASTE_URL = "https://inzamelkalender.hvcgroep.nl/rest/adressen/%s/afvalstromen"

        /** Zet de ruwe HVC-JSON om naar een oplopende lijst [WastePickup]s (alleen items met een echte ophaaldatum). */
        internal fun parseSchedule(root: JsonNode): WasteSchedule {
            if (!root.isArray) return WasteSchedule(emptyList(), "HVC gaf geen afvalkalender terug.")
            val pickups = root.mapNotNull { item ->
                val dateText = item.path("ophaaldatum").takeIf { !it.isMissingNode && !it.isNull }?.asText()
                val title = item.path("title").asText()
                if (dateText.isNullOrBlank() || title.isBlank()) {
                    null
                } else {
                    runCatching { WastePickup(title, LocalDate.parse(dateText)) }.getOrNull()
                }
            }.sortedBy { it.date }
            return WasteSchedule(pickups)
        }
    }
}
