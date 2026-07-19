package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.news.NewsClient
import nl.vdzon.robbertsassistent.news.NewsItem
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot de laatste algemene nieuwskoppen via [NewsClient] (RSS,
 * standaard NOS Algemeen — keyless, geen secret nodig, zie CLAUDE.md §5).
 */
@Component
class NewsTools(private val newsClient: NewsClient) {

    @Tool(
        description = "Haal de laatste algemene nieuwskoppen op (NOS): titel, tijdstip en link. " +
            "Gebruik dit voor vragen als 'wat is er in het nieuws' of 'geef me het laatste nieuws'.",
    )
    fun getLatestNews(): String {
        val feed = newsClient.latestHeadlines(DEFAULT_MAX_ITEMS)
        feed.error?.let { return it }
        if (feed.items.isEmpty()) return "Geen nieuwsitems gevonden."
        return feed.items.joinToString("\n") { line(it) }
    }

    private fun line(item: NewsItem): String {
        val time = item.publishedAt?.let { TIME_FORMATTER.format(it.atZone(ZONE)) } ?: "onbekend tijdstip"
        return "$time: ${item.title} (${item.link})"
    }

    private companion object {
        const val DEFAULT_MAX_ITEMS = 10
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
