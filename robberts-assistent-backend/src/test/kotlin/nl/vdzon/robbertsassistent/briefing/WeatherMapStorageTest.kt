package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class WeatherMapStorageTest {

    @Test
    fun `load geeft null terug zonder eerdere store voor dat dagdeel`() {
        val storage = InMemoryWeatherMapStorage()

        assertNull(storage.load("ochtend"))
    }

    @Test
    fun `store slaat bytes op per dagdeel-sleutel, load haalt ze terug`() {
        val storage = InMemoryWeatherMapStorage()
        storage.store("ochtend", byteArrayOf(1, 2, 3))
        storage.store("middag", byteArrayOf(4, 5, 6))

        assertContentEquals(byteArrayOf(1, 2, 3), storage.load("ochtend"))
        assertContentEquals(byteArrayOf(4, 5, 6), storage.load("middag"))
    }

    @Test
    fun `store overschrijft eerdere bytes voor hetzelfde dagdeel`() {
        val storage = InMemoryWeatherMapStorage()
        storage.store("ochtend", byteArrayOf(1))

        storage.store("ochtend", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), storage.load("ochtend"))
    }
}
