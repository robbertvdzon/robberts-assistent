package nl.vdzon.robbertsassistent.notifier

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

/**
 * Stub-[Notifier] die het bericht alleen logt. Actief zolang er geen echt kanaal geconfigureerd
 * is (bv. Telegram in fase 1). Dankzij [ConditionalOnMissingBean] neemt een echte Notifier —
 * zodra die als bean bestaat — automatisch de plek over, zonder code-wijziging elders.
 */
@Configuration
class LoggingNotifierConfig {
    @Bean
    @ConditionalOnMissingBean(Notifier::class)
    fun loggingNotifier(): Notifier = LoggingNotifier()
}

class LoggingNotifier : Notifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(message: String) {
        logger.info("[NOTIFY] {}", message)
    }
}
