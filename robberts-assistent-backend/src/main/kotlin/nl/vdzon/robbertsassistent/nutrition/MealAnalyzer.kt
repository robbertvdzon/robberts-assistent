package nl.vdzon.robbertsassistent.nutrition

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.content.Media
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils

/** Wat de AI van een eetmoment maakte. */
data class MealAnalysis(val title: String, val remark: String, val items: List<MealItem>)

/** Alles wat de AI meekrijgt om één eetmoment te beoordelen. */
class AnalysisRequest(
    val photos: List<StoredMealPhoto>,
    val note: String,
    val eatenAtLocal: LocalDateTime,
    /** Vuistregels en vaste gerechten die eerder geleerd zijn. */
    val memory: String,
    /** Recente, door de gebruiker bevestigde of verbeterde maaltijden, als voorbeeld. */
    val recent: List<Meal>,
    /** Het vorige voorstel, als dit een herziening is. */
    val previous: List<MealItem> = emptyList(),
    /** Wat de gebruiker in woorden heeft verbeterd, in volgorde. */
    val corrections: List<String> = emptyList(),
    /** Eerdere maaltijden met gewogen onderdelen en hun foto, om mee te vergelijken. */
    val references: List<ReferenceExample> = emptyList(),
    /** Per product de eerder gewogen porties in gram, bv. "Havermout" → [58, 60, 62]. */
    val weighedPortions: Map<String, List<Int>> = emptyMap(),
    /** Tekst van receptpagina's waar Robbert in zijn notitie naar linkte, per link. */
    val recipes: Map<String, String> = emptyMap(),
)

/** Een eerdere maaltijd waarvan (een deel van) de gewichten gemeten zijn, met de foto erbij. */
class ReferenceExample(val photo: StoredMealPhoto, val description: String)

/** Uitkomst van de leerstap: het nieuwe geheugen en één zin over wat er bijkwam. */
data class LearnResult(val memory: String, val learned: String)

interface MealAnalyzer {
    fun analyze(request: AnalysisRequest): MealAnalysis

    /** Werkt het geheugen bij na een bevestiging; `null` als er niets verandert of het niet lukt. */
    fun learn(memory: String, meal: Meal): LearnResult?
}

private val NUTRITION_SYSTEM_PROMPT = """
    Je bent de voedingsassistent van Robbert. Je krijgt foto's en/of een notitie van één eetmoment
    en bepaalt wat hij at en hoeveel. Foto's kunnen zijn: een bord of glas (ook een tweede bord bij
    opscheppen: tel dat erbij op), een recept, een verpakking of etiket met voedingswaarden,
    ingrediënten op het aanrecht, of een weegschaal met het gewicht op het display.

    Werkwijze:
    - Gebruik de meest betrouwbare bron per onderdeel: gewogen (display of door Robbert genoemd
      gewicht) > etiket > recept > schatting. Zet dat in "basis".
    - Bij een recept: reken het hele recept door en bepaal met de bordfoto welk deel hij at.
      Vergeet olie, boter, room en suiker uit het recept niet.
    - Schat porties aan de hand van bord, bestek, hand of verpakking in beeld.
    - Referentiefoto's: na de foto's van dit eetmoment kunnen foto's van eerdere maaltijden volgen
      waarvan de gewichten gemeten zijn. Vergelijk de hoeveelheid op de nieuwe foto daarmee en zet
      "basis" op VERGELEKEN als je schatting daarop rust. Eet hij duidelijk hetzelfde als op een
      referentie, neem dan de gewogen hoeveelheden over.
    - Staat er de tekst van een receptpagina bij, gebruik die dan als recept.
    - Volg de vuistregels en eerdere maaltijden van Robbert als die van toepassing zijn; lijkt het
      eetmoment op een eerdere maaltijd, neem dan die hoeveelheden over tenzij de foto iets anders
      laat zien.
    - Voedingswaarden gelden voor de gegeten hoeveelheid, niet per 100 gram.
    - "groente_gram" en "fruit_gram" zijn het deel van het onderdeel dat groente of fruit is.
    - Zet "twijfel" op true als je product of hoeveelheid niet goed kunt zien.
    - Water, thee en koffie zonder melk of suiker: laat ze weg.

    Antwoord uitsluitend met één JSON-object, zonder uitleg ervoor of erna en zonder codeblok:
    {"titel": "Ontbijt|Lunch|Avondeten|Tussendoor|Drinken",
     "opmerking": "één korte Nederlandse zin, bv. waar je over twijfelt; mag leeg",
     "items": [{"naam": "Gekookte aardappelen", "gram": 200, "kcal": 170, "eiwit": 4.0,
                "koolhydraten": 38.0, "vet": 0.2, "vezels": 3.6, "groente_gram": 0,
                "fruit_gram": 0, "twijfel": false,
                "basis": "GEWOGEN|ETIKET|RECEPT|VERGELEKEN|GESCHAT"}]}
""".trimIndent()

