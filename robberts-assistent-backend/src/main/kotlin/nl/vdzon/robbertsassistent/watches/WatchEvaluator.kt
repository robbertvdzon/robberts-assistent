package nl.vdzon.robbertsassistent.watches

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

data class WatchAssessment(val found: Boolean, val description: String, val note: String = "")

interface WatchEvaluator {
    fun assess(instruction: String, pageText: String): WatchAssessment
}

object WatchAssessmentParser {
    /**
     * Het model houdt zich niet altijd letterlijk aan het formaat ("regel 1: NIET GEVONDEN",
     * "**Niet gevonden.**"); opmaak en het voorvoegsel tellen daarom niet mee. Een antwoord zonder
     * herkenbare status blijft een fout, met het begin van het antwoord erbij voor de log.
     */
    fun parse(answer: String): WatchAssessment {
        val lines = answer.lines().map { clean(it) }.filter(String::isNotEmpty)
        require(lines.isNotEmpty()) { "Beoordelaar gaf een leeg antwoord terug" }
        val status = lines[0].uppercase()
        val found = when {
            status.startsWith("NIET GEVONDEN") -> false
            status.startsWith("GEVONDEN") -> true
            else -> error("Beoordelaar gaf een onbekende status terug: ${answer.take(120)}")
        }
        // Staat de omschrijving op dezelfde regel als de status, dan is dat de rest van die regel.
        val sameLine = lines[0].drop(if (found) "GEVONDEN".length else "NIET GEVONDEN".length).trimStart(' ', ':', '.', '-', ',').trim()
        val description = lines.getOrNull(1) ?: sameLine
        require(description.isNotEmpty()) { "Beoordelaar gaf geen omschrijving terug" }
        // Een derde regel is de context; "geen" of een streepje betekent dat er niets opviel.
        val note = lines.getOrNull(2).orEmpty().takeUnless { it.lowercase().trim('.', '-', ' ') in setOf("", "geen", "n.v.t", "nvt") }.orEmpty()
        return WatchAssessment(found, description.take(500), note.take(300))
    }

    private fun clean(line: String): String =
        line.trim().trim('*', '`', '#', ' ').replace(Regex("(?i)^regel\\s*[123]\\s*[:.-]\\s*"), "").trim('*', '`', ' ')
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
                GEVONDEN betekent: de voorwaarde uit de instructie is nu waar, dus de gebruiker moet
                nu een melding krijgen. Het betekent niet dat je het onderwerp of een woord uit de
                instructie op de pagina hebt teruggevonden. Bij een instructie als "meld als dit
                niet meer uitverkocht is" en een pagina waar het nog uitverkocht staat, is het
                antwoord dus NIET GEVONDEN. Controleer dat regel 1 en regel 2 elkaar niet
                tegenspreken. Na GEVONDEN stopt de zoekopdracht; kies het alleen als je zeker bent.
                Antwoord exact met:
                regel 1: GEVONDEN of NIET GEVONDEN
                regel 2: een korte Nederlandstalige statusomschrijving
                regel 3: context die de gebruiker helpt inschatten of wachten nog zin heeft, in één
                zin, of het woord geen. Bijvoorbeeld: de pagina lijkt niet meer te bestaan of
                verwijst door, het product lijkt uit het assortiment, er staat een verwachte
                leverdatum, of de pagina vraagt om in te loggen. Herhaal regel 2 niet.
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
