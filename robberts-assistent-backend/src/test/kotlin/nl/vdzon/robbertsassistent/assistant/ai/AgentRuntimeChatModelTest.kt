package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test de tool-loop van [AgentRuntimeChatModel] met een gescripte runtime (geen netwerk). */
class AgentRuntimeChatModelTest {
    private val mapper = ObjectMapper()

    private class ScriptedGateway(private val mapper: ObjectMapper, private val results: List<String>) : AgentRuntimeGateway {
        val instructions = mutableListOf<String>()
        val attachments = mutableListOf<List<RuntimeAttachment>>()

        override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode {
            instructions += instruction
            this.attachments += attachments
            return mapper.readTree(results[minOf(instructions.size - 1, results.size - 1)])
        }

        override fun ping() = true to "ok"
    }

    class WeerTools {
        var calls = 0

        @Tool(description = "Geeft het weer voor een plaats")
        fun weer(@ToolParam(description = "plaatsnaam") plaats: String): String {
            calls++
            return "zonnig in $plaats"
        }
    }

    private fun client(gateway: AgentRuntimeGateway, tools: WeerTools? = null, maxRounds: Int = AgentRuntimeChatModel.MAX_ROUNDS): ChatClient {
        val model = AgentRuntimeChatModel(gateway, ToolCallingManager.builder().build(), mapper, maxRounds)
        return ChatClient.builder(model).defaultSystem("Je bent de assistent.").apply { if (tools != null) defaultTools(tools) }.build()
    }

    @Test
    fun `antwoord zonder tools is een enkele runtime-job`() {
        val gateway = ScriptedGateway(mapper, listOf("""{"reply":"Hallo!","toolCalls":[]}"""))

        val reply = client(gateway).prompt().user("Hoi").call().content()

        assertEquals("Hallo!", reply)
        assertEquals(1, gateway.instructions.size)
        assertTrue(gateway.instructions[0].contains("Je bent de assistent."))
        assertTrue(gateway.instructions[0].contains("Hoi"))
    }

    @Test
    fun `tool-calls worden lokaal uitgevoerd en het resultaat gaat naar de volgende job`() {
        val tools = WeerTools()
        val gateway = ScriptedGateway(
            mapper,
            listOf(
                """{"reply":"","toolCalls":[{"name":"weer","arguments":"{\"plaats\":\"Heemskerk\"}"}]}""",
                """{"reply":"Het is zonnig in Heemskerk.","toolCalls":[]}""",
            ),
        )

        val reply = client(gateway, tools).prompt().user("Hoe is het weer in Heemskerk?").call().content()

        assertEquals("Het is zonnig in Heemskerk.", reply)
        assertEquals(1, tools.calls)
        assertEquals(2, gateway.instructions.size)
        assertTrue(gateway.instructions[0].contains("weer"), "tool-definitie hoort in de eerste job")
        assertTrue(gateway.instructions[1].contains("TOOL-RESULTAAT"))
        assertTrue(gateway.instructions[1].contains("zonnig in Heemskerk"))
    }

    @Test
    fun `foto's gaan als bijlage mee`() {
        val gateway = ScriptedGateway(mapper, listOf("""{"reply":"Een tomatenplant.","toolCalls":[]}"""))
        val bytes = byteArrayOf(1, 2, 3, 4)

        val reply = client(gateway).prompt()
            .user { u -> u.text("Wat is dit?").media(org.springframework.ai.content.Media(MimeTypeUtils.IMAGE_JPEG, ByteArrayResource(bytes))) }
            .call().content()

        assertEquals("Een tomatenplant.", reply)
        val sent = gateway.attachments.single().single()
        assertEquals("foto1", sent.name)
        assertEquals("image/jpeg", sent.mimeType)
        assertTrue(sent.bytes.contentEquals(bytes))
        assertTrue(gateway.instructions[0].contains("/job/input/objects/foto1/content"))
    }

    @Test
    fun `stopt na het maximum aantal tool-rondes`() {
        val tools = WeerTools()
        val loop = """{"reply":"","toolCalls":[{"name":"weer","arguments":"{\"plaats\":\"X\"}"}]}"""
        val gateway = ScriptedGateway(mapper, listOf(loop))

        val reply = client(gateway, tools, maxRounds = 3).prompt().user("weer?").call().content()

        assertEquals(3, gateway.instructions.size)
        assertTrue(reply!!.startsWith("Sorry"))
    }
}
