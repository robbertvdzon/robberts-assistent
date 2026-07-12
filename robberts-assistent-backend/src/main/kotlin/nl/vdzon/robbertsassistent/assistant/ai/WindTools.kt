package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Geeft de chat-assistent toegang tot actuele windmetingen + -voorspellingen bij IJmuiden. Haalt de ruwe pagina op
 * en geeft platte tekst terug — geen HTML-scraping met CSS-selectors, want die breekt bij de
 * eerstvolgende layout-wijziging. Het model leest de waarde zelf uit de tekst, net zoals een mens
 * de pagina zou lezen.
 *
 * Bekende beperkingen:
 * - waterinfo.rws.nl rendert de meetwaarden via JavaScript — een kale HTTP-GET geeft dus alleen
 *   een laadscherm terug, geen bruikbare data (windfinder.com werkt wel, de waarde staat daar al
 *   in de server-gerenderde HTML). Zie de system prompt in [AiConfig] voor de fallback-instructie.
 *   Een structurele fix zou de officiële Rijkswaterstaat-waterwebservices-API vereisen (locatie-/
 *   parametercodes opzoeken via de metadata-catalogus) — bewust niet in deze eerste versie.
 * - De windfinder-voorspelling (`getWindForecastWindfinderIJmuiden`) dekt in de statische HTML
 *   maar 2 dagen (vandaag/morgen); de uitgebreide 10-dagen-tabel op de site laadt via JavaScript.
 *   Ontdekt via een echte vraag ("hoeveel wind de komende dagen in de avond") die de AI liet
 *   weigeren i.p.v. te antwoorden met de wél beschikbare 2 dagen — zie de tool-description.
 */
@Component
class WindTools(private val httpClient: HttpClient = HttpClient.newHttpClient()) {

    private val objectMapper = jacksonObjectMapper()

    @Tool(
        description = "Haal de actuele windpagina van Rijkswaterstaat (waterinfo.rws.nl) op voor " +
            "IJmuiden buitenhaven. Deze pagina laadt data via JavaScript; als de tekst geen " +
            "windwaarde bevat (bv. alleen een laadscherm), gebruik dan getWindWindfinderIJmuiden.",
    )
    fun getWindWaterinfoIJmuiden(): String = fetchText(WATERINFO_URL)

    @Tool(
        description = "Haal het actuele windrapport van windfinder.com op voor IJmuiden " +
            "(windsnelheid, windrichting, tijdstip van de meting).",
    )
    fun getWindWindfinderIJmuiden(): String = fetchText(WINDFINDER_URL)

    @Tool(
        description = "Haal de windvoorspelling van windfinder.com op voor IJmuiden (windsnelheid, " +
            "windstoten, windrichting en temperatuur per tijdstip). LET OP: dit geeft alleen vandaag " +
            "en morgen (2 dagen) — de uitgebreide 10-dagen-voorspelling op de site laadt via " +
            "JavaScript en zit niet in deze data. Vraagt iemand naar 'de komende dagen' of meerdere " +
            "dagen vooruit, beantwoord dan gewoon met de dagen die je WEL hebt (vandaag/morgen) en " +
            "meld expliciet dat je verder dan morgen geen voorspelling kunt ophalen — geef nooit een " +
            "leeg 'kon niet ophalen'-antwoord terwijl je wel bruikbare data hebt.",
    )
    fun getWindForecastWindfinderIJmuiden(): String = fetchText(WINDFINDER_FORECAST_URL)

