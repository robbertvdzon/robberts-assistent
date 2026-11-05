package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** Bijlage bij een runtime-job (bv. een foto die de AI moet bekijken, of een te lange prompt). */
class RuntimeAttachment(
    val name: String,
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
    /** `PROMPT` voor een te lange opdracht, anders `SOURCE`. */
    val role: String = "SOURCE",
)

/**
 * Poort naar de Agent Runtime: voer één AI-opdracht uit en geef het gestructureerde resultaat
 * terug. Alle AI in deze applicatie loopt hierdoorheen — er is bewust geen directe verbinding met
 * een AI-provider (OpenAI, Anthropic, ...). Zie [HttpAgentRuntimeGateway] voor de echte
 * implementatie.
 */
interface AgentRuntimeGateway {
    /** Voert [instruction] uit en geeft het `result`-object (conform [resultSchema]) terug. */
    fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment> = emptyList()): JsonNode

    /** Lichte controle (geen job): bereikbaar en token geldig. */
    fun ping(): Pair<Boolean, String>
}

/** Welk model de runtime voor deze applicatie gebruikt. `SUBSCRIPTION` = op het abonnement, geen API-kosten. */
data class RuntimeExecution(val vendorId: String, val model: String, val mode: String) {
    override fun toString() = "$vendorId/$model/$mode"
}

/**
 * [AgentRuntimeGateway] op de Agent Runtime v2 HTTP-API: bijlagen via resumable uploads,
 * `POST /v2/jobs`, pollen tot een eindstatus en `GET /v2/jobs/{id}/result`. Elke aanroep is een
 * nieuwe job (unieke idempotency-key): chat-beurten mogen nooit een eerder antwoord hergebruiken.
 */
