package nl.vdzon.robbertsassistent.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Stuurt push-notificaties (FCM) naar de geregistreerde telefoon(s). Zonder Firebase of zonder
 * geregistreerde tokens is het een no-op (return 0). Verlopen/ongeldige tokens worden opgeruimd.
 */
@Service
class PushService(
    private val firebase: FirebaseProvider,
    private val tokenStore: FcmTokenStore,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Stuurt naar alle geregistreerde tokens; geeft terug naar hoeveel toestellen verstuurd is.
     * [data] gaat als extra FCM-data-payload mee (naast `title`/`body`, bv. `"type" to "briefing"`)
     * zodat de app bij het tikken op de melding kan bepalen welk scherm te openen (deep-link),
     * zie `FcmService` in `robberts_assistent`.
     */
    fun sendToAll(title: String, body: String, data: Map<String, String> = emptyMap()): Int {
        if (!firebase.isConfigured) {
            logger.info("Push overgeslagen: Firebase niet geconfigureerd")
            return 0
        }
        val tokens = tokenStore.all()
        if (tokens.isEmpty()) {
            logger.info("Push overgeslagen: geen geregistreerde FCM-tokens")
            return 0
        }
        val messaging = firebase.messaging()
        var sent = 0
        tokens.forEach { token ->
            runCatching {
                val message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    // Hoge prioriteit + high-importance kanaal met geluid: heads-up melding,
                    // zichtbaar op het lockscreen en spiegelt naar een gekoppeld horloge.
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(
                                AndroidNotification.builder()
                                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                                    .setSound("default")
                                    .build(),
                            )
                            .build(),
                    )
                    .putData("title", title)
                    .putData("body", body)
                    .putAllData(data)
                    .build()
                messaging.send(message)
                sent++
            }.onFailure { ex ->
                val code = (ex as? FirebaseMessagingException)?.messagingErrorCode
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    tokenStore.remove(token)
                    logger.info("FCM-token opgeruimd ({})", code)
                } else {
                    logger.warn("FCM-push faalde: {}", ex.message)
                }
            }
        }
        return sent
    }

    private companion object {
        // Moet gelijk zijn aan het kanaal dat de app aanmaakt (zie FcmService in robberts_assistent).
        const val NOTIFICATION_CHANNEL_ID = "assistent_meldingen"
    }
}
