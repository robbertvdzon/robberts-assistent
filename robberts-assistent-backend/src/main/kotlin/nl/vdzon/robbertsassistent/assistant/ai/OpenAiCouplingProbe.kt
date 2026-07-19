package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Koppelingsstatus voor OpenAI (chat-assistent + moestuin-vision), voor het "Koppelingen"-scherm. */
@Component
class OpenAiCouplingProbe(private val secrets: AppSecrets) : CouplingProbe {

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    override val id = "openai"
    override val name = "OpenAI (chat + vision)"
    override val description = "De AI-assistent en de moestuin-foto-chat."
    override val configured: Boolean get() = !secrets.openAiApiKey.isNullOrBlank()
    override val mode: String get() = if (secrets.effectiveMockAi) "fallback" else "echt"

    override fun test(): Pair<Boolean, String> {
        val key = secrets.openAiApiKey
        if (key.isNullOrBlank()) return false to "geen API-key (mock actief)"
        val request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
            .header("Authorization", "Bearer $key")
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() in 200..299) true to "API-key geldig" else false to "HTTP ${response.statusCode()}"
    }
}
