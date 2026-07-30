package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val WATCH_SYSTEM_PROMPT = """
    Je beoordeelt of een webpagina voldoet aan een zoekopdracht. Je krijgt de platte tekst van de
    pagina en de instructie van de gebruiker. Antwoord in exact dit formaat:
    - Regel 1: alleen het woord "GEVONDEN" of "NIET GEVONDEN" (zonder extra tekst)
    - Regel 2: een korte uitleg (max 1 zin) waarom je tot deze conclusie komt

    Wees strikt: alleen "GEVONDEN" als de instructie duidelijk wordt vervuld door de inhoud van de
    pagina. Bij twijfel: "NIET GEVONDEN".
""".trimIndent()

/**
 * Losse, lichte [ChatClient] (geen tools, geen gesprekshistorie) voor de watch-beoordeling.
 * Hergebruikt de bestaande [ChatModel]-bean (echt OpenAI of MockChatModel onder RA_MOCK_AI).
 */
@Configuration
class WatchAiConfig {
    @Bean
    fun watchChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WATCH_SYSTEM_PROMPT)
            .build()
}
