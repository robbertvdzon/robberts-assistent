package nl.vdzon.robbertsassistent.notifier

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de actieve [Notifier]: [TelegramNotifier] zodra er een bot-token én chat-id geconfigureerd
 * zijn (zie [AppSecrets]), anders de [LoggingNotifier]-fallback. Zo werkt het fundament zonder
 * secrets (log) en wordt het echte kanaal live door alleen de secret te zetten — geen code-wijziging.
 */
@Configuration
class NotifierConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun notifier(secrets: AppSecrets): Notifier {
        val token = secrets.telegramBotToken
        val chatId = secrets.telegramChatId
        return if (!token.isNullOrBlank() && !chatId.isNullOrBlank()) {
            logger.info("Notifier: Telegram actief (chat {})", chatId)
            TelegramNotifier(token, chatId)
        } else {
            logger.info("Notifier: geen Telegram-config, val terug op LoggingNotifier")
            LoggingNotifier()
        }
    }
}
