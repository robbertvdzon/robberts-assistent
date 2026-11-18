package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiModelSettingsTest {
    private val secrets = AppSecrets(
        rememberSecret = "x",
        googleClientId = "x",
        allowedEmails = setOf("robbert@vdzon.com"),
        aiVendor = "anthropic",
        aiModel = "claude-sonnet-5",
        aiMode = "SUBSCRIPTION",
    )
    private val repository = InMemoryAiModelRepository()
    private val settings = AiModelSettings(repository, secrets)
    private val opus = RuntimeExecution("anthropic", "claude-opus-5", "SUBSCRIPTION")

    @Test
    fun `zonder keuze geldt de configuratie, met keuze alleen voor die toepassing`() {
        assertEquals(RuntimeExecution("anthropic", "claude-sonnet-5", "SUBSCRIPTION"), settings.execution(AiRole.NUTRITION))
        assertNull(settings.chosen(AiRole.NUTRITION))

        settings.update(AiRole.NUTRITION, opus)

        assertEquals(opus, settings.execution(AiRole.NUTRITION))
        assertEquals(settings.default, settings.execution(AiRole.CHAT))
        assertEquals(settings.default, settings.execution(AiRole.BACKGROUND))

        settings.reset(AiRole.NUTRITION)
        assertEquals(settings.default, settings.execution(AiRole.NUTRITION))
    }

    @Test
    fun `een storing in de opslag valt terug op het standaardmodel`() {
        val broken = object : AiModelRepository {
            override fun find(role: AiRole): RuntimeExecution? = error("opslag onbereikbaar")
            override fun save(role: AiRole, execution: RuntimeExecution) = Unit
            override fun delete(role: AiRole) = Unit
        }
        assertEquals(settings.default, AiModelSettings(broken, secrets).execution(AiRole.CHAT))
    }

    @Test
    fun `het chatmodel geeft de gekozen uitvoering van zijn toepassing mee aan elke job`() {
        val used = mutableListOf<RuntimeExecution?>()
        val gateway = object : AgentRuntimeGateway {
            override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode =
                error("de variant met uitvoering hoort gebruikt te worden")

            override fun runStructured(
                instruction: String,
                resultSchema: JsonNode,
                attachments: List<RuntimeAttachment>,
                execution: RuntimeExecution?,
            ): JsonNode {
                used += execution
                return ObjectMapper().readTree("""{"reply":"ok"}""")
            }

            override fun ping() = true to "ok"
        }
        val model = AgentRuntimeChatModel(
            gateway, ToolCallingManager.builder().build(), ObjectMapper(),
            execution = { settings.execution(AiRole.NUTRITION) },
        )

        model.call(Prompt(UserMessage("hoi")))
        settings.update(AiRole.NUTRITION, opus)
        model.call(Prompt(UserMessage("hoi")))

        // De wijziging geldt direct, zonder herstart: de tweede job draait op het nieuwe model.
        assertEquals(listOf<RuntimeExecution?>(settings.default, opus), used)
    }

    @Test
    fun `beheer-endpoint toont de drie toepassingen, de keuze en de modellen van de runtime`() {
        val gateway = object : AgentRuntimeGateway {
            override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode = error("n.v.t.")
            override fun ping() = true to "ok"
            override fun executionOptions() = listOf(
                RuntimeExecutionOption(settings.default, available = true, onlineWorkers = 2),
                RuntimeExecutionOption(opus, available = false, onlineWorkers = 0),
            )
        }
        val auth = org.mockito.Mockito.mock(nl.vdzon.robbertsassistent.auth.AuthService::class.java)
        val controller = AiModelsController(auth, settings, gateway)

        val before = controller.get(null)
        assertEquals(listOf("CHAT", "NUTRITION", "BACKGROUND"), before.roles.map { it.role })
        assertTrue(before.roles.none { it.custom })
        assertEquals(listOf(true, false), before.options.map { it.available })

        val after = controller.update(null, "nutrition", AiExecutionDto("anthropic", "claude-opus-5", "SUBSCRIPTION"))
        val nutrition = after.roles.single { it.role == "NUTRITION" }
        assertEquals("claude-opus-5", nutrition.execution.model)
        assertTrue(nutrition.custom)
        assertFalse(after.roles.single { it.role == "CHAT" }.custom)

        assertFalse(controller.reset(null, "NUTRITION").roles.single { it.role == "NUTRITION" }.custom)
    }
}
