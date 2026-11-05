package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.tool.definition.ToolDefinition
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * [ChatModel] die ALLE AI van deze applicatie via de Agent Runtime laat lopen (geen directe
 * provider-aanroepen). De runtime kent geen tool-calling-protocol, alleen taken met een
 * gestructureerd resultaat; daarom doet dit model zelf de tool-loop:
 *
 * 1. Eén runtime-job krijgt het systeemprompt, het gesprek (inclusief eerdere tool-resultaten),
 *    de beschikbare tools en de foto's, en geeft `{reply, toolCalls}` terug.
 * 2. Zijn er `toolCalls`, dan voert Spring AI's [ToolCallingManager] ze lokaal uit (de `@Tool`-
 *    methoden van de assistent) en volgt een nieuwe job met de uitkomsten.
 * 3. Zonder `toolCalls` is `reply` het definitieve antwoord.
 *
 * Elke ronde is een volledige job (op het abonnement, dus langzamer dan een directe API-call).
 */
class AgentRuntimeChatModel(
    private val gateway: AgentRuntimeGateway,
    private val toolCallingManager: ToolCallingManager,
    private val mapper: ObjectMapper,
    private val maxRounds: Int = MAX_ROUNDS,
) : ChatModel {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getDefaultOptions(): ChatOptions = ToolCallingChatOptions.builder().build()

    override fun call(prompt: Prompt): ChatResponse {
        var current = prompt
        repeat(maxRounds) { round ->
            val response = step(current)
            val assistant = response.result.output
            if (!assistant.hasToolCalls() || !ToolCallingChatOptions.isInternalToolExecutionEnabled(current.options)) return response
            log.info("[AgentRuntimeChatModel] ronde {}: {} tool-call(s)", round + 1, assistant.toolCalls.size)
            val executed = toolCallingManager.executeToolCalls(current, response)
            if (executed.returnDirect()) {
                return ChatResponse.builder().generations(ToolExecutionResult.buildGenerations(executed)).build()
            }
            current = Prompt(executed.conversationHistory(), current.options)
        }
        log.warn("[AgentRuntimeChatModel] geen eindantwoord na {} rondes", maxRounds)
        return ChatResponse(listOf(Generation(AssistantMessage("Sorry, ik kwam er met de beschikbare tools niet uit."))))
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))

    /** Eén runtime-job: bepaalt het volgende antwoord of de volgende tool-calls. */
    private fun step(prompt: Prompt): ChatResponse {
        val tools = (prompt.options as? ToolCallingChatOptions)?.let { toolCallingManager.resolveToolDefinitions(it) }.orEmpty()
        val attachments = mutableListOf<RuntimeAttachment>()
        val instruction = buildInstruction(prompt.instructions, tools, attachments)
        val result = gateway.runStructured(instruction, RESULT_SCHEMA, attachments)
        return toResponse(result)
    }

    private fun toResponse(result: JsonNode): ChatResponse {
        val reply = result.path("reply").asText("").trim()
        val toolCalls = result.path("toolCalls").map { call ->
            val arguments = call.path("arguments")
            AssistantMessage.ToolCall(
                "call_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                "function",
                call.path("name").asText(),
                if (arguments.isTextual) arguments.asText() else if (arguments.isMissingNode || arguments.isNull) "{}" else arguments.toString(),
            )
        }.filter { it.name().isNotBlank() }
        val message = AssistantMessage.builder().content(reply).toolCalls(toolCalls).build()
        return ChatResponse(listOf(Generation(message)))
    }

    internal fun buildInstruction(messages: List<Message>, tools: List<ToolDefinition>, attachments: MutableList<RuntimeAttachment>): String {
        val out = StringBuilder()
        out.appendLine(PROTOCOL)
        if (tools.isEmpty()) {
            out.appendLine("\nEr zijn in deze stap GEEN tools beschikbaar: `toolCalls` blijft altijd leeg.")
        } else {
            out.appendLine("\n## Beschikbare tools")
            tools.forEach { tool ->
                out.appendLine("- ${tool.name()}: ${tool.description().replace('\n', ' ')}")
                out.appendLine("  parameters (JSON-schema): ${tool.inputSchema().replace(Regex("\\s+"), " ")}")
            }
        }
        out.appendLine("\n## Gesprek")
        messages.forEach { message ->
            when (message) {
                is SystemMessage -> out.appendLine("\n[SYSTEEM]\n${message.text}")
                is UserMessage -> {
                    out.appendLine("\n[GEBRUIKER]\n${message.text}")
                    message.media.forEach { media ->
                        val index = attachments.count { it.name.startsWith("foto") } + 1
                        val extension = media.mimeType.subtype.substringBefore('+').ifBlank { "jpg" }
                        val name = "foto$index"
                        attachments += RuntimeAttachment(name, "$name.$extension", media.mimeType.toString(), media.dataAsByteArray)
                        out.appendLine("(bijgevoegde foto: invoerobject '$name', /job/input/objects/$name/content — bekijk die afbeelding)")
                    }
                }
                is AssistantMessage -> {
                    if (message.text?.isNotBlank() == true) out.appendLine("\n[ASSISTENT]\n${message.text}")
                    message.toolCalls.forEach { out.appendLine("\n[ASSISTENT TOOL-CALL ${it.id()}] ${it.name()} ${it.arguments()}") }
                }
                is ToolResponseMessage -> message.responses.forEach {
                    out.appendLine("\n[TOOL-RESULTAAT ${it.id()} ${it.name()}]\n${it.responseData()}")
                }
                else -> out.appendLine("\n[${message.messageType}]\n${message.text}")
            }
        }
        out.appendLine("\n## Jouw taak nu\nGeef het volgende antwoord of de volgende tool-calls, als JSON volgens het resultaatschema.")
        return out.toString()
    }

    companion object {
        /** Bovengrens voor tool-rondes per chat-beurt; voorkomt een oneindige loop. */
        const val MAX_ROUNDS = 8

        private val PROTOCOL = """
            Je bent het taalmodel achter een chat-assistent. Je krijgt hieronder de systeeminstructies, het
            gesprek en (eventueel) de beschikbare tools. Je geeft ALLEEN het gevraagde JSON-resultaat terug.
            - Heb je een tool nodig: zet de aanroepen in `toolCalls` (`name` = toolnaam, `arguments` = een
              JSON-object als tekst, volgens het parameterschema) en laat `reply` leeg. Je krijgt daarna de
              resultaten terug in het gesprek.
            - Heb je genoeg informatie: laat `toolCalls` leeg en zet het definitieve antwoord aan de gebruiker in
              `reply`, in de taal en stijl die de systeeminstructies vragen.
            - Verzin geen toolresultaten en gebruik alleen tools uit de lijst. Gebruik zelf geen shell-,
              bestands- of webtools; de enige acties zijn de genoemde tools.
        """.trimIndent()

        val RESULT_SCHEMA: JsonNode = ObjectMapper().readTree(
            """
            {"type":"object","required":["reply","toolCalls"],"additionalProperties":false,
             "properties":{
               "reply":{"type":"string"},
               "toolCalls":{"type":"array","items":{"type":"object","required":["name","arguments"],"additionalProperties":false,
                 "properties":{"name":{"type":"string"},"arguments":{"type":"string"}}}}}}
            """.trimIndent(),
        )
    }
}
