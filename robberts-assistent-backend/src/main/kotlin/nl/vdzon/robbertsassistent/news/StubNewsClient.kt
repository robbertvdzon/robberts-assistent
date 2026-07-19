package nl.vdzon.robbertsassistent.news

import java.time.Duration
import java.time.Instant

/**
 * Vaste, deterministische nieuwsitems — puur voor tests, zodat `NewsTools` zonder netwerk-call
 * getest kan worden (zelfde patroon als `StubCalendarClient`). Niet als Spring-bean
 * geregistreerd: [RssNewsClient] is keyless en dus altijd actief.
 */
class StubNewsClient : NewsClient {
    override fun latestHeadlines(maxItems: Int): NewsFeed {
        val now = Instant.now()
        val items = listOf(
            NewsItem("Voorbeeldnieuws één", "https://voorbeeld.nl/1", now),
            NewsItem("Voorbeeldnieuws twee", "https://voorbeeld.nl/2", now.minus(Duration.ofHours(1))),
            NewsItem("Voorbeeldnieuws drie", "https://voorbeeld.nl/3", now.minus(Duration.ofHours(3))),
        )
        return NewsFeed(items.take(maxItems))
    }
}
