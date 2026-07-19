package nl.vdzon.robbertsassistent.news

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor het nieuws (RSS, keyless — altijd echt). */
@Component
class NewsCouplingProbe(private val newsClient: NewsClient) : CouplingProbe {

    override val id = "news"
    override val name = "Nieuws"
    override val description = "Laatste nieuwskoppen (NOS, RSS)."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val feed = newsClient.latestHeadlines(1)
        return feed.error?.let { false to it } ?: (true to "${feed.items.size} nieuwsitem(s) opgehaald")
    }
}
