package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val WATCH_SYSTEM_PROMPT = """
    Je krijgt een instructie van Robbert en de platte tekst van een webpagina. Bepaal of datgene
    waar Robbert op wacht volgens die paginatekst het geval is.

    Antwoord in exact dit formaat, in het Nederlands, zonder inleiding en zonder opmaak:
    - Regel 1: precies "GEVONDEN" als het het geval is, of precies "NIET GEVONDEN" als niet.
    - Regel 2: één korte statuszin (max 12 woorden) die de huidige stand beschrijft, bijvoorbeeld
      "nog steeds uitverkocht" of "nu weer op voorraad".

    Weet je het niet zeker op basis van de paginatekst, antwoord dan "NIET GEVONDEN" met een
    statuszin die dat aangeeft.
""".trimIndent()

/**
 * Losse, tool-loze [ChatClient] voor het beoordelen van één zoekopdracht-check — hergebruikt de
 * bestaande [ChatModel]-bean (echt OpenAI-model of
 * [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel], afhankelijk van
 * `AppSecrets.effectiveMockAi`), zelfde patroon als `briefing.BriefingAiConfig`. Onder
 * `RA_MOCK_AI` levert het mock-model een antwoord dat niet aan het formaat voldoet; dat wordt
 * defensief geparsed tot "niet gevonden" (zie [WatchesService.parseAssessment]), dus preview
 * blijft deterministisch en pushvrij.
 */
@Configuration
class WatchAiConfig {
    @Bean
    fun watchChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WATCH_SYSTEM_PROMPT)
            .build()
}
