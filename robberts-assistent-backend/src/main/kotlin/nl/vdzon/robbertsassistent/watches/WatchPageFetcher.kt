package nl.vdzon.robbertsassistent.watches

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Haalt een webpagina op en converteert de HTML naar platte tekst voor AI-analyse.
 * Eigen kopie van htmlToPlainText (ModulithArchitectureTest-bewaking — mag niet uit andere module
 * importeren).
 */
open class WatchPageFetcher(private val httpClient: HttpClient = HttpClient.newHttpClient()) {

    open fun fetch(url: String): FetchResult =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; RobbertsAssistent/1.0)")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                FetchResult.Error("HTTP ${response.statusCode()}")
            } else {
                FetchResult.Success(htmlToPlainText(response.body()))
            }
        }.getOrElse { FetchResult.Error(it.message ?: "Onbekende fout") }

    companion object {
        private const val MAX_LENGTH = 8000

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
                .replace("&#39;", "'")
                .replace("&apos;", "'")
            return decoded.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)
        }
    }

    sealed class FetchResult {
        data class Success(val text: String) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }
}
