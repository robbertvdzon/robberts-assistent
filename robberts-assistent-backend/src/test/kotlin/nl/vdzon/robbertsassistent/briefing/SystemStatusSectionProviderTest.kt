package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.MowerActionResult
import nl.vdzon.robbertsassistent.automower.MowerStatus
import nl.vdzon.robbertsassistent.automower.MowerStatusResult
import nl.vdzon.robbertsassistent.automower.StubAutomowerClient
import nl.vdzon.robbertsassistent.openshift.ClusterHealthResult
import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.StubOpenShiftClient
import nl.vdzon.robbertsassistent.softwarefactory.FactoryMyActionsResult
import nl.vdzon.robbertsassistent.softwarefactory.FactoryStoriesResult
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryClient
import nl.vdzon.robbertsassistent.softwarefactory.StubSoftwareFactoryClient
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Draait tegen [MockChatModel] (deterministisch, geen netwerk) of een [FixedChatModel] met een
 * gecontroleerd antwoord — zelfde patroon als `WeekTasksSectionProviderTest`.
 */
class SystemStatusSectionProviderTest {

    private class ThrowingChatModel : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = error("AI-fout")
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(IllegalStateException("AI-fout"))
    }

    private class FixedChatModel(private val reply: String) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(reply))))
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    private class ThrowingOpenShiftClient : OpenShiftClient {
        override fun clusterHealth(): ClusterHealthResult = error("kapot")
    }

    private class ThrowingAutomowerClient : AutomowerClient {
        override fun status(): MowerStatusResult = error("kapot")
        override fun startMowing(durationMinutes: Int): MowerActionResult = error("kapot")
        override fun park(): MowerActionResult = error("kapot")
    }

    private class ThrowingSoftwareFactoryClient : SoftwareFactoryClient {
        override fun stories(): FactoryStoriesResult = error("kapot")
        override fun myActions(): FactoryMyActionsResult = error("kapot")
    }

    private class ErroredAutomowerClient : AutomowerClient {
        override fun status(): MowerStatusResult = MowerStatusResult(emptyList(), "netwerkfout")
        override fun startMowing(durationMinutes: Int): MowerActionResult = MowerActionResult(false)
        override fun park(): MowerActionResult = MowerActionResult(false)
    }

    private fun errorMower() = MowerStatus(
        name = "Maaier",
        model = "310E NERA",
        mode = "MAIN_AREA",
        activity = "STOPPED_IN_GARDEN",
        state = "FATAL_ERROR",
        batteryPercent = 40,
        errorCode = 42,
        connected = true,
    )

    private class FixedAutomowerClient(private val mower: MowerStatus) : AutomowerClient {
        override fun status(): MowerStatusResult = MowerStatusResult(listOf(mower))
        override fun startMowing(durationMinutes: Int): MowerActionResult = MowerActionResult(true)
        override fun park(): MowerActionResult = MowerActionResult(true)
    }

    private fun provider(
        chatModel: ChatModel = MockChatModel(),
        openShiftClient: OpenShiftClient = StubOpenShiftClient(),
        automowerClient: AutomowerClient = StubAutomowerClient(),
        softwareFactoryClient: SoftwareFactoryClient = StubSoftwareFactoryClient(),
    ) = SystemStatusSectionProvider(
        openShiftClient = openShiftClient,
        automowerClient = automowerClient,
        softwareFactoryClient = softwareFactoryClient,
        chatClient = ChatClient.builder(chatModel).build(),
    )

    @Test
    fun `section geeft de rapporttekst na de AANDACHT-regel terug`() {
        val reply = "AANDACHT: geen\nAlles is in orde."

        val section = provider(chatModel = FixedChatModel(reply)).section()

        assertEquals("system-status", section.key)
        assertEquals("Alles is in orde.", section.text)
    }

    @Test
    fun `shortSummary is null als geen enkele check aandacht nodig heeft`() {
        val reply = "AANDACHT: geen\nAlles is in orde."

        assertNull(provider(chatModel = FixedChatModel(reply)).shortSummary())
    }

    @Test
    fun `shortSummary bevat de aandachtspunten als de AI die meldt`() {
        val reply = "AANDACHT: maaier, software factory\nMaaier staat in fout. Twee stories wachten."

        val summary = provider(chatModel = FixedChatModel(reply)).shortSummary()

        assertTrue(summary != null && summary.contains("maaier"))
        assertTrue(summary != null && summary.contains("software factory"))
    }

    @Test
    fun `section valt terug op een neutrale tekst als de AI-call faalt`() {
        val section = provider(chatModel = ThrowingChatModel()).section()

        assertEquals("Kon het systeemstatusrapport niet ophalen.", section.text)
    }

    @Test
    fun `shortSummary is null als de AI-call faalt`() {
        assertNull(provider(chatModel = ThrowingChatModel()).shortSummary())
    }

    @Test
    fun `een falende OpenShift-client crasht de sectie niet`() {
        val section = provider(chatModel = FixedChatModel("AANDACHT: geen\nOk."), openShiftClient = ThrowingOpenShiftClient()).section()

        assertEquals("Ok.", section.text)
    }

    @Test
    fun `een falende Automower-client crasht de sectie niet`() {
        val section = provider(chatModel = FixedChatModel("AANDACHT: geen\nOk."), automowerClient = ThrowingAutomowerClient()).section()

        assertEquals("Ok.", section.text)
    }

    @Test
    fun `een falende Software Factory-client crasht de sectie niet`() {
        val section = provider(
            chatModel = FixedChatModel("AANDACHT: geen\nOk."),
            softwareFactoryClient = ThrowingSoftwareFactoryClient(),
        ).section()

        assertEquals("Ok.", section.text)
    }

    @Test
    fun `een maaier-fout-status komt in de ruwe data terecht (foutcode en state)`() {
        var capturedPrompt: String? = null
        val chatClient = ChatClient.builder(object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse {
                capturedPrompt = prompt.instructions.last().text
                return ChatResponse(listOf(Generation(AssistantMessage("AANDACHT: geen\nOk."))))
            }

            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
        }).build()

        SystemStatusSectionProvider(
            openShiftClient = StubOpenShiftClient(),
            automowerClient = FixedAutomowerClient(errorMower()),
            softwareFactoryClient = StubSoftwareFactoryClient(),
            chatClient = chatClient,
        ).section()

        assertTrue(capturedPrompt != null && capturedPrompt!!.contains("errorCode=42"))
        assertTrue(capturedPrompt!!.contains("FATAL_ERROR") || capturedPrompt!!.contains("kritieke fout"))
    }

    @Test
    fun `het geheugen-SSD-externe-HDD-gebruik en de update-versie komen in de ruwe data terecht`() {
        var capturedPrompt: String? = null
        val chatClient = ChatClient.builder(object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse {
                capturedPrompt = prompt.instructions.last().text
                return ChatResponse(listOf(Generation(AssistantMessage("AANDACHT: geen\nOk."))))
            }

            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
        }).build()
        val openShiftClient = object : OpenShiftClient {
            override fun clusterHealth() = ClusterHealthResult(
                healthy = true,
                clusterVersion = "4.16.3",
                updateAvailable = true,
                degradedOperators = emptyList(),
                availableUpdateVersions = listOf("4.16.4"),
                nodeMetrics = nl.vdzon.robbertsassistent.openshift.NodeMetrics(
                    ssd = nl.vdzon.robbertsassistent.openshift.DiskUsage(totalGb = 240.0, usedGb = 220.0, freeGb = 20.0, usedPercent = 91.7),
                ),
            )
        }

        SystemStatusSectionProvider(
            openShiftClient = openShiftClient,
            automowerClient = StubAutomowerClient(),
            softwareFactoryClient = StubSoftwareFactoryClient(),
            chatClient = chatClient,
        ).section()

        assertTrue(capturedPrompt != null && capturedPrompt!!.contains("4.16.4"), capturedPrompt)
        assertTrue(capturedPrompt!!.contains("SSD"), capturedPrompt)
        assertTrue(capturedPrompt!!.contains("91.7"), capturedPrompt)
    }

    @Test
    fun `de netwerkfout van de Automower-client komt in de ruwe data terecht`() {
        var capturedPrompt: String? = null
        val chatClient = ChatClient.builder(object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse {
                capturedPrompt = prompt.instructions.last().text
                return ChatResponse(listOf(Generation(AssistantMessage("AANDACHT: geen\nOk."))))
            }

            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
        }).build()

        SystemStatusSectionProvider(
            openShiftClient = StubOpenShiftClient(),
            automowerClient = ErroredAutomowerClient(),
            softwareFactoryClient = StubSoftwareFactoryClient(),
            chatClient = chatClient,
        ).section()

        assertTrue(capturedPrompt != null && capturedPrompt!!.contains("netwerkfout"))
    }

    @Test
    fun `deterministisch onder mock-ai`() {
        val a = provider().section().text
        val b = provider().section().text

        assertTrue(a.isNotBlank())
        assertEquals(a, b)
    }

    // --- parseAiReply (pure functie) ---

    @Test
    fun `parseAiReply herkent AANDACHT-geen en levert de rapporttekst`() {
        val result = SystemStatusSectionProvider.parseAiReply("AANDACHT: geen\nAlles in orde.\nNiets te melden.")

        assertTrue(result.attentionItems.isEmpty())
        assertEquals("Alles in orde.\nNiets te melden.", result.text)
    }

    @Test
    fun `parseAiReply parseert een kommagescheiden aandachtslijst`() {
        val result = SystemStatusSectionProvider.parseAiReply("AANDACHT: maaier, backups\nRapport hier.")

        assertEquals(listOf("maaier", "backups"), result.attentionItems)
        assertTrue(result.hasAttention)
    }

    @Test
    fun `parseAiReply valt terug op de volledige tekst zonder aandachtspunten bij een onherkend formaat`() {
        val result = SystemStatusSectionProvider.parseAiReply("Zomaar een antwoord zonder het verwachte formaat.")

        assertTrue(result.attentionItems.isEmpty())
        assertEquals("Zomaar een antwoord zonder het verwachte formaat.", result.text)
    }
}
