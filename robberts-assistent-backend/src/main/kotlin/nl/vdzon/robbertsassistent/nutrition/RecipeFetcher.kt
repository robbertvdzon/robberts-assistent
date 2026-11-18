package nl.vdzon.robbertsassistent.nutrition

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Haalt de tekst van een receptpagina op, zodat de AI de ingrediënten en porties kan lezen. */
interface RecipeFetcher {
    /** Platte tekst van de pagina, of `null` als ophalen niet lukt. Gooit geen exception. */
    fun fetch(url: String): String?
}

/** Links (http/https) in een notitie; hooguit [MAX_LINKS], zodat een notitie de analyse niet kan ophouden. */
object RecipeLinks {
    private val URL = Regex("""https?://[^\s<>"']+""")
    private const val MAX_LINKS = 2

    fun find(note: String): List<String> =
        URL.findAll(note).map { it.value.trimEnd('.', ',', ')', ';') }.distinct().take(MAX_LINKS).toList()

    /** HTML naar leesbare tekst: scripts en opmaak weg, witruimte samengevoegd, begrensd in lengte. */
    fun toText(html: String, maxChars: Int = 12_000): String =
        html
            .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(br|/p|/li|/h[1-6]|/tr|/div)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&frac12;", "½").replace("&frac14;", "¼")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .lines().map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(maxChars)
}

/** Zelfde gedachte als `watches.JdkWatchPageFetcher`: server-HTML ophalen, begrensd, zonder scripts uit te voeren. */
@Component
class JdkRecipeFetcher : RecipeFetcher {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun fetch(url: String): String? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "Mozilla/5.0 (compatible; receptlezer)")
            .header("Accept", "text/html")
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            logger.info("Recept ophalen gaf HTTP {}", response.statusCode())
            return null
        }
        val html = response.body().use { String(it.readNBytes(MAX_BYTES), Charsets.UTF_8) }
        RecipeLinks.toText(html).takeIf { it.length > 200 }
    }.onFailure { logger.info("Recept ophalen mislukt: {}", it.message) }.getOrNull()

    private companion object {
        const val MAX_BYTES = 1_000_000
    }
}
