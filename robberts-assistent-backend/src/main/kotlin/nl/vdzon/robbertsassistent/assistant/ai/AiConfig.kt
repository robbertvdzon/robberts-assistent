package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

private val SYSTEM_PROMPT = """
    Je bent Robberts persoonlijke assistent. Antwoord kort en to-the-point, in het Nederlands.
    Je hebt tools om Robberts notitie te lezen/bij te werken, om actuele windmetingen +
    windvoorspellingen bij IJmuiden op te halen, om reminders te zetten (die op tijd een push/
    alarm geven), om Robberts agenda te lezen, om een Google Doc te lezen, en om een
    push-notificatie naar Robberts telefoon te sturen. Gebruik een tool zodra de vraag daarom
    vraagt; verzin geen gegevens die je met een tool kunt ophalen.
    Als een windbron geen bruikbare waarde teruggeeft (bv. alleen een laadscherm), probeer dan de
    andere windbron voordat je aangeeft dat het niet lukt.
    Voor voorspellingen: windfinder dekt vandaag/morgen en is nauwkeuriger voor deze kustlocatie —
    gebruik die als eerste bron. Vraagt iemand verder vooruit (overmorgen en later), vul dan aan met
    de Open-Meteo-tool en combineer beide in één antwoord: windfinder voor de dagen die het dekt,
    Open-Meteo voor de rest. Vermeld kort dat de dagen verder vooruit van een andere (minder
    kustspecifieke) bron komen.
""".trimIndent()

/**
 * Bewust GEEN spring-ai-starter-model-openai (auto-configuratie op basis van properties) — met
 * handmatige bean-wiring kiezen we hier zelf tussen het echte OpenAI-model en [MockChatModel],
 * gestuurd door [AppSecrets.effectiveMockAi] (altijd mock in preview/tests, nooit in productie).
 */
@Configuration
class AiConfig {

    @Bean
    fun chatModel(secrets: AppSecrets): ChatModel =
        if (secrets.effectiveMockAi) {
            MockChatModel()
        } else {
            OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey(secrets.openAiApiKey).build())
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").maxTokens(600).build())
                .build()
        }

    @Bean
    @Primary
    fun assistantChatClient(
        chatModel: ChatModel,
        notesTools: NotesTools,
        windTools: WindTools,
        reminderTools: ReminderTools,
        calendarTools: CalendarTools,
        docsTools: DocsTools,
        pushTools: PushTools,
    ): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(notesTools, windTools, reminderTools, calendarTools, docsTools, pushTools)
            .build()
}
