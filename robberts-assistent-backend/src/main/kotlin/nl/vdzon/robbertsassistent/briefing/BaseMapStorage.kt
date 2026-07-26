package nl.vdzon.robbertsassistent.briefing

import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor de eenmalig opgehaalde OSM-basiskaart van de kust IJmuiden-Egmond
 * ([OsmCoastMapImageBuilder]) — één vaste sleutel, geen historie, overleeft een pod-herstart
 * zodat niet na elke herstart opnieuw alle tegels opgehaald hoeven te worden. Fallback is
 * [InMemoryBaseMapStorage]; met Firebase geconfigureerd kiest [BriefingStoreConfig] de
 * [FirebaseStorageBaseMapStorage]. Zelfde patroon als [WeatherMapStorage].
 */
interface BaseMapStorage {
    fun store(bytes: ByteArray)

    fun load(): ByteArray?
}

class InMemoryBaseMapStorage : BaseMapStorage {
    private val store = ConcurrentHashMap<String, ByteArray>()

    override fun store(bytes: ByteArray) {
        store[KEY] = bytes
    }

    override fun load(): ByteArray? = store[KEY]

    private companion object {
        const val KEY = "basemap"
    }
}
