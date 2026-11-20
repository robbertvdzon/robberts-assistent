package nl.vdzon.robbertsassistent.nutrition

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.storage.Bucket
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// ---- Maaltijden -------------------------------------------------------------------------------

/** Opslag-poort voor maaltijden. Fallback in-memory; met Firebase de Firestore-variant. */
interface MealRepository {
    fun save(meal: Meal): Meal
    fun findById(id: String): Meal?

    /** Maaltijden met `from <= eatenAt < to`, oplopend in tijd. */
    fun findBetween(from: Instant, to: Instant): List<Meal>
    fun delete(id: String)
}

class InMemoryMealRepository : MealRepository {
    private val meals = ConcurrentHashMap<String, Meal>()

    override fun save(meal: Meal): Meal = meal.also { meals[it.id] = it }
    override fun findById(id: String): Meal? = meals[id]
    override fun findBetween(from: Instant, to: Instant): List<Meal> =
        meals.values.filter { !it.eatenAt.isBefore(from) && it.eatenAt.isBefore(to) }.sortedBy { it.eatenAt }

    override fun delete(id: String) {
        meals.remove(id)
    }
}

/** Eén document per maaltijd in de collectie `meals`; tijden als epoch-millis (zelfde stijl als `watches`). */
class FirestoreMealRepository(private val firestore: Firestore) : MealRepository {
    private val collection get() = firestore.collection(COLLECTION)

    override fun save(meal: Meal): Meal {
        collection.document(meal.id).set(meal.toMap()).get()
        return meal
    }

    override fun findById(id: String): Meal? = collection.document(id).get().get().toMeal()

    override fun findBetween(from: Instant, to: Instant): List<Meal> =
        collection
            .whereGreaterThanOrEqualTo(FIELD_EATEN_AT, from.toEpochMilli())
            .whereLessThan(FIELD_EATEN_AT, to.toEpochMilli())
            .get().get().documents
            .mapNotNull { it.toMeal() }
            .sortedBy { it.eatenAt }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Meal.toMap(): Map<String, Any> = mapOf(
        FIELD_EATEN_AT to eatenAt.toEpochMilli(),
        "title" to title,
        "photoIds" to photoIds,
        "note" to note,
        "status" to status.name,
        "items" to items.map { it.toMap() },
        "aiProposal" to aiProposal.map { it.toMap() },
        "remark" to remark,
        "corrections" to corrections,
        "learned" to learned,
        "error" to error,
        "eatenPercent" to eatenPercent,
        "referenceRole" to referenceRole.name,
        "referenceDescription" to referenceDescription,
        "referencePhotoId" to referencePhotoId,
        "duplicateOf" to duplicateOf,
        "referenceManual" to referenceManual,
        "createdAt" to createdAt.toEpochMilli(),
        "updatedAt" to updatedAt.toEpochMilli(),
    )

    private fun MealItem.toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "grams" to grams,
        "kcal" to kcal,
        "proteinG" to proteinG,
        "carbsG" to carbsG,
        "fatG" to fatG,
        "fiberG" to fiberG,
        "vegetableG" to vegetableG,
        "fruitG" to fruitG,
        "uncertain" to uncertain,
        "basis" to basis.name,
    )

    private fun DocumentSnapshot.toMeal(): Meal? {
        if (!exists()) return null
        val eatenAt = getLong(FIELD_EATEN_AT) ?: return null
        return Meal(
            id = id,
            eatenAt = Instant.ofEpochMilli(eatenAt),
            title = getString("title").orEmpty(),
            photoIds = strings("photoIds"),
            note = getString("note").orEmpty(),
            status = runCatching { MealStatus.valueOf(getString("status").orEmpty()) }.getOrDefault(MealStatus.MISLUKT),
            items = items("items"),
            aiProposal = items("aiProposal"),
            remark = getString("remark").orEmpty(),
            corrections = strings("corrections"),
            learned = getString("learned").orEmpty(),
            error = getString("error").orEmpty(),
            eatenPercent = (getLong("eatenPercent") ?: 100L).toInt().coerceIn(1, 100),
            referenceRole = runCatching { ReferenceRole.valueOf(getString("referenceRole").orEmpty()) }
                .getOrDefault(ReferenceRole.ONBEKEND),
            referenceDescription = getString("referenceDescription").orEmpty(),
            referencePhotoId = getString("referencePhotoId").orEmpty(),
            duplicateOf = getString("duplicateOf").orEmpty(),
            referenceManual = getBoolean("referenceManual") ?: false,
            createdAt = Instant.ofEpochMilli(getLong("createdAt") ?: eatenAt),
            updatedAt = Instant.ofEpochMilli(getLong("updatedAt") ?: eatenAt),
        )
    }

    private fun DocumentSnapshot.strings(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private fun DocumentSnapshot.items(field: String): List<MealItem> =
        (get(field) as? List<*>)?.filterIsInstance<Map<*, *>>()?.map { m ->
            MealItem(
                name = m["name"] as? String ?: "",
                grams = (m["grams"] as? Number)?.toInt() ?: 0,
                kcal = (m["kcal"] as? Number)?.toInt() ?: 0,
                proteinG = (m["proteinG"] as? Number)?.toDouble() ?: 0.0,
                carbsG = (m["carbsG"] as? Number)?.toDouble() ?: 0.0,
                fatG = (m["fatG"] as? Number)?.toDouble() ?: 0.0,
                fiberG = (m["fiberG"] as? Number)?.toDouble() ?: 0.0,
                vegetableG = (m["vegetableG"] as? Number)?.toInt() ?: 0,
                fruitG = (m["fruitG"] as? Number)?.toInt() ?: 0,
                uncertain = m["uncertain"] as? Boolean ?: false,
                basis = runCatching { ItemBasis.valueOf(m["basis"] as? String ?: "") }.getOrDefault(ItemBasis.GESCHAT),
            )
        } ?: emptyList()

    private companion object {
        const val COLLECTION = "meals"
        const val FIELD_EATEN_AT = "eatenAtEpochMillis"
    }
}

