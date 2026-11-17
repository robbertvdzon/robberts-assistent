package nl.vdzon.robbertsassistent.activity

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Eén nacht slaap, toegerekend aan de dag waarop Robbert wakker werd. */
data class SleepSummary(
    val start: Instant,
    val end: Instant,
    val minutes: Int,
    val deepMinutes: Int = 0,
    val lightMinutes: Int = 0,
    val remMinutes: Int = 0,
    val awakeMinutes: Int = 0,
)

data class Workout(
    val start: Instant,
    val end: Instant,
    /** Soort zoals Health Connect die aanlevert, bv. `WALKING` of `BIKING`. */
    val type: String,
    val kcal: Int? = null,
    val distanceM: Int? = null,
)

/**
 * Wat Robbert op één dag bewoog, verbruikte en sliep. De gegevens komen van Garmin via Health
 * Connect op zijn telefoon (de app leest ze uit en stuurt ze hierheen); [weightKg] voert hij zelf in.
 * Elk veld mag ontbreken: wat Garmin niet aanlevert blijft leeg.
 */
data class ActivityDay(
    val date: LocalDate,
    val steps: Int? = null,
    /** Totaal verbruik (rust + bewegen) volgens Garmin. */
    val totalKcal: Int? = null,
    val distanceM: Int? = null,
    val floors: Int? = null,
    val restingHeartRate: Int? = null,
    val sleep: SleepSummary? = null,
    val workouts: List<Workout> = emptyList(),
    val weightKg: Double? = null,
    val source: String = "",
    val syncedAt: Instant? = null,
)

interface ActivityDayRepository {
    fun save(day: ActivityDay): ActivityDay
    fun find(date: LocalDate): ActivityDay?

    /** Dagen met `from <= date <= to`, oplopend. */
    fun findBetween(from: LocalDate, to: LocalDate): List<ActivityDay>

    /** De dag die het laatst is bijgewerkt door een sync, of `null` als er nog nooit is gesynct. */
    fun lastSynced(): ActivityDay?
}

class InMemoryActivityDayRepository : ActivityDayRepository {
    private val store = ConcurrentHashMap<LocalDate, ActivityDay>()

    override fun save(day: ActivityDay): ActivityDay = day.also { store[it.date] = it }
    override fun find(date: LocalDate): ActivityDay? = store[date]
    override fun findBetween(from: LocalDate, to: LocalDate): List<ActivityDay> =
        store.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    override fun lastSynced(): ActivityDay? = store.values.filter { it.syncedAt != null }.maxByOrNull { it.syncedAt!! }
}

/** Eén document per dag in `activity-days`, met de datum (`jjjj-mm-dd`) als document-id. */
class FirestoreActivityDayRepository(private val firestore: Firestore) : ActivityDayRepository {
    private val collection get() = firestore.collection("activity-days")

    override fun save(day: ActivityDay): ActivityDay {
        collection.document(day.date.toString()).set(day.toMap()).get()
        return day
    }

    override fun find(date: LocalDate): ActivityDay? = collection.document(date.toString()).get().get().toDay()

    override fun findBetween(from: LocalDate, to: LocalDate): List<ActivityDay> =
        // ISO-datums sorteren als tekst in tijdsvolgorde, dus een bereik op het veld `date` volstaat.
        collection
            .whereGreaterThanOrEqualTo("date", from.toString())
            .whereLessThanOrEqualTo("date", to.toString())
            .get().get().documents
            .mapNotNull { it.toDay() }
            .sortedBy { it.date }

