package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

private val TITLE_SYSTEM_PROMPT = """
    Je verzint een korte titel (maximaal 6 woorden) voor een gesprek, gebaseerd op de eerste vraag
    en het eerste antwoord die je krijgt. Geef alleen de titel terug, in het Nederlands, zonder
    aanhalingstekens en zonder leesteken erachter.
""".trimIndent()

private val MEMORY_SYSTEM_PROMPT = """
    Je houdt een kort geheugen bij van feiten/voorkeuren/context over Robbert, op basis van wat hij
    in gesprekken met zijn assistent vertelt. Je krijgt de huidige geheugen-tekst en de laatste
    vraag/antwoord-uitwisseling. Geef de volledige, bijgewerkte geheugen-tekst terug: verwerk nieuwe
    feiten/voorkeuren die de uitwisseling oplevert, werk verouderde informatie bij, en laat
    overbodige informatie weg. Schrijf kort en concreet, in het Nederlands, als vrije tekst (geen
    opsommingstekens/nummering verplicht) en zonder verdere uitleg over wat je gedaan hebt. Herhaal
    ongewijzigde delen exact zoals aangeleverd. Als er niets te onthouden valt (en er ook geen
    bestaande tekst overblijft), antwoord dan met een lege tekst.
""".trimIndent()

private val SYSTEM_PROMPT = """
    Je bent Robberts persoonlijke assistent. Antwoord kort en to-the-point, in het Nederlands.
    Je hebt tools om Robberts notities te lezen/bij te werken: er zijn meerdere notitiedocumenten
    (bijvoorbeeld 'todo' en 'recepten'), die je kunt opsommen, op titel lezen en overschrijven, en
    je kunt een nieuw document aanmaken; noem je geen titel, dan werk je op het standaarddocument
    ('todo'). Verder heb je tools om actuele windmetingen +
    windvoorspellingen bij IJmuiden op te halen, om de regen-/weersvoorspelling bij de moestuin in
    Heemskerk op te halen (komende uren of komende dagen), om de getijvoorspelling (hoog-/laagwater,
    waterhoogte) bij IJmuiden op te halen, om luchtkwaliteit/UV-index/pollen bij de moestuin op te
    halen, om de laatste nieuwskoppen (NOS) op te halen, om de afvalophaalkalender van Robberts
    huisadres op te halen (wanneer moet welke bak buiten), om de robotmaaier (Husqvarna Automower)
    te bekijken en te starten/parkeren, om Robberts recente Strava-trainingen op te halen, om
    stories en actiepunten uit de software factory op te halen, om de gezondheid van de
    OpenShift-cluster (thuis-server) op te halen, om reminders te zetten (push-notificatie op tijd,
    eenmalig of herhalend), om alarms te zetten (een echte wekker op de telefoon, eenmalig of
    herhalend), om Robberts agenda te lezen, om een Google Doc te lezen, om zijn langdurige
    zoekopdrachten (watches) op te sommen, aan te maken en aan te passen (verwijderen kan niet via
    de chat), en om een push-notificatie naar Robberts telefoon te sturen. Kies een reminder als een melding
    volstaat, en een alarm als het echt moet afgaan/wekken. Gebruik een tool zodra de vraag daarom
    vraagt; verzin geen gegevens die je met een tool kunt ophalen. De robotmaaier stuurt een
    fysiek apparaat aan (starten/parkeren) — doe dat alleen bij een expliciet verzoek, niet uit
    jezelf.
    Als een windbron geen bruikbare waarde teruggeeft (bv. alleen een laadscherm), probeer dan de
    andere windbron voordat je aangeeft dat het niet lukt.
    Voor voorspellingen: windfinder dekt vandaag/morgen en is nauwkeuriger voor deze kustlocatie —
    gebruik die als eerste bron. Vraagt iemand verder vooruit (overmorgen en later), vul dan aan met
    de Open-Meteo-tool en combineer beide in één antwoord: windfinder voor de dagen die het dekt,
    Open-Meteo voor de rest. Vermeld kort dat de dagen verder vooruit van een andere (minder
    kustspecifieke) bron komen.
    Je hebt ook tools voor Robberts dagboek. Vastleggen: wat hij at of dronk (logMeal, ook achteraf
    met een tijdstip), hoe hij zich voelt qua honger en energie (logCheckIn), zijn gewicht
    (logWeight) en dutjes (startNap, endNap, logNap). Opvragen: wat hij op een dag at
    (getDayNutrition), de hele dag op tijd met slaap, wandelingen, trainingen, dutjes, check-ins,
    eten en gewicht (getDayTimeline), het weekoverzicht en de verbanden uit zijn eigen gegevens.
    Aanpassen: een eetmoment verbeteren in woorden (correctMeal), op een ander tijdstip zetten
    (moveMeal) of verwijderen (deleteMeal), en een dutje verwijderen (deleteNap). Eetmomenten en
    dutjes hebben een kort id dat je uit getDayNutrition of getDayTimeline haalt; vraag Robbert
    nooit om een id. Verwijder alleen bij een duidelijk verzoek, en alleen dat ene ding.
    Slaap, stappen, verbruik en trainingen komen van zijn Garmin-horloge; die kun je lezen maar
    niet veranderen. Honger loopt van 0 (geen) via 1 trek, 2 beetje honger, 3 honger tot 4 slap.

    Zo zit de app in elkaar, voor als Robbert vraagt waar iets staat of hoe iets werkt. Onderin
    staan vijf tabs. Vandaag: de dagelijkse briefing (weerkaart, kiten, strandfietsen, agenda,
    afval, weektaken). Assistent: deze gesprekken, typen of praten, met foto's. Dagboek: bovenaan
    vier knoppen (Eten = foto of tekst van wat hij eet, Gevoel = honger en energie, Dutje = starten
    met of zonder wekker en afmelden, Gewicht), daaronder de dagtotalen en de tijdlijn van de dag;
    met de pijltjes naar een andere dag; onder de drie puntjes Deze week, Verbanden en "Wat ik van
    je weet" (het eet-geheugen). Een eetmoment is eerst een voorstel van de AI; Robbert bevestigt
    of verbetert het door erop te tikken. Een dutje waarbij hij zich niet afmeldt krijgt een
    geschat eind. Taken: herinneringen en wekkers. Meer: Health check, Zoekopdrachten,
    Koppelingen, Nachtchecks, Geheugen (wat jij over hem onthoudt), AI-modellen en Updates.
    Lang drukken op het app-icoon geeft snelkoppelingen voor een foto van eten, een check-in en een
    dutje. Drie keer per dag (10:30, 15:30, 20:30) vraagt de app hoe hij zich voelt.
    Kun je iets niet met je tools, zeg dat dan eerlijk en vertel waar het in de app wel kan.
""".trimIndent()

