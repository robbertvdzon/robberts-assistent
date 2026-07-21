package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class WeatherMapStorageTest {

    @Test
    fun `load geeft null terug zonder eerdere store voor die sleutel`() {
        val storage = InMemoryWeatherMapStorage()

        assertNull(storage.load("morgen"))
    }

    @Test
    fun `store slaat bytes op onder de sleutel morgen, load haalt ze terug`() {
        val storage = InMemoryWeatherMapStorage()
        storage.store("morgen", byteArrayOf(1, 2, 3))

        assertContentEquals(byteArrayOf(1, 2, 3), storage.load("morgen"))
    }

    @Test
    fun `store overschrijft eerdere bytes voor dezelfde sleutel`() {
        val storage = InMemoryWeatherMapStorage()
        storage.store("morgen", byteArrayOf(1))

        storage.store("morgen", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), storage.load("morgen"))
    }
}