class HttpAgentRuntimeGateway(
    private val baseUrl: String,
    private val token: String,
    private val execution: RuntimeExecution,
    private val environment: String,
    private val mapper: ObjectMapper,
    private val executionTimeoutSeconds: Int = 900,
    private val maxWait: Duration = Duration.ofMinutes(20),
    private val pollInterval: Duration = Duration.ofMillis(1500),
) : AgentRuntimeGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode {
        require(token.isNotBlank()) { "RA_AGENT_RUNTIME_TOKEN ontbreekt" }
        val started = Instant.now()
        val allAttachments = attachments.toMutableList()
        val inlineInstruction = if (instruction.length > MAX_INLINE_INSTRUCTION) {
            allAttachments += RuntimeAttachment("prompt", "prompt.txt", "text/plain", instruction.toByteArray(), role = "PROMPT")
            "Lees de volledige opdracht in het invoerobject 'prompt' (/job/input/objects/prompt/content) en voer die exact uit."
        } else instruction
        val objects = allAttachments.map { mapOf("objectId" to upload(it), "name" to it.name, "role" to it.role) }
        val body: ObjectNode = mapper.createObjectNode().apply {
            put("idempotencyKey", "ra:$environment:${UUID.randomUUID()}")
            put("jobKind", "APPLICATION_WORK")
            put("taskType", "STRUCTURED_GENERATION")
            putPOJO("execution", mapOf("vendorId" to execution.vendorId, "model" to execution.model, "mode" to execution.mode))
            putPOJO("input", mapOf("instruction" to inlineInstruction, "objects" to objects))
            val output = putObject("output")
            output.set<JsonNode>("resultSchema", resultSchema)
            output.putArray("artifacts")
            put("executionTimeoutSeconds", executionTimeoutSeconds)
        }
        val response = send(
            HttpRequest.newBuilder(uri("/v2/jobs")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).timeout(Duration.ofSeconds(60)),
        )
        if (response.statusCode() != 202 && response.statusCode() != 200) {
            throw IOException("Job aanmaken gaf HTTP ${response.statusCode()}: ${response.body().take(500)}")
        }
        val jobId = mapper.readTree(response.body()).path("id").asText()
        check(jobId.isNotBlank()) { "Runtime gaf geen job-id terug" }
        val outcome = await(jobId)
        if (outcome.first != "SUCCEEDED") throw IOException("Runtime-job $jobId eindigde met ${outcome.second}")
        val result = getJson("/v2/jobs/$jobId/result").path("result")
        log.info("[AgentRuntime] job {} ({}) klaar in {} ms", jobId, execution, Duration.between(started, Instant.now()).toMillis())
        return result
    }

    override fun ping(): Pair<Boolean, String> {
        if (token.isBlank()) return false to "geen token (mock actief)"
        val response = send(HttpRequest.newBuilder(uri("/v2/execution-options")).GET().timeout(Duration.ofSeconds(10)))
        return if (response.statusCode() in 200..299) true to "token geldig ($execution)" else false to "HTTP ${response.statusCode()}"
    }

    private fun await(jobId: String): Pair<String, String> {
        val deadline = Instant.now().plus(maxWait)
        var failures = 0
        while (Instant.now().isBefore(deadline)) {
            val job = try {
                getJson("/v2/jobs/$jobId").also { failures = 0 }
            } catch (e: IOException) {
                if (++failures >= 20) throw e
                Thread.sleep(pollInterval.toMillis() * 3)
                continue
            }
            when (val status = job.path("status").asText("")) {
                "SUCCEEDED", "FAILED", "CANCELLED" -> {
                    val message = listOf(job.path("errorCode").asText(""), job.path("errorMessage").asText(""))
                        .filter { it.isNotBlank() }.joinToString(": ").ifBlank { status }
                    return status to message
                }
            }
            Thread.sleep(pollInterval.toMillis())
        }
        runCatching { send(HttpRequest.newBuilder(uri("/v2/jobs/$jobId/cancel")).POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(30))) }
        throw IOException("Job $jobId niet klaar binnen ${maxWait.toMinutes()} minuten")
    }

    private fun upload(attachment: RuntimeAttachment): String {
        val size = attachment.bytes.size.toLong()
        val reservation = mapper.readTree(
            send(
                HttpRequest.newBuilder(uri("/v2/uploads")).header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(
                                mapOf("filename" to attachment.filename, "mimeType" to attachment.mimeType, "sizeBytes" to size, "sha256" to sha256(attachment.bytes)),
                            ),
                        ),
                    ).timeout(Duration.ofSeconds(60)),
                expect = 201,
            ).body(),
        )
        val uploadId = reservation.path("uploadId").asText()
        val chunk = reservation.path("chunkSizeBytes").asLong(8L * 1024 * 1024).coerceAtLeast(1024)
        var offset = reservation.path("offset").asLong(0)
        while (offset < size) {
            val end = minOf(offset + chunk, size).toInt()
            val part = attachment.bytes.copyOfRange(offset.toInt(), end)
            val response = send(
                HttpRequest.newBuilder(uri("/v2/uploads/$uploadId")).header("Upload-Offset", offset.toString())
                    .header("Content-Type", "application/offset+octet-stream").method("PATCH", HttpRequest.BodyPublishers.ofByteArray(part))
                    .timeout(Duration.ofMinutes(5)),
                expect = 204,
            )
            offset = response.headers().firstValue("Upload-Offset").map(String::toLong).orElse(end.toLong())
        }
        val completed = send(
            HttpRequest.newBuilder(uri("/v2/uploads/$uploadId/complete")).POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofMinutes(2)),
            expect = 200,
        )
        return mapper.readTree(completed.body()).path("objectId").asText()
    }

    private fun getJson(path: String): JsonNode =
        mapper.readTree(send(HttpRequest.newBuilder(uri(path)).GET().timeout(Duration.ofSeconds(60)), expect = 200).body())

    private fun send(builder: HttpRequest.Builder, expect: Int? = null): HttpResponse<String> {
        val response = http.send(builder.header("Authorization", "Bearer $token").build(), HttpResponse.BodyHandlers.ofString())
        if (expect != null && response.statusCode() != expect) {
            throw IOException("Agent Runtime gaf HTTP ${response.statusCode()}: ${response.body().take(500)}")
        }
        return response
    }

    private fun uri(path: String) = URI.create(baseUrl.removeSuffix("/") + path)

    companion object {
        /** Ruim onder de runtime-limiet van 65.536 tekens; langere opdrachten gaan als bijlage. */
        const val MAX_INLINE_INSTRUCTION = 60_000

        private fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
