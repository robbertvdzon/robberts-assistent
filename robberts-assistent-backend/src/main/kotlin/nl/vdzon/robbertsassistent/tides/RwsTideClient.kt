package nl.vdzon.robbertsassistent.tides

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Echte getijvoorspelling via de RWS WaterWebservices (waterwebservices.rijkswaterstaat.nl,
 * inmiddels verhuisd naar ddapi20-waterwebservices.rijkswaterstaat.nl) — gratis, geen API-key.
 * Grootheid WATHTE (waterhoogte t.o.v. NAP), locatie "ijmuiden.buitenhaven" (zelfde als de
 * waterinfo-URL in WindTools). De service geeft per aanvraag meerdere reeksen terug
 * (`ProcesType`: "meting" = actuele meting, "verwachting" = voorspelling mét weersinvloed,
 * "astronomisch" = pure getijberekening zonder wind/opzet); wij gebruiken "verwachting", de
 * praktisch bruikbaarste voor "kan ik straks kiten/fietsen op het strand".
 */
@Component
class RwsTideClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : TideClient {

    private val objectMapper = jacksonObjectMapper()

    override fun forecast(hours: Int): TideForecast =
        runCatching {
            val now = Instant.now()
            val body = objectMapper.writeValueAsString(requestBody(now, now.plusSeconds(hours * 3600L)))
            val request = HttpRequest.newBuilder(URI.create(OBSERVATIONS_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                TideForecast(emptyList(), emptyList(), "Kon RWS-getij niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseForecast(objectMapper.readTree(response.body()))
            }
        }.getOrElse { TideForecast(emptyList(), emptyList(), "Kon RWS-getij niet ophalen: ${it.message}") }

    internal companion object {
        private const val OBSERVATIONS_URL =
            "https://ddapi20-waterwebservices.rijkswaterstaat.nl/ONLINEWAARNEMINGENSERVICES/OphalenWaarnemingen"
        private const val LOCATION_CODE = "ijmuiden.buitenhaven"
        private const val VERWACHTING_PROCESTYPE = "verwachting"
        // Venster waarbinnen een punt het hoogste/laagste moet zijn om als hoog-/laagwater te
        // gelden — voorkomt dat kleine schommelingen in de voorspellingscurve (en de "agger",
        // een bekend dubbel-laagwater-effect op de Hollandse kust) losse valse extremen opleveren.
        private val EXTREME_WINDOW = Duration.ofHours(3)
        // Twee extremen van hetzelfde type binnen dit venster horen bij elkaar (plateau/ruis) —
        // houd de meest extreme waarde.
        private val MERGE_GAP = Duration.ofHours(4)

        internal fun requestBody(begin: Instant, end: Instant): Map<String, Any> {
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            return mapOf(
                "Locatie" to mapOf("Code" to LOCATION_CODE),
                "AquoPlusWaarnemingMetadata" to mapOf(
                    "AquoMetadata" to mapOf(
                        "Compartiment" to mapOf("Code" to "OW"),
                        "Grootheid" to mapOf("Code" to "WATHTE"),
                    ),
                ),
                "Periode" to mapOf(
                    "Begindatumtijd" to formatter.format(begin.atOffset(ZoneOffset.UTC)),
                    "Einddatumtijd" to formatter.format(end.atOffset(ZoneOffset.UTC)),
                ),
            )
        }

        /** Pakt alleen de "verwachting"-reeks (weersinvloed meegenomen) en leidt hoog-/laagwater af. */
        internal fun parseForecast(root: JsonNode): TideForecast {
            val forecastList = root.path("WaarnemingenLijst").firstOrNull {
                it.path("AquoMetadata").path("ProcesType").asText() == VERWACHTING_PROCESTYPE
            } ?: return TideForecast(emptyList(), emptyList(), "RWS gaf geen getijvoorspelling terug.")

            val levels = forecastList.path("MetingenLijst").map {
                WaterLevel(
                    time = OffsetDateTime.parse(it.path("Tijdstip").asText()).toInstant(),
                    heightCm = it.path("Meetwaarde").path("Waarde_Numeriek").asDouble().toInt(),
                )
            }.sortedBy { it.time }
            if (levels.isEmpty()) return TideForecast(emptyList(), emptyList(), "RWS gaf geen getijvoorspelling terug.")

            return TideForecast(levels, extremesOf(levels))
        }

        /**
         * Vindt hoog-/laagwatermomenten: een punt telt mee als het binnen [EXTREME_WINDOW] rondom
         * het hoogste (of laagste) is. Aaneengesloten treffers (plateaus) worden samengevoegd tot
         * één moment (het middelste), en extremen van hetzelfde type die daarna nog dicht bij
         * elkaar liggen (< [MERGE_GAP]) worden verder samengevoegd tot de meest extreme waarde.
         */
        internal fun extremesOf(levels: List<WaterLevel>): List<TideExtreme> {
            if (levels.size < 3) return emptyList()
            val interval = Duration.between(levels[0].time, levels[1].time).takeIf { !it.isZero } ?: Duration.ofMinutes(10)
            val window = (EXTREME_WINDOW.toMillis() / interval.toMillis()).toInt().coerceAtLeast(1)

            // Het eerste/laatste punt wordt nooit als extreem geteld: aan de rand van de reeks kan
            // het venster niet aan beide kanten volledig zijn, waardoor een punt op een gewone
            // stijgende/dalende flank ten onrechte "extreem" lijkt (er is simpelweg nog geen data
            // voorbij de rand om dat te weerleggen).
            val flags = levels.indices.map { i ->
                if (i == 0 || i == levels.size - 1) {
                    null
                } else {
                    val lo = (i - window).coerceAtLeast(0)
                    val hi = (i + window).coerceAtMost(levels.size - 1)
                    val slice = (lo..hi).map { levels[it].heightCm }
                    when (levels[i].heightCm) {
                        slice.max() -> TideType.HOOGWATER
                        slice.min() -> TideType.LAAGWATER
                        else -> null
                    }
                }
            }

            val runs = mutableListOf<TideExtreme>()
            var i = 0
            while (i < levels.size) {
                val type = flags[i]
                if (type != null) {
                    var j = i
                    while (j + 1 < levels.size && flags[j + 1] == type) j++
                    val mid = (i + j) / 2
                    runs += TideExtreme(levels[mid].time, levels[mid].heightCm, type)
                    i = j + 1
                } else {
                    i++
                }
            }
            return mergeClose(runs)
        }

        private fun mergeClose(extremes: List<TideExtreme>): List<TideExtreme> {
            val merged = mutableListOf<TideExtreme>()
            for (extreme in extremes) {
                val last = merged.lastOrNull()
                if (last != null && last.type == extreme.type && Duration.between(last.time, extreme.time).abs() < MERGE_GAP) {
                    val keepNew = if (extreme.type == TideType.HOOGWATER) {
                        extreme.heightCm > last.heightCm
                    } else {
                        extreme.heightCm < last.heightCm
                    }
                    if (keepNew) merged[merged.lastIndex] = extreme
                } else {
                    merged += extreme
                }
            }
            return merged
        }
    }
}
