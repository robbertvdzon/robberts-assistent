package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

data class WatchAssessment(val found: Boolean, val description: String)

interface WatchEvaluator {
    fun assess(instruction: String, pageText: String): WatchAssessment
}

object WatchAssessmentParser {
    fun parse(answer: String): WatchAssessment {
        val lines = answer.lines().map(String::trim).filter(String::isNotEmpty)
        require(lines.size >= 2) { "Beoordelaar gaf te weinig regels terug" }
        val found = when (lines[0].uppercase()) {
            "GEVONDEN" -> true
            "NIET GEVONDEN" -> false
            else -> error("Beoordelaar gaf een onbekende status terug")
        }
        return WatchAssessment(found, lines[1].take(500))
    }
}

@Configuration
class WatchAiConfig {
    @Bean
    fun watchChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(
                """
                Beoordeel uitsluitend of de zoekinstructie in de aangeleverde webpaginatekst is
                vervuld. Gebruik geen tools en verzin geen ontbrekende informatie.
                Antwoord exact met:
                regel 1: GEVONDEN of NIET GEVONDEN
                regel 2: een korte Nederlandstalige statusomschrijving
                """.trimIndent(),
            )
            .build()
}

@Component
class ChatWatchEvaluator(
    @Qualifier("watchChatClient") private val chatClient: ChatClient,
) : WatchEvaluator {
    override fun assess(instruction: String, pageText: String): WatchAssessment {
        val answer = chatClient.prompt()
            .user("Zoekinstructie:\n$instruction\n\nWebpaginatekst:\n$pageText")
            .call()
            .content()
            .orEmpty()
        return WatchAssessmentParser.parse(answer)
    }
}
