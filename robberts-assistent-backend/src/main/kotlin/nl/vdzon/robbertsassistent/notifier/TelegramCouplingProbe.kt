package nl.vdzon.robbertsassistent.notifier

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Koppelingsstatus voor Telegram (uitgaande meldingen), voor het "Koppelingen"-scherm. */
@Component
class TelegramCouplingProbe(private val secrets: AppSecrets) : CouplingProbe {

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    private val telegramConfigured: Boolean
        get() = !secrets.telegramBotToken.isNullOrBlank() && !secrets.telegramChatId.isNullOrBlank()

    override val id = "telegram"
    override val name = "Telegram"
    override val description = "Uitgaande meldingen naar je Telegram."
    override val configured: Boolean get() = telegramConfigured
    override val mode: String get() = if (telegramConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        val token = secrets.telegramBotToken
        if (token.isNullOrBlank()) return false to "geen bot-token"
        val request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot$token/getMe"))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() in 200..299) true to "bot bereikbaar" else false to "HTTP ${response.statusCode()}"
    }
}