    override fun lastSynced(): ActivityDay? =
        collection.orderBy("syncedAtEpochMillis", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(1).get().get().documents.firstOrNull()?.toDay()

    private fun ActivityDay.toMap(): Map<String, Any> = buildMap {
        put("date", date.toString())
        steps?.let { put("steps", it) }
        totalKcal?.let { put("totalKcal", it) }
        distanceM?.let { put("distanceM", it) }
        floors?.let { put("floors", it) }
        restingHeartRate?.let { put("restingHeartRate", it) }
        sleep?.let {
            put(
                "sleep",
                mapOf(
                    "start" to it.start.toEpochMilli(),
                    "end" to it.end.toEpochMilli(),
                    "minutes" to it.minutes,
                    "deepMinutes" to it.deepMinutes,
                    "lightMinutes" to it.lightMinutes,
                    "remMinutes" to it.remMinutes,
                    "awakeMinutes" to it.awakeMinutes,
                ),
            )
        }
        put(
            "workouts",
            workouts.map { w ->
                buildMap<String, Any> {
                    put("start", w.start.toEpochMilli())
                    put("end", w.end.toEpochMilli())
                    put("type", w.type)
                    w.kcal?.let { put("kcal", it) }
                    w.distanceM?.let { put("distanceM", it) }
                }
            },
        )
        weightKg?.let { put("weightKg", it) }
        put("source", source)
        syncedAt?.let { put("syncedAtEpochMillis", it.toEpochMilli()) }
    }

    private fun DocumentSnapshot.toDay(): ActivityDay? {
        if (!exists()) return null
        val date = runCatching { LocalDate.parse(getString("date") ?: id) }.getOrNull() ?: return null
        val sleep = (get("sleep") as? Map<*, *>)?.let { m ->
            val start = (m["start"] as? Number)?.toLong() ?: return@let null
            val end = (m["end"] as? Number)?.toLong() ?: return@let null
            SleepSummary(
                start = Instant.ofEpochMilli(start),
                end = Instant.ofEpochMilli(end),
                minutes = (m["minutes"] as? Number)?.toInt() ?: 0,
                deepMinutes = (m["deepMinutes"] as? Number)?.toInt() ?: 0,
                lightMinutes = (m["lightMinutes"] as? Number)?.toInt() ?: 0,
                remMinutes = (m["remMinutes"] as? Number)?.toInt() ?: 0,
                awakeMinutes = (m["awakeMinutes"] as? Number)?.toInt() ?: 0,
            )
        }
        val workouts = (get("workouts") as? List<*>)?.filterIsInstance<Map<*, *>>()?.mapNotNull { m ->
            val start = (m["start"] as? Number)?.toLong() ?: return@mapNotNull null
            val end = (m["end"] as? Number)?.toLong() ?: return@mapNotNull null
            Workout(
                start = Instant.ofEpochMilli(start),
                end = Instant.ofEpochMilli(end),
                type = m["type"] as? String ?: "",
                kcal = (m["kcal"] as? Number)?.toInt(),
                distanceM = (m["distanceM"] as? Number)?.toInt(),
            )
        } ?: emptyList()
        return ActivityDay(
            date = date,
            steps = getLong("steps")?.toInt(),
            totalKcal = getLong("totalKcal")?.toInt(),
            distanceM = getLong("distanceM")?.toInt(),
            floors = getLong("floors")?.toInt(),
            restingHeartRate = getLong("restingHeartRate")?.toInt(),
            sleep = sleep,
            workouts = workouts,
            weightKg = getDouble("weightKg"),
            source = getString("source").orEmpty(),
            syncedAt = getLong("syncedAtEpochMillis")?.let(Instant::ofEpochMilli),
        )
    }
}

@Configuration
class ActivityStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun activityDayRepository(firebase: FirebaseProvider): ActivityDayRepository {
        if (!firebase.isConfigured) {
            logger.info("Activiteit-dagen: in-memory (geen Firebase-config)")
            return InMemoryActivityDayRepository()
        }
        return runCatching { FirestoreActivityDayRepository(firebase.firestore()) }
            .onSuccess { logger.info("Activiteit-dagen: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory activiteit-dagen", it)
                InMemoryActivityDayRepository()
            }
    }
}
