package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val WATCH_SYSTEM_PROMPT = """
    Je krijgt de tekst van een webpagina en een zoekinstructie van de gebruiker. Beoordeel of het
    gezochte item of de gezochte conditie aanwezig/vervuld is op de pagina.

    Antwoord in exact één van deze twee formaten:
    - "GEVONDEN: <korte reden waarom je denkt dat het gevonden is>"
    - "NIET GEVONDEN: <korte beschrijving van de huidige status op de pagina>"

    Wees voorzichtig: zeg alleen GEVONDEN als je er zeker van bent. Bij twijfel, zeg NIET GEVONDEN.
""".trimIndent()

/**
 * Losse, lichte [ChatClient] (geen tools, geen gesprekshistorie) voor de watch-beoordeling —
 * hergebruikt de bestaande [ChatModel]-bean (echt OpenAI-model of MockChatModel, afhankelijk van
 * `AppSecrets.effectiveMockAi`).
 */
@Configuration
class WatchAiConfig {
    @Bean
    fun watchChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WATCH_SYSTEM_PROMPT)
            .build()
}
