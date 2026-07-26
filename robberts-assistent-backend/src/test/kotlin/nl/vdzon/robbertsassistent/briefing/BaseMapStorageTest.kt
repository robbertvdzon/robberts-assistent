package nl.vdzon.robbertsassistent.briefing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class BaseMapStorageTest {

    @Test
    fun `load geeft null terug zonder eerdere store`() {
        val storage = InMemoryBaseMapStorage()

        assertNull(storage.load())
    }

    @Test
    fun `store slaat bytes op, load haalt ze terug`() {
        val storage = InMemoryBaseMapStorage()
        storage.store(byteArrayOf(1, 2, 3))

        assertContentEquals(byteArrayOf(1, 2, 3), storage.load())
    }

    @Test
    fun `store overschrijft eerdere bytes`() {
        val storage = InMemoryBaseMapStorage()
        storage.store(byteArrayOf(1))

        storage.store(byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), storage.load())
    }
}
