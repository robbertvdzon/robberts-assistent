package nl.vdzon.robbertsassistent.briefing

import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor het gegenereerde weerkaart-PNG ([WeatherMapSectionProvider]), onder één vaste
 * sleutel (`morgen`, dekt beide dagdelen in één gecombineerd beeld) — geen historie, elke
 * cache-refresh overschrijft. Fallback is [InMemoryWeatherMapStorage]; met Firebase geconfigureerd
 * kiest [BriefingStoreConfig] de [FirebaseStorageWeatherMapStorage]. Zelfde patroon als
 * `assistant.PhotoStorage`.
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
