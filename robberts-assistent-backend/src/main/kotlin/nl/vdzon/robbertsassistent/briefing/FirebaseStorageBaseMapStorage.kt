package nl.vdzon.robbertsassistent.briefing

import com.google.cloud.storage.Bucket

/**
 * Bewaart de OSM-basiskaart-PNG in Firebase Cloud Storage, onder `briefing-weather-map/basemap.png`
 * (zelfde map als het overlay-PNG, los bestand). Vaste bestandsnaam, dus een verse fetch
 * overschrijft — geen historie, zie [BaseMapStorage].
 */
class FirebaseStorageBaseMapStorage(private val bucket: Bucket) : BaseMapStorage {

    override fun store(bytes: ByteArray) {
        bucket.create(PATH, bytes, "image/png")
    }

    override fun load(): ByteArray? = bucket.get(PATH)?.getContent()

    private companion object {
        const val PATH = "briefing-weather-map/basemap.png"
    }
}
