package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneId
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.jobs.FoundJob
import nl.vdzon.robbertsassistent.jobs.JobFinder
import nl.vdzon.robbertsassistent.jobs.JobLead
import nl.vdzon.robbertsassistent.jobs.JobSearchService
import nl.vdzon.robbertsassistent.jobs.JobSearchSettings
import nl.vdzon.robbertsassistent.jobs.LeadVerdict
import nl.vdzon.robbertsassistent.jobs.LearnedPreferences
import org.springframework.stereotype.Component

/**
 * Laat de Agent Runtime het web doorzoeken. Dit gaat rechtstreeks via de gateway en niet via een
 * `ChatClient`: het gewone protocol verbiedt het model juist om webtools te gebruiken. Staat in dit
 * pakket omdat de gateway niet buiten de module `assistant` gebruikt mag worden.
 */
@Component
class RuntimeJobFinder(
    private val gateway: AgentRuntimeGateway,
    private val models: AiModelSettings,
    private val secrets: AppSecrets,
) : JobFinder {
    private val mapper = ObjectMapper()

    override fun find(settings: JobSearchSettings, known: List<JobLead>): List<FoundJob> {
        if (secrets.effectiveMockAi) {
            return listOf(
                FoundJob("Senior Kotlin Engineer", "Voorbeeld BV", "Haarlem", "https://example.org/vacature/kotlin", "Proefvacature (mock-AI)."),
                FoundJob("AI Engineer", "Tata Steel", "IJmuiden", "https://example.org/vacature/ai", "Proefvacature (mock-AI)."),
            )
        }
        val result = gateway.runStructured(findInstruction(settings, known), FIND_SCHEMA, emptyList(), models.execution(AiRole.BACKGROUND))
        return result.path("vacatures").mapNotNull { node ->
            val title = node.path("titel").asText("").trim()
            val url = node.path("url").asText("").trim()
            if (title.isEmpty() || !url.startsWith("http")) return@mapNotNull null
            FoundJob(
                title = title.take(160),
                company = node.path("bedrijf").asText("").trim().take(120),
                location = node.path("plaats").asText("").trim().take(120),
                url = url,
                summary = node.path("samenvatting").asText("").trim().take(600),
                publishedOn = parsePublishedOn(node.path("publicatiedatum").asText("")),
            )
        }
    }

    override fun learn(settings: JobSearchSettings, lead: JobLead): LearnedPreferences? {
        if (secrets.effectiveMockAi) return null
        val result = gateway.runStructured(learnInstruction(settings, lead), LEARN_SCHEMA, emptyList(), models.execution(AiRole.BACKGROUND))
        val preferences = result.path("voorkeuren").asText("").trim()
        if (preferences.isEmpty()) return null
        return LearnedPreferences(
            preferences.take(MAX_PREFERENCES),
            result.path("geblokkeerde_bedrijven").map { it.asText("").trim() }.filter { it.isNotEmpty() },
        )
    }

    internal fun findInstruction(settings: JobSearchSettings, known: List<JobLead>): String = buildString {
        appendLine("Zoek op het web naar actuele vacatures voor Robbert. Gebruik hiervoor je zoek- en webtools: zoek breed")
        appendLine("(vacaturesites, LinkedIn-vacatures, werken-bij-pagina's van werkgevers in de regio) en open de pagina's om te")
        appendLine("controleren dat de vacature nog openstaat en bij de zoekvraag past.")
        appendLine()
        appendLine("## Zoekvraag")
        appendLine(settings.description)
        if (settings.preferences.isNotBlank()) {
            appendLine()
            appendLine("## Wat Robbert wel en niet interessant vindt (geleerd uit eerdere oordelen)")
            appendLine(settings.preferences)
        }
        if (settings.blockedCompanies.isNotEmpty()) {
            appendLine()
            appendLine("## Bedrijven waarvan hij niets meer wil zien")
            appendLine(settings.blockedCompanies.joinToString(", "))
        }
        if (known.isNotEmpty()) {
            appendLine()
            appendLine("## Al eerder gemeld (niet opnieuw als nieuw melden)")
            known.sortedByDescending { it.foundAt }.take(MAX_KNOWN_IN_PROMPT).forEach { appendLine("- ${it.title} bij ${it.company} (${it.url})") }
        }
        appendLine()
        appendLine("## Regels")
        appendLine("- Meld hooguit ${JobSearchService.MAX_PER_RUN} nieuwe vacatures, de best passende eerst; liever weinig goede dan veel matige.")
        appendLine("- Alleen vacatures die je zelf op een pagina hebt gezien; verzin niets. `url` is de pagina van de vacature zelf.")
        appendLine("- Geen detacheerders of recruiters die de werkgever niet noemen, tenzij de zoekvraag daar om vraagt.")
        appendLine("- `samenvatting`: twee of drie zinnen in het Nederlands over de rol, de techniek en waarom dit past; noem de")
        appendLine("  reisafstand of thuiswerkregeling als die op de pagina staat.")
        appendLine("- Vind je niets nieuws en geen ontbrekende publicatiedatums, geef dan een lege lijst.")
        appendLine("- Vandaag is ${LocalDate.now(ZoneId.of("Europe/Amsterdam"))}. `publicatiedatum`: de oorspronkelijke plaatsingsdatum")
        appendLine("  die de vacaturepagina noemt (bijvoorbeeld datePosted), als YYYY-MM-DD; anders null. Verzin geen datum.")
        appendLine("  Gebruik niet de datum waarop jij hem vindt, een sluitingsdatum, wijzigingsdatum of copyrightjaar.")
        appendLine("- Controleer daarnaast hooguit 10 van de onderstaande bestaande vacatures zonder publicatiedatum.")
        appendLine("  Alleen als je hun plaatsingsdatum daadwerkelijk vindt, mag je ze naast de nieuwe vacatures in dezelfde lijst")
        appendLine("  teruggeven, met exact hun bestaande url. De app vult alleen de datum aan, zonder nieuwe melding.")
        known.filter { it.publishedOn == null && it.verdict != LeadVerdict.NIET }
            .sortedByDescending { it.foundAt }.take(10)
            .forEach { appendLine("  - ${it.title} bij ${it.company}: ${it.url}") }
    }

    internal fun learnInstruction(settings: JobSearchSettings, lead: JobLead): String = buildString {
        appendLine("Robbert heeft een vacature beoordeeld. Werk zijn voorkeuren bij, zodat de volgende zoekronde beter past.")
        appendLine("Gebruik geen tools; dit is alleen tekst bijwerken.")
        appendLine()
        appendLine("## Vacature")
        appendLine("${lead.title} bij ${lead.company} (${lead.location})")
        appendLine(lead.summary)
        appendLine()
        appendLine("## Oordeel")
        appendLine(if (lead.verdict == LeadVerdict.INTERESSANT) "Interessant" else "Niet interessant")
        appendLine("Reden: ${lead.reason.ifBlank { "(geen reden gegeven)" }}")
        appendLine()
        appendLine("## Huidige voorkeuren")
        appendLine(settings.preferences.ifBlank { "(nog leeg)" })
        appendLine()
        appendLine("## Huidige lijst van bedrijven die wegblijven")
        appendLine(settings.blockedCompanies.joinToString(", ").ifBlank { "(leeg)" })
        appendLine()
        appendLine("## Regels")
        appendLine("- `voorkeuren`: de volledige, bijgewerkte tekst als korte opsomming (wat hij zoekt, wat afvalt en waarom).")
        appendLine("  Houd wat er stond, voeg toe wat dit oordeel leert, haal tegenstrijdigheden weg. Hooguit 25 regels.")
        appendLine("- `geblokkeerde_bedrijven`: de volledige lijst. Voeg het bedrijf alleen toe als de reden over het bedrijf zelf")
        appendLine("  gaat (hij wil daar niet werken), niet als alleen deze functie, de techniek of de afstand niet past.")
        appendLine("  Een oordeel 'interessant' haalt het bedrijf van de lijst.")
    }

    internal companion object {
        const val MAX_KNOWN_IN_PROMPT = 80
        const val MAX_PREFERENCES = 4000

        internal fun parsePublishedOn(text: String): LocalDate? =
            runCatching { LocalDate.parse(text.trim()) }.getOrNull()
                ?.takeUnless { it.isAfter(LocalDate.now(ZoneId.of("Europe/Amsterdam"))) }

        val FIND_SCHEMA: JsonNode = ObjectMapper().readTree(
            """
            {"type":"object","required":["vacatures"],"additionalProperties":false,
             "properties":{"vacatures":{"type":"array","items":{"type":"object",
               "required":["titel","bedrijf","plaats","url","samenvatting","publicatiedatum"],"additionalProperties":false,
               "properties":{"titel":{"type":"string"},"bedrijf":{"type":"string"},"plaats":{"type":"string"},
                             "url":{"type":"string"},"samenvatting":{"type":"string"},
                             "publicatiedatum":{"type":["string","null"]}}}}}}
            """.trimIndent(),
        )
        val LEARN_SCHEMA: JsonNode = ObjectMapper().readTree(
            """
            {"type":"object","required":["voorkeuren","geblokkeerde_bedrijven"],"additionalProperties":false,
             "properties":{"voorkeuren":{"type":"string"},
                           "geblokkeerde_bedrijven":{"type":"array","items":{"type":"string"}}}}
            """.trimIndent(),
        )
    }
}
