package nl.vdzon.robbertsassistent.briefing

import com.google.cloud.storage.Bucket

/**
 * Bewaart de weerkaart-PNG's in Firebase Cloud Storage, onder de map `briefing-weather-map/` (los
 * van de bestaande foto-mappen `assistent-chat/`/`moestuin/`). Vaste bestandsnaam per dagdeel
 * (`ochtend.png`/`middag.png`), dus elke refresh overschrijft — geen historie, zie
 * [WeatherMapStorage].
 */
class FirebaseStorageWeatherMapStorage(private val bucket: Bucket) : WeatherMapStorage {

    override fun store(slot: String, bytes: ByteArray) {
        bucket.create("$PREFIX$slot.png", bytes, "image/png")
    }

    override fun load(slot: String): ByteArray? = bucket.get("$PREFIX$slot.png")?.getContent()

    private companion object {
        const val PREFIX = "briefing-weather-map/"
    }
}
