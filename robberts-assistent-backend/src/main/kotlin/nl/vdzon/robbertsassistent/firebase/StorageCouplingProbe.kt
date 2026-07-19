package nl.vdzon.robbertsassistent.firebase

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor Firebase Storage (moestuin-foto's, assistent-gespreksfoto's). */
@Component
class StorageCouplingProbe(private val secrets: AppSecrets, private val firebase: FirebaseProvider) : CouplingProbe {

    private val storageConfigured: Boolean
        get() = secrets.firebaseConfigured && !secrets.firebaseStorageBucket.isNullOrBlank()

    override val id = "storage"
    override val name = "Firebase Storage"
    override val description = "Opslag van de moestuin-foto's."
    override val configured: Boolean get() = storageConfigured
    override val mode: String get() = if (storageConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!storageConfigured) return false to "niet geconfigureerd"
        val bucket = firebase.bucket()
        return true to "bucket ${bucket.name} bereikbaar"
    }
}
