package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Test het runtime-protocol (uploads, job, polling, resultaat) tegen een lokale nep-runtime. */
class HttpAgentRuntimeGatewayTest {
    private val mapper = ObjectMapper()
    private val requests = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()
    private var finalStatus = "SUCCEEDED"
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex -> handle(ex) }
        start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun reply(ex: HttpExchange, status: Int, body: String = "") {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, if (status == 204) -1 else bytes.size.toLong())
        if (status != 204) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    private fun handle(ex: HttpExchange) {
        val key = "${ex.requestMethod} ${ex.requestURI.path}"
        requests += key
        if (ex.requestHeaders.getFirst("Authorization") != "Bearer geheim") return reply(ex, 401)
        bodies[key] = ex.requestBody.readAllBytes().decodeToString()
        when (key) {
            "POST /v2/uploads" -> reply(ex, 201, """{"uploadId":"u1","chunkSizeBytes":2048,"offset":0}""")
            "PATCH /v2/uploads/u1" -> {
                ex.responseHeaders.add("Upload-Offset", (ex.requestHeaders.getFirst("Upload-Offset").toLong() + bodies.getValue(key).length).toString())
                reply(ex, 204)
            }
            "POST /v2/uploads/u1/complete" -> reply(ex, 200, """{"objectId":"o1"}""")
            "POST /v2/jobs" -> reply(ex, 202, """{"id":"j1","status":"QUEUED"}""")
            "GET /v2/jobs/j1" -> reply(ex, 200, """{"id":"j1","status":"$finalStatus","errorCode":"X","errorMessage":"stuk"}""")
            "GET /v2/jobs/j1/result" -> reply(ex, 200, """{"result":{"reply":"hoi","toolCalls":[]}}""")
            "GET /v2/execution-options" -> reply(ex, 200, "{}")
            else -> reply(ex, 404)
        }
    }

    private fun gateway(token: String = "geheim") = HttpAgentRuntimeGateway(
        baseUrl = "http://127.0.0.1:${server.address.port}",
        token = token,
        execution = RuntimeExecution("anthropic", "claude-sonnet-5", "SUBSCRIPTION"),
        environment = "test",
        mapper = mapper,
        pollInterval = Duration.ofMillis(10),
    )

    @Test
    fun `voert een job uit met bijlage en geeft het resultaat terug`() {
        val result = gateway().runStructured(
            "Zeg hoi", mapper.readTree("""{"type":"object"}"""),
            listOf(RuntimeAttachment("foto1", "foto1.jpeg", "image/jpeg", byteArrayOf(1, 2, 3))),
        )

        assertEquals("hoi", result.path("reply").asText())
        assertEquals(
            listOf("POST /v2/uploads", "PATCH /v2/uploads/u1", "POST /v2/uploads/u1/complete", "POST /v2/jobs", "GET /v2/jobs/j1", "GET /v2/jobs/j1/result"),
            requests,
        )
        val job = mapper.readTree(bodies.getValue("POST /v2/jobs"))
        assertEquals("anthropic", job.path("execution").path("vendorId").asText())
        assertEquals("SUBSCRIPTION", job.path("execution").path("mode").asText())
        assertEquals("o1", job.path("input").path("objects")[0].path("objectId").asText())
        assertTrue(job.path("idempotencyKey").asText().startsWith("ra:test:"))
    }

    @Test
    fun `een mislukte job geeft een fout`() {
        finalStatus = "FAILED"
        val error = assertFailsWith<IOException> { gateway().runStructured("x", mapper.readTree("{}")) }
        assertTrue(error.message!!.contains("stuk"))
    }

    @Test
    fun `een ongeldig token geeft een fout`() {
        assertFailsWith<IOException> { gateway("fout").runStructured("x", mapper.readTree("{}")) }
    }

    @Test
    fun `ping controleert het token`() {
        assertEquals(true, gateway().ping().first)
        assertEquals(false, gateway("").ping().first)
    }
}
