package nl.vdzon.robbertsassistent.reminders

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import nl.vdzon.robbertsassistent.scheduling.Recurrence
import java.time.Instant

/**
 * Firestore-implementatie van [ReminderRepository]. Reminders in de collectie `reminders`,
 * document-id = reminder-id. `.get()` blokkeert (de aanroepende service/scheduler is synchroon).
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
        collection.whereEqualTo(FIELD_ACTIVE, true).get().get().documents
            .mapNotNull { it.toReminder() }
            .filter { !it.dueAt.isAfter(now) }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Reminder.toMap(): Map<String, Any> = buildMap {
        put(FIELD_MESSAGE, message)
        put(FIELD_DUE_AT, dueAt.toEpochMilli())
        put(FIELD_ACTIVE, active)
        recurrence?.let { put(FIELD_RECURRENCE, it.toMap()) }
    }

    private fun DocumentSnapshot.toReminder(): Reminder? {
        val message = getString(FIELD_MESSAGE) ?: return null
        val dueAtMillis = getLong(FIELD_DUE_AT) ?: return null
        @Suppress("UNCHECKED_CAST")
        val recurrence = Recurrence.fromMap(get(FIELD_RECURRENCE) as? Map<String, Any?>)
        return Reminder(
            id = id,
            message = message,
            dueAt = Instant.ofEpochMilli(dueAtMillis),
            recurrence = recurrence,
            active = getBoolean(FIELD_ACTIVE) ?: true,
        )
    }

    private companion object {
        const val COLLECTION = "reminders"
        const val FIELD_MESSAGE = "message"
        const val FIELD_DUE_AT = "dueAtEpochMillis"
        const val FIELD_ACTIVE = "active"
        const val FIELD_RECURRENCE = "recurrence"
    }
}
