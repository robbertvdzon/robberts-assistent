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

/**
 * Losse, lichte [ChatClient] (geen tools, geen gesprekshistorie) voor [WeekTasksSectionProvider] —
 * hergebruikt de bestaande [ChatModel]-bean (echt OpenAI-model of [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel],
 * afhankelijk van `AppSecrets.effectiveMockAi`, zie `assistant.ai.AiConfig`) zodat de briefing
 * zonder eigen mock-schakelaar deterministisch blijft onder `RA_MOCK_AI`.
 */
@Configuration
class BriefingAiConfig {
    @Bean
    fun weekTasksChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(WEEK_TASKS_SYSTEM_PROMPT)
            .build()
}
