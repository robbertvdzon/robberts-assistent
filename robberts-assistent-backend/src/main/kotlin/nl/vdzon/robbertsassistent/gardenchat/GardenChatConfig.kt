package nl.vdzon.robbertsassistent.gardenchat

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val GARDEN_SYSTEM_PROMPT = """
    Je bent Robberts moestuin-assistent. De gebruiker stuurt tekst en vaak één of meer foto's van
    zijn moestuin of planten. Help met het herkennen van planten, ziektes en plagen, met verzorging,
    bemesting, water geven, snoeien en oogsten. Beschrijf wat je op de foto's ziet als dat relevant is.
    Antwoord kort, praktisch en concreet, in het Nederlands. Weet je iets niet zeker op basis van de
    foto, zeg dat eerlijk en vraag om een scherpere of andere foto.
""".trimIndent()

/**
 * Aparte [ChatClient] voor de moestuin-chat: eigen system-prompt, geen tools, en multimodaal
 * (de foto's gaan als Media mee). Hergebruikt het [ChatModel] uit `assistant.ai.AiConfig`
 * (OpenAI gpt-4o-mini is vision-capable; in preview/tests de deterministische mock).
 */
@Configuration
class GardenChatConfig {
    @Bean
    fun gardenChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(GARDEN_SYSTEM_PROMPT)
            .build()
}
