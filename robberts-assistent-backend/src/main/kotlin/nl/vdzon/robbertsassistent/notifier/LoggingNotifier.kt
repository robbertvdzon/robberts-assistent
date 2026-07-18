package nl.vdzon.robbertsassistent.notifier

import org.slf4j.LoggerFactory

/**
 * Stub-[Notifier] die het bericht alleen logt. De fallback zolang er geen echt kanaal
 * (Telegram/FCM) geconfigureerd is; [NotifierConfig] kiest tussen deze en de echte notifier.
 */
class LoggingNotifier : Notifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(message: String) {
        logger.info("[NOTIFY] {}", message)
    }
}
