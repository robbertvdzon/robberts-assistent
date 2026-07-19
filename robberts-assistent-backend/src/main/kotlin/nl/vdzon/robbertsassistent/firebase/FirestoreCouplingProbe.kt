package nl.vdzon.robbertsassistent.firebase

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor Firestore (notities, reminders, alarms, chat-historie, FCM-tokens). */
@Component
class FirestoreCouplingProbe(private val firebase: FirebaseProvider) : CouplingProbe {

    override val id = "firestore"
    override val name = "Firestore"
    override val description = "Notities, reminders, alarms, chat-historie, FCM-tokens."
    override val configured: Boolean get() = firebase.isConfigured
    override val mode: String get() = if (firebase.isConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!firebase.isConfigured) return false to "niet geconfigureerd (in-memory)"
        // Generieke bereikbaarheidscheck (ListCollectionIds) — geen aanname over welke module
        // welke collectie gebruikt, dat hoort de firebase-module niet te weten.
        val collections = firebase.firestore().listCollections().toList()
        return true to "bereikbaar (${collections.size} top-level collectie(s))"
    }
}
