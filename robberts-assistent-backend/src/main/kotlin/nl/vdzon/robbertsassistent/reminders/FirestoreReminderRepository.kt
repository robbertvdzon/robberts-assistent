package nl.vdzon.robbertsassistent.reminders

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

/**
 * Firestore-implementatie van [ReminderRepository]. Reminders staan in de collectie `reminders`,
 * document-id = reminder-id. De Firestore-calls zijn async (ApiFuture); we blokkeren met `.get()`
 * omdat de aanroepende service/scheduler synchroon is.
 *
 * NB: nog niet end-to-end geverifieerd tegen een echt Firebase-project — dat gebeurt zodra de
 * service-account-credentials beschikbaar zijn (zie docs/foundation-couplings.md, fase 2).
 */
class FirestoreReminderRepository(private val firestore: Firestore) : ReminderRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(reminder: Reminder): Reminder {
        collection.document(reminder.id).set(reminder.toMap()).get()
        return reminder
    }

    override fun all(): List<Reminder> =
        collection.get().get().documents.mapNotNull { it.toReminder() }

    override fun due(now: Instant): List<Reminder> =
        collection.whereEqualTo(FIELD_DELIVERED, false).get().get().documents
            .mapNotNull { it.toReminder() }
            .filter { !it.dueAt.isAfter(now) }

    override fun markDelivered(id: String) {
        collection.document(id).update(FIELD_DELIVERED, true).get()
    }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Reminder.toMap(): Map<String, Any> = mapOf(
        FIELD_MESSAGE to message,
        FIELD_DUE_AT to dueAt.toEpochMilli(),
        FIELD_DELIVERED to delivered,
    )

    private fun DocumentSnapshot.toReminder(): Reminder? {
        val message = getString(FIELD_MESSAGE) ?: return null
        val dueAtMillis = getLong(FIELD_DUE_AT) ?: return null
        val delivered = getBoolean(FIELD_DELIVERED) ?: false
        return Reminder(id = id, message = message, dueAt = Instant.ofEpochMilli(dueAtMillis), delivered = delivered)
    }

    private companion object {
        const val COLLECTION = "reminders"
        const val FIELD_MESSAGE = "message"
        const val FIELD_DUE_AT = "dueAtEpochMillis"
        const val FIELD_DELIVERED = "delivered"
    }
}
