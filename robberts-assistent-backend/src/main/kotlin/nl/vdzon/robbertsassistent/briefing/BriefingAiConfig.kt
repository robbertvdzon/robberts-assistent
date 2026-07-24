package nl.vdzon.robbertsassistent.briefing

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val WEEK_TASKS_SYSTEM_PROMPT = """
    Je krijgt Robberts openstaande reminders en zijn notitie. Geef in maximaal 3 korte zinnen een
    samenvatting van wat hij deze week écht moet doen. Schrijf in het Nederlands, direct en concreet,
    zonder inleidende zin ("hier is een samenvatting" e.d.). Is er niets relevants, zeg dan gewoon dat
    er niets dringends op de planning staat.
""".trimIndent()

private val SYSTEM_STATUS_SYSTEM_PROMPT = """
    Je krijgt ruwe statusdata van vijf systeemchecks: zonnepanelen, backups, OpenShift-cluster,
    robotmaaier en software factory. Bepaal zelf, per check, of er 'aandacht nodig' is (bv. een
    fout, storing, of iets dat op actie van Robbert wacht) — er is geen vaste drempel, gebruik je
    eigen beoordeling van de data.

    Antwoord in exact dit formaat, in het Nederlands:
    - Regel 1: "AANDACHT: <korte, kommagescheiden lijst van checks die aandacht nodig hebben>",
      of "AANDACHT: geen" als geen enkele check aandacht nodig heeft.
    - Daarna een helder rapport, één alinea per check, zonder inleidende zin. Geen lengtelimiet —
      neem concrete cijfers uit de ruwe data over (percentages, versienummers, foutcodes e.d.)
      i.p.v. ze weg te laten voor de beknoptheid: een volledig, leesbaar rapport weegt zwaarder
      dan kort zijn.
""".trimIndent()

/**
 * Losse, lichte [ChatClient]s (geen tools, geen gesprekshistorie) voor de briefingsecties die een
 * AI-beoordeling nodig hebben — hergebruiken de bestaande [ChatModel]-bean (echt OpenAI-model of
 * [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel], afhankelijk van
 * `AppSecrets.effectiveMockAi`, zie `assistant.ai.AiConfig`) zodat de briefing zonder eigen
 * mock-schakelaar deterministisch blijft onder `RA_MOCK_AI`.
 */
@Configuration
class BriefingAiConfig {
    @Bean
    fun weekTasksChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WEEK_TASKS_SYSTEM_PROMPT)
            .build()

    @Bean
    fun systemStatusChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_STATUS_SYSTEM_PROMPT)
            .build()
}
