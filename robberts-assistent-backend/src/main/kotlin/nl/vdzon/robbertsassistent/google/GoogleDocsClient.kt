package nl.vdzon.robbertsassistent.google

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Leest een Google Doc (read-only) via de Docs REST-API (v1) en zet de body om naar platte tekst
 * door alle `textRun`-fragmenten aan elkaar te plakken. Access-token uit [GoogleOAuthService].
 *
 * NB: nog niet end-to-end geverifieerd (geen echte OAuth-token beschikbaar) — zie
 * docs/foundation-couplings.md, fase 3.
 */
class GoogleDocsClient(
    private val oauth: GoogleOAuthService,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : DocsClient {
    private val objectMapper = jacksonObjectMapper()

    override fun read(documentId: String): String {
        val request = HttpRequest.newBuilder(URI.create("$DOCS_URL/$documentId"))
            .header("Authorization", "Bearer ${oauth.accessToken()}")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google Docs-call faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }
        return extractText(objectMapper.readTree(response.body()))
    }

    /** Loopt door body.content[].paragraph.elements[].textRun.content en plakt alles aan elkaar. */
    private fun extractText(root: JsonNode): String {
        val text = StringBuilder()
        root.path("body").path("content").forEach { structural ->
            structural.path("paragraph").path("elements").forEach { element ->
                element.path("textRun").get("content")?.takeIf { !it.isNull }?.asText()?.let { text.append(it) }
            }
        }
        return text.toString().trim().ifBlank { "(document is leeg of bevat geen tekst)" }
    }

    private companion object {
        const val DOCS_URL = "https://docs.googleapis.com/v1/documents"
    }
}
