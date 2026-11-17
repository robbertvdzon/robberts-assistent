package nl.vdzon.robbertsassistent.wellbeing

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Waar een check-in vandaan kwam. Van belang voor de analyse: alleen MELDING is een "gewoon moment". */
enum class CheckInSource { FOTO, APP, MELDING, CHAT }

/**
 * Hoe Robbert zich op één moment voelt. Twee losse schalen: [hunger] 0 = geen, 1 = trek,
 * 2 = honger, 3 = slap van de honger; [energy] 1 = moe … 5 = fit. Elk mag ontbreken.
 */
data class CheckIn(
    val id: String,
    val at: Instant,
    val hunger: Int? = null,
    val energy: Int? = null,
    val note: String = "",
    val source: CheckInSource = CheckInSource.APP,
    /** Het eetmoment waar deze check-in bij hoort (gevoel net vóór het eten), als dat er is. */
    val mealId: String? = null,
)

interface CheckInRepository {
    fun save(checkIn: CheckIn): CheckIn

    /** Check-ins met `from <= at < to`, oplopend in tijd. */
    fun findBetween(from: Instant, to: Instant): List<CheckIn>
    fun delete(id: String)
}

class InMemoryCheckInRepository : CheckInRepository {
    private val store = ConcurrentHashMap<String, CheckIn>()

    override fun save(checkIn: CheckIn): CheckIn = checkIn.also { store[it.id] = it }
    override fun findBetween(from: Instant, to: Instant): List<CheckIn> =
        store.values.filter { !it.at.isBefore(from) && it.at.isBefore(to) }.sortedBy { it.at }

    override fun delete(id: String) {
        store.remove(id)
    }
}

/** Eén document per check-in in de collectie `checkins`; tijd als epoch-millis. */
class FirestoreCheckInRepository(private val firestore: Firestore) : CheckInRepository {
    private val collection get() = firestore.collection("checkins")

    override fun save(checkIn: CheckIn): CheckIn {
        collection.document(checkIn.id).set(checkIn.toMap()).get()
        return checkIn
    }

    override fun findBetween(from: Instant, to: Instant): List<CheckIn> =
        collection
            .whereGreaterThanOrEqualTo(FIELD_AT, from.toEpochMilli())
            .whereLessThan(FIELD_AT, to.toEpochMilli())
            .get().get().documents
            .mapNotNull { it.toCheckIn() }
            .sortedBy { it.at }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun CheckIn.toMap(): Map<String, Any> = buildMap {
        put(FIELD_AT, at.toEpochMilli())
        hunger?.let { put("hunger", it) }
        energy?.let { put("energy", it) }
        put("note", note)
        put("source", source.name)
        mealId?.let { put("mealId", it) }
    }

    private fun DocumentSnapshot.toCheckIn(): CheckIn? {
        val at = getLong(FIELD_AT) ?: return null
        return CheckIn(
            id = id,
            at = Instant.ofEpochMilli(at),
            hunger = getLong("hunger")?.toInt(),
            energy = getLong("energy")?.toInt(),
            note = getString("note").orEmpty(),
            source = runCatching { CheckInSource.valueOf(getString("source").orEmpty()) }.getOrDefault(CheckInSource.APP),
            mealId = getString("mealId"),
        )
    }

    private companion object {
        const val FIELD_AT = "atEpochMillis"
    }
}

@Configuration
class WellbeingStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun checkInRepository(firebase: FirebaseProvider): CheckInRepository {
        if (!firebase.isConfigured) {
            logger.info("Check-ins: in-memory (geen Firebase-config)")
            return InMemoryCheckInRepository()
        }
        return runCatching { FirestoreCheckInRepository(firebase.firestore()) }
            .onSuccess { logger.info("Check-ins: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory check-ins", it)
                InMemoryCheckInRepository()
            }
    }
}
