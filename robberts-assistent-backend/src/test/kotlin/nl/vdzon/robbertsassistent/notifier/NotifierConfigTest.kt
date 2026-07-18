package nl.vdzon.robbertsassistent.notifier

import nl.vdzon.robbertsassistent.config.AppSecrets
import kotlin.test.Test
import kotlin.test.assertTrue

class NotifierConfigTest {
    private val config = NotifierConfig()

    private fun secrets(token: String? = null, chatId: String? = null) = AppSecrets(
        rememberSecret = "x",
        googleClientId = "x",
        allowedEmails = setOf("robbert@vdzon.com"),
        telegramBotToken = token,
        telegramChatId = chatId,
    )

    @Test
    fun `zonder telegram-config valt terug op LoggingNotifier`() {
        assertTrue(config.notifier(secrets()) is LoggingNotifier)
    }

    @Test
    fun `met bot-token en chat-id kiest TelegramNotifier`() {
        assertTrue(config.notifier(secrets(token = "123:abc", chatId = "-100")) is TelegramNotifier)
    }

    @Test
    fun `alleen een token zonder chat-id valt terug op LoggingNotifier`() {
        assertTrue(config.notifier(secrets(token = "123:abc")) is LoggingNotifier)
    }
}
