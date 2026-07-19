package nl.vdzon.robbertsassistent.news

import java.time.Instant

/** Eén nieuwsitem uit een RSS-feed. */
data class NewsItem(
    val title: String,
    val link: String,
    val publishedAt: Instant?,
)

/**
 * Resultaat van een nieuws-ophaal-poging. Bij een netwerk-/parsefout is [items] leeg en [error]
 * gezet — de aanroeper (`NewsTools`) degradeert dan netjes naar een duidelijke melding in plaats
 * van te crashen.
 */
data class NewsFeed(
    val items: List<NewsItem>,
    val error: String? = null,
)

/**
 * Laatste nieuwskoppen uit een RSS-feed (standaard: NOS Algemeen). Fase 0 (keyless, geen secret
 * nodig): [RssNewsClient] is de enige, altijd-actieve implementatie. [StubNewsClient] bestaat
 * alleen voor tests, zodat tools zonder netwerk-call getest kunnen worden.
 */
interface NewsClient {
    /** De meest recente nieuwsitems, nieuwste eerst, tot maximaal [maxItems]. */
    fun latestHeadlines(maxItems: Int = 10): NewsFeed
}
