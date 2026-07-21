package nl.vdzon.robbertsassistent.briefing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.firestore.Firestore

/**
 * Bewaart de gecachete briefing als één document `briefing-cache/current` (veld `json`, de volledige
 * [BriefingResponse] geserialiseerd) in Firestore. Zelfde één-document-patroon als
 * `assistant.FirestoreMemoryRepository`, maar met JSON i.p.v. losse velden omdat [BriefingResponse]
 * geneste secties/items/acties bevat.
 */
class FirestoreBriefingCacheRepository(private val firestore: Firestore) : BriefingCacheRepository {

    private val objectMapper = jacksonObjectMapper()
    private val document get() = firestore.collection(COLLECTION).document(DOCUMENT)

    override fun current(): BriefingResponse? {
        val snapshot = document.get().get()
        if (!snapshot.exists()) return null
        val json = snapshot.getString(FIELD_JSON) ?: return null
        return runCatching { objectMapper.readValue(json, BriefingResponse::class.java) }.getOrNull()
    }

    override fun store(response: BriefingResponse) {
        document.set(mapOf(FIELD_JSON to objectMapper.writeValueAsString(response))).get()
    }

    private companion object {
        const val COLLECTION = "briefing-cache"
        const val DOCUMENT = "current"
        const val FIELD_JSON = "json"
    }
}