private val FOOD_MEMORY_SYSTEM_PROMPT = """
    Je beheert het geheugen van Robberts voedingsassistent: een korte Nederlandse tekst met
    vuistregels ("koffie is altijd met een scheutje halfvolle melk"), vaste gerechten met
    hoeveelheden ("Ontbijt-havermout: 60 g havermout, 200 ml halfvolle melk, 1 banaan") en maten
    van zijn servies. Je krijgt het huidige geheugen en één zojuist bevestigde maaltijd, met het
    oorspronkelijke AI-voorstel en wat Robbert ervan maakte.

    Werk het geheugen bij als deze maaltijd iets blijvends leert: een correctie die vaker zal
    gelden, een gewogen hoeveelheid, of een gerecht dat terug zal komen. Eenmalige dingen horen
    er niet in. Houd het geheugen compact (hooguit 60 regels), voeg samen wat dubbel is en gooi
    niets weg dat nog klopt.

    Antwoord uitsluitend met één JSON-object:
    {"geheugen": "de volledige nieuwe geheugentekst", "geleerd": "één korte zin over wat erbij kwam, of leeg als er niets verandert"}
""".trimIndent()

/** Eigen tool-loze clients: vision voor de analyse, tekst voor het geheugen. */
@Configuration
class NutritionAiConfig {
    @Bean
    fun nutritionChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel).defaultSystem(NUTRITION_SYSTEM_PROMPT).build()

    @Bean
    fun foodMemoryChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel).defaultSystem(FOOD_MEMORY_SYSTEM_PROMPT).build()
}

/** Haalt het JSON-antwoord van de AI uit elkaar; tolerant voor tekst of een codeblok eromheen. */
object MealAnalysisParser {
    fun parse(answer: String, mapper: ObjectMapper): MealAnalysis {
        val root = jsonObject(answer, mapper)
        val items = root.path("items").map { node ->
            MealItem(
                name = node.path("naam").asText("").trim().ifEmpty { "Onbekend" },
                grams = node.path("gram").asInt(0).coerceAtLeast(0),
                kcal = node.path("kcal").asInt(0).coerceAtLeast(0),
                proteinG = node.path("eiwit").asDouble(0.0).coerceAtLeast(0.0),
                carbsG = node.path("koolhydraten").asDouble(0.0).coerceAtLeast(0.0),
                fatG = node.path("vet").asDouble(0.0).coerceAtLeast(0.0),
                fiberG = node.path("vezels").asDouble(0.0).coerceAtLeast(0.0),
                vegetableG = node.path("groente_gram").asInt(0).coerceAtLeast(0),
                fruitG = node.path("fruit_gram").asInt(0).coerceAtLeast(0),
                uncertain = node.path("twijfel").asBoolean(false),
                basis = runCatching { ItemBasis.valueOf(node.path("basis").asText("").uppercase()) }
                    .getOrDefault(ItemBasis.GESCHAT),
            )
        }
        return MealAnalysis(
            title = root.path("titel").asText("").trim().take(40),
            remark = root.path("opmerking").asText("").trim().take(300),
            items = items,
        )
    }

    fun jsonObject(answer: String, mapper: ObjectMapper): JsonNode {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI gaf geen JSON terug" }
        return mapper.readTree(answer.substring(start, end + 1))
    }
}

