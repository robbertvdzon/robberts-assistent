package nl.vdzon.robbertsassistent.alarms

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import nl.vdzon.robbertsassistent.scheduling.Recurrence
import java.time.Instant

/** Firestore-opslag voor alarms in de collectie `alarms` (document-id = alarm-id). */
class FirestoreAlarmRepository(private val firestore: Firestore) : AlarmRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(alarm: Alarm): Alarm {
        collection.document(alarm.id).set(alarm.toMap()).get()
        return alarm
    }

    override fun all(): List<Alarm> =
        collection.get().get().documents.mapNotNull { it.toAlarm() }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Alarm.toMap(): Map<String, Any> = buildMap {
        put(FIELD_MESSAGE, message)
        put(FIELD_TIME, time.toEpochMilli())
        put(FIELD_ACTIVE, active)
        recurrence?.let { put(FIELD_RECURRENCE, it.toMap()) }
    }

    private fun DocumentSnapshot.toAlarm(): Alarm? {
        val message = getString(FIELD_MESSAGE) ?: return null
        val timeMillis = getLong(FIELD_TIME) ?: return null
        @Suppress("UNCHECKED_CAST")
        val recurrence = Recurrence.fromMap(get(FIELD_RECURRENCE) as? Map<String, Any?>)
        return Alarm(
            id = id,
            message = message,
            time = Instant.ofEpochMilli(timeMillis),
            recurrence = recurrence,
            active = getBoolean(FIELD_ACTIVE) ?: true,
        )
    }

    private companion object {
        const val COLLECTION = "alarms"
        const val FIELD_MESSAGE = "message"
        const val FIELD_TIME = "timeEpochMillis"
        const val FIELD_ACTIVE = "active"
        const val FIELD_RECURRENCE = "recurrence"
    }
}