/**
 * Extra instructie die **bovenop** [SYSTEM_PROMPT] meegaat wanneer het antwoord hardop wordt
 * voorgelezen (praatmodus in de app, `voice=true` op `POST /api/v1/assistant/chat`). Bewust een
 * losse system-boodschap per request i.p.v. een request-level `.system(...)`: dat laatste zou de
 * `defaultSystem(...)` van `assistantChatClient` vervangen i.p.v. aanvullen. Zonder de vlag gaat er
 * niets van deze tekst mee en is de prompt exact als voorheen.
 */
val VOICE_SYSTEM_PROMPT = """
    Dit antwoord wordt hardop voorgelezen. Antwoord daarom in vlotte spreektaal en zo kort mogelijk:
    maximaal 2 korte zinnen. Geen opsommingen, geen markdown, geen kopjes, geen URL's, geen emoji en
    geen tabellen. Geen inleiding of samenvatting vooraf — geef meteen het antwoord. Schrijf getallen
    en eenheden uitspreekbaar op (bijvoorbeeld "twintig knopen uit het zuidwesten"). Antwoord alleen
    langer als Robbert expliciet om details of een lijst vraagt.
""".trimIndent()

/**
 * Alle AI loopt via de Agent Runtime ([AgentRuntimeChatModel]); er is bewust geen directe
 * verbinding met een AI-provider en geen Spring AI-starter (auto-configuratie). Zonder
 * runtime-token, of met `RA_MOCK_AI=true` (preview/tests), is het [MockChatModel] actief, zie
 * [AppSecrets.effectiveMockAi].
 */