@Component
class ChatMealAnalyzer(
    @Qualifier("nutritionChatClient") private val chatClient: ChatClient,
    @Qualifier("foodMemoryChatClient") private val memoryClient: ChatClient,
    private val mapper: ObjectMapper,
    private val secrets: AppSecrets,
) : MealAnalyzer {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun analyze(request: AnalysisRequest): MealAnalysis {
        if (secrets.effectiveMockAi) return mockAnalysis(request)
        val text = promptText(request)
        val media = (request.photos + request.references.map { it.photo })
            .map { Media(MimeTypeUtils.parseMimeType(it.contentType), ByteArrayResource(it.bytes)) }
        var lastError: Exception? = null
        // Eén herkansing: een enkel antwoord dat geen geldige JSON is komt voor.
        repeat(2) { attempt ->
            try {
                val answer = chatClient.prompt()
                    .user { user ->
                        user.text(text)
                        if (media.isNotEmpty()) user.media(*media.toTypedArray())
                    }
                    .call()
                    .content()
                    .orEmpty()
                return MealAnalysisParser.parse(answer, mapper)
            } catch (e: Exception) {
                logger.warn("Maaltijd-analyse poging {} mislukt: {}", attempt + 1, e.message)
                lastError = e
            }
        }
        throw IllegalStateException("De analyse is mislukt: ${lastError?.message}", lastError)
    }

    override fun learn(memory: String, meal: Meal): LearnResult? {
        if (secrets.effectiveMockAi) return null
        return runCatching {
            val answer = memoryClient.prompt()
                .user(
                    buildString {
                        appendLine("Huidig geheugen:")
                        appendLine(memory.ifBlank { "(leeg)" })
                        appendLine()
                        appendLine("Bevestigde maaltijd: ${meal.title}")
                        if (meal.note.isNotBlank()) appendLine("Notitie van Robbert: ${meal.note}")
                        appendLine("Oorspronkelijk AI-voorstel:")
                        appendLine(describe(meal.aiProposal))
                        appendLine("Wat Robbert ervan maakte:")
                        appendLine(describe(meal.items))
                        if (meal.corrections.isNotEmpty()) {
                            appendLine("Zijn correcties in woorden:")
                            meal.corrections.forEach { appendLine("- $it") }
                        }
                    },
                )
                .call()
                .content()
                .orEmpty()
            val root = MealAnalysisParser.jsonObject(answer, mapper)
            val learned = root.path("geleerd").asText("").trim()
            val newMemory = root.path("geheugen").asText("").trim()
            if (learned.isEmpty() || newMemory.isEmpty()) null else LearnResult(newMemory.take(MAX_MEMORY), learned.take(300))
        }.onFailure { logger.warn("Eet-geheugen bijwerken mislukt: {}", it.message) }.getOrNull()
    }

    private fun promptText(request: AnalysisRequest): String = buildString {
        appendLine("Tijdstip van eten: ${request.eatenAtLocal.format(TIME_FORMAT)}")
        appendLine("Aantal foto's van dit eetmoment: ${request.photos.size} (de eerste ${request.photos.size} foto's)")
        request.references.forEachIndexed { index, reference ->
            appendLine("Foto ${request.photos.size + index + 1} is een referentie, niet van dit eetmoment: ${reference.description}")
        }
        appendLine("Notitie van Robbert: ${request.note.ifBlank { "(geen)" }}")
        if (request.memory.isNotBlank()) {
            appendLine()
            appendLine("Wat je over Robberts eten weet:")
            appendLine(request.memory)
        }
        if (request.recent.isNotEmpty()) {
            appendLine()
            appendLine("Recente door Robbert bevestigde maaltijden:")
            request.recent.forEach { meal ->
                appendLine("- ${meal.title}: ${describe(meal.items)}")
            }
        }
        if (request.weighedPortions.isNotEmpty()) {
            appendLine()
            appendLine("Porties die Robbert eerder heeft gewogen (gram):")
            request.weighedPortions.forEach { (name, grams) -> appendLine("- $name: ${grams.joinToString(", ")}") }
        }
        request.recipes.forEach { (url, text) ->
            appendLine()
            appendLine("Tekst van de receptpagina $url:")
            appendLine(text)
        }
        if (request.previous.isNotEmpty()) {
            appendLine()
            appendLine("Je vorige voorstel voor dit eetmoment:")
            appendLine(describe(request.previous))
        }
        if (request.corrections.isNotEmpty()) {
            appendLine()
            appendLine("Robbert verbeterde dat zo (laatste bovenaan telt het zwaarst):")
            request.corrections.reversed().forEach { appendLine("- $it") }
            appendLine("Geef het hele eetmoment opnieuw, met zijn verbeteringen verwerkt.")
        }
    }

    private fun mockAnalysis(request: AnalysisRequest): MealAnalysis {
        val hour = request.eatenAtLocal.hour
        val title = when {
            hour < 11 -> "Ontbijt"
            hour < 15 -> "Lunch"
            hour < 17 -> "Tussendoor"
            else -> "Avondeten"
        }
        return MealAnalysis(
            title = title,
            remark = "Mock-voorstel (geen echte AI in deze omgeving).",
            items = listOf(
                MealItem("Gekookte aardappelen", 200, 170, 4.0, 38.0, 0.2, 3.6, 0, 0, false, ItemBasis.GESCHAT),
                MealItem("Sperziebonen", 150, 45, 2.7, 6.0, 0.3, 4.5, 150, 0, false, ItemBasis.GESCHAT),
                MealItem("Rookworst", 125, 390, 16.0, 3.0, 35.0, 0.0, 0, 0, true, ItemBasis.GESCHAT),
            ),
        )
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy HH:mm", java.util.Locale.of("nl"))
        private const val MAX_MEMORY = 8000

        fun describe(items: List<MealItem>): String =
            if (items.isEmpty()) "(niets)"
            else items.joinToString("; ") { "${it.name} ${it.grams} g = ${it.kcal} kcal [${it.basis.name.lowercase()}]" }
    }
}
