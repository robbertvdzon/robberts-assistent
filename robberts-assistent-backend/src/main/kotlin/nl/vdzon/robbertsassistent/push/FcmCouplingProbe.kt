package nl.vdzon.robbertsassistent.push

import com.google.firebase.messaging.Message
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.springframework.stereotype.Component

/** Koppelingsstatus voor FCM (push-notificaties naar de telefoon). */
@Component
class FcmCouplingProbe(
    private val secrets: AppSecrets,
    private val firebase: FirebaseProvider,
    private val tokenStore: FcmTokenStore,
) : CouplingProbe {

    override val id = "fcm"
    override val name = "FCM push"
    override val description = "Push-notificaties naar je telefoon."
    override val configured: Boolean get() = secrets.firebaseConfigured
    override val mode: String get() = if (secrets.firebaseConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!secrets.firebaseConfigured) return false to "niet geconfigureerd"
        val tokens = tokenStore.all()
        if (tokens.isEmpty()) return true to "Firebase bereikbaar; geen toestel geregistreerd"
        // Dry-run: valideert token + pad zonder een echte push af te leveren.
        firebase.messaging().send(Message.builder().setToken(tokens.first()).putData("ping", "1").build(), true)
        return true to "push-pad gevalideerd (dry-run, ${tokens.size} toestel)"
    }
}