    @Tool(
        description = "Haal een 7-daagse uurvoorspelling (elke 3 uur) van Open-Meteo op voor IJmuiden " +
            "(windsnelheid, windstoten, windrichting). Gebruik dit ALLEEN voor dagen die " +
            "getWindForecastWindfinderIJmuiden niet dekt, dus vanaf overmorgen — voor vandaag/morgen " +
            "is windfinder nauwkeuriger voor deze kustlocatie. Vergelijking met windfinder's " +
            "Superforecast (2026-07-12) liet zien dat Open-Meteo's 'windsnelheid' structureel lager " +
            "uitkomt dan windfinder's sustained-waarde, terwijl Open-Meteo's 'windstoten' juist " +
            "aardig overeenkomt met windfinder's 'max'-kolom (waarschijnlijk een ander " +
            "middelingsinterval, niet per se een minder accuraat model). Vermeld bij gebruik van " +
            "deze bron dat het een ander/minder kustspecifiek model is dan windfinder.",
    )
    fun getWindForecastOpenMeteoIJmuiden(): String = fetchOpenMeteoForecast()

    private fun fetchOpenMeteoForecast(): String =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(OPEN_METEO_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                "Kon Open-Meteo niet ophalen (HTTP ${response.statusCode()})."
            } else {
                openMeteoResponseToText(objectMapper.readTree(response.body()))
            }
        }.getOrElse { "Kon Open-Meteo niet ophalen: ${it.message}" }

    private fun fetchText(url: String): String =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; RobbertsAssistent/1.0)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                "Kon $url niet ophalen (HTTP ${response.statusCode()})."
            } else {
                htmlToPlainText(response.body())
            }
        }.getOrElse { "Kon $url niet ophalen: ${it.message}" }

    internal companion object {
        private const val WATERINFO_URL = "https://waterinfo.rws.nl/publiek/wind/ijmuiden.buitenhaven/details"
        private const val WINDFINDER_URL = "https://www.windfinder.com/report/ijmuiden"
        private const val WINDFINDER_FORECAST_URL = "https://www.windfinder.com/forecast/ijmuiden"
        // IJmuiden Zuidpier-coördinaten. Gratis, geen API-key nodig (Open-Meteo).
        private const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=52.4614&longitude=4.5552" +
            "&hourly=wind_speed_10m,wind_gusts_10m,wind_direction_10m" +
            "&forecast_days=7&wind_speed_unit=kn&timezone=Europe%2FAmsterdam"
        private val OPEN_METEO_CHECKPOINT_HOURS = setOf(8, 11, 14, 17, 20, 23)
        private val COMPASS_POINTS = listOf(
            "N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO",
            "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW",
        )
        // Iets ruimer dan voor het actuele-rapport nodig zou zijn — de voorspellingspagina
        // bevat meerdere dagen/tijdstippen, dus die heeft meer tekst nodig om bruikbaar te zijn.
        private const val MAX_LENGTH = 6000

        /** Converteert graden (0-360) naar een 16-punts kompasrichting (Nederlands, zoals windfinder). */
        internal fun compassPoint(degrees: Double): String =
            COMPASS_POINTS[(((degrees % 360) / 22.5) + 0.5).toInt() % 16]

        /**
         * Compacte tekstsamenvatting van de Open-Meteo-JSON-response: per dag een paar vaste
         * checkpoints (elke 3 uur) i.p.v. alle 168 uren — genoeg voor "in de avond"-vragen zonder
         * de tool-response nodeloos op te blazen.
         */
        internal fun openMeteoResponseToText(root: JsonNode): String {
            val hourly = root.path("hourly")
            val times = hourly.path("time").map { it.asText() }
            val speeds = hourly.path("wind_speed_10m").map { it.asDouble() }
            val gusts = hourly.path("wind_gusts_10m").map { it.asDouble() }
            val dirs = hourly.path("wind_direction_10m").map { it.asDouble() }
            if (times.isEmpty()) return "Open-Meteo gaf geen voorspellingsdata terug."

            val lines = times.indices.filter { i ->
                val hour = times[i].takeLast(5).substringBefore(":").toIntOrNull()
                hour in OPEN_METEO_CHECKPOINT_HOURS
            }.map { i ->
                val (date, time) = times[i].split("T")
                "$date $time: ${speeds[i]} kts (windstoten ${gusts[i]} kts), ${compassPoint(dirs[i])}"
            }
            return lines.joinToString("\n")
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
            return decoded.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)
        }
    }
}
