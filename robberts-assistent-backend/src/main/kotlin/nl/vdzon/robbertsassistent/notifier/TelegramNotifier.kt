package nl.vdzon.robbertsassistent.notifier

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Stuurt berichten naar Robbert via de Telegram Bot API (`sendMessage`). Gooit een fout bij een
 * niet-2xx-respons, zodat de [nl.vdzon.robbertsassistent.reminders.ReminderScheduler] de reminder
 * niet als afgeleverd markeert en het de volgende tick opnieuw probeert.
 */
class TelegramNotifier(
    private val botToken: String,
    private val chatId: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : Notifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(message: String) {
        val body = "chat_id=" + encode(chatId) + "&text=" + encode(message)
        val request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot$botToken/sendMessage"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Telegram sendMessage faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }
        logger.debug("Telegram-bericht verstuurd naar chat {}", chatId)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
