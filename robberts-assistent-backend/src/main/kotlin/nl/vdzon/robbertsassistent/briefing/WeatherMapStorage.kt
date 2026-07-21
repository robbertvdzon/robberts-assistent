package nl.vdzon.robbertsassistent.briefing

import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor de gegenereerde weerkaart-PNG's ([WeatherMapSectionProvider]), per vast
 * dagdeel-sleutel (`ochtend`/`middag`) — geen historie, elke cache-refresh overschrijft. Fallback
 * is [InMemoryWeatherMapStorage]; met Firebase geconfigureerd kiest [BriefingStoreConfig] de
 * [FirebaseStorageWeatherMapStorage]. Zelfde patroon als `assistant.PhotoStorage`.
 */
interface WeatherMapStorage {
    fun store(slot: String, bytes: ByteArray)

    fun load(slot: String): ByteArray?
}

class InMemoryWeatherMapStorage : WeatherMapStorage {
    private val store = ConcurrentHashMap<String, ByteArray>()

    override fun store(slot: String, bytes: ByteArray) {
        store[slot] = bytes
    }

    override fun load(slot: String): ByteArray? = store[slot]
}
