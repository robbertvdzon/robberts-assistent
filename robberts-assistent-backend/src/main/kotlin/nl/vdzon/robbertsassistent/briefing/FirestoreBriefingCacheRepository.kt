package nl.vdzon.robbertsassistent.briefing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.firestore.Firestore

/**
 * Bewaart de gecachete briefing als één document `briefing-cache/<documentId>` (veld `json`, de
 * volledige [BriefingResponse] geserialiseerd) in Firestore. Zelfde één-document-patroon als
 * `assistant.FirestoreMemoryRepository`, maar met JSON i.p.v. losse velden omdat [BriefingResponse]
 * geneste secties/items/acties bevat. Sinds SF-1275 kiest [documentId] tussen de Upcoming-cache
 * (`current`, zie [BriefingStoreConfig]) en de losse Health check-cache (`health`), zodat beide
 * onafhankelijk van elkaar ververst kunnen worden.
 */
class FirestoreBriefingCacheRepository(
    private val firestore: Firestore,
    private val documentId: String = DEFAULT_DOCUMENT,
) : BriefingCacheRepository {

    private val objectMapper = jacksonObjectMapper()
    private val document get() = firestore.collection(COLLECTION).document(documentId)

    override fun current(): BriefingResponse? {
        val snapshot = document.get().get()
        if (!snapshot.exists()) return null
        val json = snapshot.getString(FIELD_JSON) ?: return null
        return runCatching { objectMapper.readValue(json, BriefingResponse::class.java) }.getOrNull()
    }

    override fun store(response: BriefingResponse) {
        document.set(mapOf(FIELD_JSON to objectMapper.writeValueAsString(response))).get()
    }

    companion object {
        const val DEFAULT_DOCUMENT = "current"
        const val HEALTH_DOCUMENT = "health"
        private const val COLLECTION = "briefing-cache"
        private const val FIELD_JSON = "json"
    }
}