@Configuration
class AiConfig {

    @Bean
    fun agentRuntimeGateway(secrets: AppSecrets, mapper: ObjectMapper): AgentRuntimeGateway =
        HttpAgentRuntimeGateway(
            baseUrl = secrets.agentRuntimeBaseUrl,
            token = secrets.agentRuntimeToken.orEmpty(),
            execution = RuntimeExecution(secrets.aiVendor, secrets.aiModel, secrets.aiMode),
            environment = "prod",
            mapper = mapper,
        )

    /**
     * Drie [ChatModel]s, één per toepassing ([AiRole]), elk met zijn eigen modelkeuze uit
     * [AiModelSettings]. `chatModel` is `@Primary`: alles wat geen eigen model vraagt (briefing,
     * zoekopdrachten, geheugen, moestuin) krijgt het model voor "overig".
     */
    @Bean
    @Primary
    fun chatModel(secrets: AppSecrets, gateway: AgentRuntimeGateway, mapper: ObjectMapper, models: AiModelSettings): ChatModel =
        roleModel(AiRole.BACKGROUND, secrets, gateway, mapper, models)

    @Bean
    fun assistantChatModel(secrets: AppSecrets, gateway: AgentRuntimeGateway, mapper: ObjectMapper, models: AiModelSettings): ChatModel =
        roleModel(AiRole.CHAT, secrets, gateway, mapper, models)

    @Bean
    fun nutritionChatModel(secrets: AppSecrets, gateway: AgentRuntimeGateway, mapper: ObjectMapper, models: AiModelSettings): ChatModel =
        roleModel(AiRole.NUTRITION, secrets, gateway, mapper, models)

    private fun roleModel(
        role: AiRole,
        secrets: AppSecrets,
        gateway: AgentRuntimeGateway,
        mapper: ObjectMapper,
        models: AiModelSettings,
    ): ChatModel =
        if (secrets.effectiveMockAi) {
            MockChatModel()
        } else {
            AgentRuntimeChatModel(gateway, ToolCallingManager.builder().build(), mapper, execution = { models.execution(role) })
        }

    @Bean
    @Primary
    fun assistantChatClient(
        @Qualifier("assistantChatModel") chatModel: ChatModel,
        notesTools: NotesTools,
        windTools: WindTools,
        weatherTools: WeatherTools,
        tideTools: TideTools,
        airQualityTools: AirQualityTools,
        newsTools: NewsTools,
        wasteTools: WasteTools,
        automowerTools: AutomowerTools,
        stravaTools: StravaTools,
        softwareFactoryTools: SoftwareFactoryTools,
        openShiftTools: OpenShiftTools,
        reminderTools: ReminderTools,
        alarmTools: AlarmTools,
        calendarTools: CalendarTools,
        docsTools: DocsTools,
        pushTools: PushTools,
        watchTools: WatchTools,
        nutritionTools: NutritionTools,
        diaryTools: DiaryTools,
    ): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(
                notesTools, windTools, weatherTools, tideTools, airQualityTools, newsTools, wasteTools,
                automowerTools, stravaTools, softwareFactoryTools, openShiftTools, reminderTools, alarmTools,
                calendarTools, docsTools, pushTools, watchTools, nutritionTools, diaryTools,
            )
            .build()

    /**
     * Losse, lichte [ChatClient] (geen tools, geen gesprekshistorie) voor het verzinnen van een
     * korte titel na de eerste vraag/antwoord-uitwisseling van een gesprek.
     */
    @Bean
    fun titleChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(TITLE_SYSTEM_PROMPT)
            .build()

    /**
     * Losse, lichte [ChatClient] (geen tools, geen gesprekshistorie) voor het bijwerken van het
     * geheugen na een chat-beurt, zie `AssistantService.updateMemoryFromExchange`.
     */
    @Bean
    fun memoryChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(MEMORY_SYSTEM_PROMPT)
            .build()
}
