package nl.vdzon.robbertsassistent.watches

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

interface WatchPageFetcher {
    fun fetch(url: String): String
}

@Component
class JdkWatchPageFetcher(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : WatchPageFetcher {
    override fun fetch(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "RobbertsAssistent-Watch/1.0")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            error("Webpagina gaf HTTP ${response.statusCode()}")
        }
        val bytes = response.body().use { it.readNBytes(MAX_HTML_BYTES + 1) }
        if (bytes.size > MAX_HTML_BYTES) error("Webpagina is groter dan de toegestane limiet")
        return htmlToText(String(bytes, StandardCharsets.UTF_8))
    }

    internal fun htmlToText(html: String): String =
        html
            .replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1\\s*>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr)>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex(" *\\n(?: *\\n)* *"), "\n")
            .trim()
            .take(MAX_TEXT_CHARS)

    companion object {
        const val MAX_HTML_BYTES = 1_000_000
        const val MAX_TEXT_CHARS = 20_000
    }
}
