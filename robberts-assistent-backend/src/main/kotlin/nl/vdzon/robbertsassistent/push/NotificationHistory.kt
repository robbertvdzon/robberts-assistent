package nl.vdzon.robbertsassistent.push

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Eén bericht, onafhankelijk van het aantal telefoons en of FCM het kon afleveren. */
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val createdAt: Instant,
    val readAt: Instant? = null,
)

data class NotificationPage(val items: List<AppNotification>, val nextCursor: String?)

class NotificationNotFoundException : RuntimeException("Melding niet gevonden")

interface NotificationHistory {
    fun save(notification: AppNotification)
    fun page(before: String?, limit: Int): NotificationPage
    fun markRead(id: String, now: Instant): AppNotification
}

class InMemoryNotificationHistory : NotificationHistory {
    private val notifications = ConcurrentHashMap<String, AppNotification>()

    override fun save(notification: AppNotification) {
        notifications[notification.id] = notification
    }

    override fun page(before: String?, limit: Int): NotificationPage {
        val ordered = notifications.values.sortedWith(compareByDescending<AppNotification> { it.createdAt }.thenByDescending { it.id })
        val offset = if (before == null) 0 else {
            val index = ordered.indexOfFirst { it.id == before }
            if (index < 0) throw NotificationNotFoundException()
            index + 1
        }
        return notificationPage(ordered.drop(offset).take(limit + 1), limit)
    }

    override fun markRead(id: String, now: Instant): AppNotification =
        notifications.compute(id) { _, current ->
            current?.let { if (it.readAt == null) it.copy(readAt = now) else it }
                ?: throw NotificationNotFoundException()
        }!!
}

/** Blijvend archief; één veldindex plus document-id als stabiele volgorde bij gelijke tijden. */
class FirestoreNotificationHistory(private val firestore: Firestore) : NotificationHistory {
    private val collection get() = firestore.collection("notification-history")

    override fun save(notification: AppNotification) {
        collection.document(notification.id).set(
            mapOf(
                "title" to notification.title,
                "body" to notification.body,
                "data" to notification.data,
                "createdAt" to notification.createdAt.toEpochMilli(),
                "readAt" to notification.readAt?.toEpochMilli(),
            ),
        ).get()
    }

    override fun page(before: String?, limit: Int): NotificationPage {
        var query = collection.orderBy("createdAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
        if (before != null) {
            val cursor = collection.document(before).get().get()
            if (!cursor.exists()) throw NotificationNotFoundException()
            query = query.startAfter(cursor)
        }
        return notificationPage(query.limit(limit + 1).get().get().documents.map { it.toNotification() }, limit)
    }

    override fun markRead(id: String, now: Instant): AppNotification {
        val document = collection.document(id)
        return firestore.runTransaction<AppNotification?> { transaction ->
            val snapshot = transaction.get(document).get()
            if (!snapshot.exists()) return@runTransaction null
            val current = snapshot.toNotification()
            if (current.readAt != null) current else {
                transaction.update(document, "readAt", now.toEpochMilli())
                current.copy(readAt = now)
            }
        }.get() ?: throw NotificationNotFoundException()
    }

    private fun DocumentSnapshot.toNotification(): AppNotification = AppNotification(
        id = id,
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        data = (get("data") as? Map<*, *>)?.entries?.mapNotNull { (key, value) ->
            if (key is String && value is String) key to value else null
        }?.toMap().orEmpty(),
        createdAt = Instant.ofEpochMilli(getLong("createdAt") ?: 0),
        readAt = getLong("readAt")?.let(Instant::ofEpochMilli),
    )
}

private fun notificationPage(rows: List<AppNotification>, limit: Int): NotificationPage =
    NotificationPage(rows.take(limit), if (rows.size > limit) rows[limit - 1].id else null)