// ---- Foto's -----------------------------------------------------------------------------------

class StoredMealPhoto(val bytes: ByteArray, val contentType: String)

/** Opslag-poort voor etensfoto's (zelfde patroon als `gardenchat.PhotoStorage`). */
interface MealPhotoStorage {
    fun store(bytes: ByteArray, contentType: String): String
    fun load(id: String): StoredMealPhoto?
    fun delete(id: String)
}

class InMemoryMealPhotoStorage : MealPhotoStorage {
    private val store = ConcurrentHashMap<String, StoredMealPhoto>()

    override fun store(bytes: ByteArray, contentType: String): String {
        val id = UUID.randomUUID().toString()
        store[id] = StoredMealPhoto(bytes, contentType)
        return id
    }

    override fun load(id: String): StoredMealPhoto? = store[id]
    override fun delete(id: String) {
        store.remove(id)
    }
}

/** Etensfoto's in Firebase Storage onder de map `eten/`. */
class FirebaseMealPhotoStorage(private val bucket: Bucket) : MealPhotoStorage {
    override fun store(bytes: ByteArray, contentType: String): String {
        val id = UUID.randomUUID().toString()
        bucket.create("$PREFIX$id", bytes, contentType)
        return id
    }

    override fun load(id: String): StoredMealPhoto? {
        val blob = bucket.get("$PREFIX$id") ?: return null
        return StoredMealPhoto(blob.getContent(), blob.contentType ?: "application/octet-stream")
    }

    override fun delete(id: String) {
        bucket.get("$PREFIX$id")?.delete()
    }

    private companion object {
        const val PREFIX = "eten/"
    }
}

// ---- Geheugen ---------------------------------------------------------------------------------

/**
 * Wat de assistent over Robberts eten heeft geleerd: één vrije tekst met vuistregels en vaste
 * gerechten. Zelfde opzet als `assistant.MemoryRepository`.
 */
interface FoodMemoryRepository {
    fun current(): String
    fun update(text: String): String
}

class InMemoryFoodMemoryRepository : FoodMemoryRepository {
    private val text = AtomicReference("")
    override fun current(): String = text.get()
    override fun update(text: String): String = text.also { this.text.set(it) }
}

class FirestoreFoodMemoryRepository(private val firestore: Firestore) : FoodMemoryRepository {
    private val document get() = firestore.collection("food-memory").document("memory")

    override fun current(): String {
        val snapshot = document.get().get()
        return if (snapshot.exists()) snapshot.getString("text").orEmpty() else ""
    }

    override fun update(text: String): String {
        document.set(mapOf("text" to text)).get()
        return text
    }
}

// ---- Keuze opslag -----------------------------------------------------------------------------

/** Firestore/Storage zodra Firebase geconfigureerd is, anders in-memory (zelfde patroon als de rest). */
@Configuration
class NutritionStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun mealRepository(firebase: FirebaseProvider): MealRepository =
        choose(firebase, "Maaltijden", { FirestoreMealRepository(firebase.firestore()) }, { InMemoryMealRepository() })

    @Bean
    fun mealPhotoStorage(firebase: FirebaseProvider): MealPhotoStorage =
        choose(firebase, "Etensfoto's", { FirebaseMealPhotoStorage(firebase.bucket()) }, { InMemoryMealPhotoStorage() })

    @Bean
    fun foodMemoryRepository(firebase: FirebaseProvider): FoodMemoryRepository =
        choose(firebase, "Eet-geheugen", { FirestoreFoodMemoryRepository(firebase.firestore()) }, { InMemoryFoodMemoryRepository() })

    private fun <T> choose(firebase: FirebaseProvider, what: String, real: () -> T, fallback: () -> T): T {
        if (!firebase.isConfigured) {
            logger.info("{}: in-memory (geen Firebase-config)", what)
            return fallback()
        }
        return runCatching(real)
            .onSuccess { logger.info("{}: Firebase", what) }
            .getOrElse {
                logger.error("{}: Firebase-init faalde, val terug op in-memory", what, it)
                fallback()
            }
    }
}
