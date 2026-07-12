package nl.vdzon.robbertsassistent.assistant.ai

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
        // Iets ruimer dan voor het actuele-rapport nodig zou zijn — de voorspellingspagina
        // bevat meerdere dagen/tijdstippen, dus die heeft meer tekst nodig om bruikbaar te zijn.
        private const val MAX_LENGTH = 6000

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
