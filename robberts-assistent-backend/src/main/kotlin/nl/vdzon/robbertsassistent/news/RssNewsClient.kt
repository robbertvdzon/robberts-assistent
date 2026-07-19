package nl.vdzon.robbertsassistent.news

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Echte nieuwsfeed via RSS (standaard: NOS Algemeen, feeds.nos.nl) — gratis, geen API-key. Geen
 * XML-library nodig: gebruikt de in de JDK ingebouwde `javax.xml.parsers.DocumentBuilderFactory`.
 */
@Component
class RssNewsClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : NewsClient {

    override fun latestHeadlines(maxItems: Int): NewsFeed =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(FEED_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                NewsFeed(emptyList(), "Kon het nieuws niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseFeed(response.body()).let { it.copy(items = it.items.take(maxItems)) }
            }
        }.getOrElse { NewsFeed(emptyList(), "Kon het nieuws niet ophalen: ${it.message}") }

    internal companion object {
        private const val FEED_URL = "https://feeds.nos.nl/nosnieuwsalgemeen"

        /** Zet de ruwe RSS-XML om naar een lijst [NewsItem]s. */
        internal fun parseFeed(xml: String): NewsFeed =
            runCatching {
                val document = newSafeDocumentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
                val itemNodes = document.getElementsByTagName("item")
                val items = (0 until itemNodes.length).mapNotNull { i ->
                    val element = itemNodes.item(i) as? Element ?: return@mapNotNull null
                    val title = element.textOf("title") ?: return@mapNotNull null
                    val link = element.textOf("link") ?: return@mapNotNull null
                    NewsItem(title = title, link = link, publishedAt = element.textOf("pubDate")?.let(::parsePubDate))
                }
                NewsFeed(items)
            }.getOrElse { NewsFeed(emptyList(), "Kon de nieuws-XML niet lezen: ${it.message}") }

        private fun parsePubDate(raw: String): java.time.Instant? =
            runCatching { OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()

        private fun Element.textOf(tag: String): String? =
            getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        /** Hardened tegen XXE: geen DOCTYPE/externe entities bij het parsen van (externe) RSS-XML. */
        private fun newSafeDocumentBuilderFactory(): DocumentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
    }
}
