package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.softwarefactory.FactoryActionItem
import nl.vdzon.robbertsassistent.softwarefactory.FactoryStory
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent toegang tot de software-factory-dashboard (stories, actiepunten) via
 * [SoftwareFactoryClient] — dezelfde bridge-API die de software-factory-frontend zelf gebruikt.
 */
@Component
class SoftwareFactoryTools(private val client: SoftwareFactoryClient) {

    @Tool(
        description = "Haal de lijst van stories (features/bugfixes) in de software factory op: " +
            "key, titel, fase, gepauzeerd/foutstatus, gemerged. Gebruik dit voor vragen als 'hoe " +
            "staat het met mijn stories' of 'wat loopt er in de software factory'.",
    )
    fun getFactoryStories(): String {
        val result = client.stories()
        result.error?.let { return it }
        if (result.stories.isEmpty()) return "Geen stories gevonden."
        return result.stories.joinToString("\n") { line(it) }
    }

    @Tool(
        description = "Haal de dingen op die in de software factory op Robberts actie wachten " +
            "(goedkeuringen, open vragen). Gebruik dit voor vragen als 'wat moet ik nog " +
            "goedkeuren' of 'staat er iets voor mij klaar in de software factory'.",
    )
    fun getFactoryMyActions(): String {
        val result = client.myActions()
        result.error?.let { return it }
        if (result.items.isEmpty()) return "Niets dat op jouw actie wacht."
        return result.items.joinToString("\n") { line(it) }
    }

    private fun line(story: FactoryStory): String {
        val status = when {
            story.error != null -> "FOUT: ${story.error}"
            story.paused -> "gepauzeerd"
            story.merged -> "gemerged"
            else -> story.phase ?: "onbekende fase"
        }
        return "${story.key}: ${story.summary} — $status"
    }

    private fun line(item: FactoryActionItem): String {
        val question = item.question?.let { " — vraag: $it" } ?: ""
        return "${item.storyKey}: ${item.storySummary}$question"
    }
}
