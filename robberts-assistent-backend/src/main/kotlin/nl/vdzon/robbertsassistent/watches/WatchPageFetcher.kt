package nl.vdzon.robbertsassistent.watches

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Haalt een pagina op voor [WatchScheduler] en geeft platte tekst terug (HTML gestript), zodat de
 * AI de tekst kan beoordelen — zelfde aanpak als `assistant.ai.WindTools.fetchText`. Een eigen
 * kopie van `htmlToPlainText()`: de variant in `WindTools` is `internal` en dus niet herbruikbaar
 * over modulegrenzen heen (bewaakt door `ModulithArchitectureTest`).
 */
@Component
open class WatchPageFetcher(private val httpClient: HttpClient = HttpClient.newHttpClient()) {

    /**
     * Gooit een exception bij een netwerk-/HTTP-fout — de aanroeper ([WatchScheduler]) vangt dit af.
     * `open` zodat tests een subklasse kunnen maken die de HTTP-call overslaat (zie
     * `WatchSchedulerTest`), zonder de constructor-signatuur te wijzigen.
     */
    open fun fetchPlainText(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (compatible; RobbertsAssistent/1.0)")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Kon $url niet ophalen (HTTP ${response.statusCode()}).")
        }
        return htmlToPlainText(response.body())
    }

    internal companion object {
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
