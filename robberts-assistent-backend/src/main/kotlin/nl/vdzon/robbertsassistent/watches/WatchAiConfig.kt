package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val WATCH_SYSTEM_PROMPT = """
    Je krijgt een instructie van Robbert en de platte tekst van een webpagina (HTML gestript). Beoordeel
    of aan de instructie is voldaan op basis van die paginatekst.

    Antwoord in exact dit formaat, in het Nederlands, precies twee regels:
    Regel 1: "GEVONDEN" als aan de instructie is voldaan, anders "NIET GEVONDEN".
    Regel 2: een korte statuszin die de huidige situatie samenvat (bv. "nog steeds uitverkocht" of
    "nu op voorraad").
""".trimIndent()

/**
 * Losse, lichte, tool-loze [ChatClient] (patroon `briefing.BriefingAiConfig`) die beoordeelt of aan
 * een watch-instructie is voldaan. Hergebruikt de bestaande [ChatModel]-bean (echt OpenAI-model of
 * [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel], afhankelijk van
 * `AppSecrets.effectiveMockAi`), zodat watches zonder eigen mock-schakelaar deterministisch blijven
 * onder `RA_MOCK_AI`.
 */
@Configuration
class WatchAiConfig {
    @Bean
    fun watchChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WATCH_SYSTEM_PROMPT)
            .build()
}
